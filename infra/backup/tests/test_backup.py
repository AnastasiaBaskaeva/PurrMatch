from contextlib import ExitStack, redirect_stdout
from datetime import datetime, timedelta, timezone
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SOURCE))
import backup

SECRET = 'DISPOSABLE-PRIVATE-AUTHORIZATION-FIXTURE'
CIPHER = b'age-encryption.org/v1\nDISPOSABLE-CIPHER\n'
ORIGINAL_RUN = subprocess.run


class BackupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='purrmatch-job-fixture-')
        self.base = Path(self.temp.name)
        self.state, self.runtime = self.base / 'state', self.base / 'runtime'
        for directory in (self.state, self.runtime):
            directory.mkdir(mode=0o700)
        self.key = self.base / 'key.json'
        self.key.write_text(json.dumps({'private_key': SECRET}))
        self.key.chmod(0o600)
        self.config = dict(environment='test', service_account_id='a' * 20, secret_id='b' * 20,
                           bucket='purrmatch-test-backups-fixture', access_key_field='access-key-id',
                           secret_key_field='secret-access-key', age_recipient='age1' + 'a' * 58)
        self.events = []
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(patch.object(backup, 'OWNER', os.getuid()))
        self.stack.enter_context(patch.object(backup.os, 'geteuid', return_value=0))
        self.stack.enter_context(patch.object(backup.socket, 'gethostname', return_value='purrmatch-test'))
        self.auth = self.stack.enter_context(patch.object(backup.cloud, 'credentials', side_effect=self.credentials))
        self.capture = self.stack.enter_context(patch.object(backup.capture, 'capture', side_effect=self.capture_archive))
        self.age = self.stack.enter_context(patch.object(backup.subprocess, 'run', side_effect=self.encrypt))
        self.put = self.stack.enter_context(patch.object(backup.cloud, 'put', side_effect=self.upload))

    def tearDown(self):
        self.stack.close()
        self.temp.cleanup()

    def credentials(self, *args, **kwargs):
        self.events.append('authorize')
        self.assertEqual(args[0]['private_key'], SECRET)
        self.assertEqual(args[1:3], ('a' * 20, 'b' * 20))
        return 'fixture-access', 'fixture-secret', 'fixture-version'

    def capture_archive(self, work, environment):
        self.events.append('capture')
        self.assertEqual(environment, 'test')
        path = work / 'archive.tar.gz'
        path.write_bytes(SECRET.encode())
        return path

    def encrypt(self, args, **kwargs):
        self.events.append('encrypt')
        self.assertEqual(args[:3], [backup.AGE, '-r', self.config['age_recipient']])
        self.assertEqual(kwargs['stderr'], subprocess.DEVNULL)
        self.assertEqual(kwargs['stdin'], subprocess.DEVNULL)
        self.assertEqual(Path(args[-1]).read_bytes(), SECRET.encode())
        partial = Path(args[4])
        partial.write_bytes(CIPHER)
        partial.chmod(0o600)
        return subprocess.CompletedProcess(args, 0)

    def upload(self, data, bucket, key, access, secret, metadata):
        self.events.append('upload')
        pending, = [path for path in self.state.glob('pending-*') if (path / 'receipt.json').exists()]
        receipt = json.loads((pending / 'receipt.json').read_text())
        self.assertEqual(receipt['status'], 'planned')
        self.assertIsNone(receipt['archiveVersionId'])
        self.assertEqual(data, CIPHER)
        self.assertEqual(bucket, self.config['bucket'])
        self.assertEqual((access, secret), ('fixture-access', 'fixture-secret'))
        self.assertEqual(metadata['environment'], 'test')
        self.assertEqual(receipt['archiveKey'], key)
        self.assertEqual(list(self.runtime.iterdir()), [])
        return 'fixture-object-version'

    def invoke(self, failure=False):
        output = io.StringIO()
        with redirect_stdout(output):
            if failure:
                with self.assertRaises(Exception):
                    backup.run(self.config, self.key, self.state, self.runtime)
                result = None
            else:
                result = backup.run(self.config, self.key, self.state, self.runtime)
        self.assertNotIn(SECRET, output.getvalue())
        self.assertNotIn('fixture-secret', output.getvalue())
        return result, output.getvalue()

    def test_success_receipt_order_cleanup_and_existing_archive_format(self):
        receipt, output = self.invoke()
        self.assertEqual(self.events, ['authorize', 'capture', 'encrypt', 'upload'])
        self.assertEqual(receipt['status'], 'uploaded')
        self.assertEqual(receipt['schema'], 'purrmatch-k3s-state-v1')
        self.assertEqual(receipt['archiveVersionId'], 'fixture-object-version')
        self.assertEqual(json.loads((self.state / 'last-success.json').read_text()), receipt)
        self.assertEqual(list(self.state.glob('pending-*')), [])
        self.assertEqual(list(self.runtime.iterdir()), [])
        self.assertIn('PASS: test backup uploaded.', output)
        for path in self.state.iterdir():
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            self.assertNotIn(SECRET.encode(), path.read_bytes())

    def test_authorization_failure_does_not_capture_or_change_last_success(self):
        self.auth.side_effect = backup.cloud.Failure('IAM unavailable')
        self.invoke(failure=True)
        self.assertEqual(self.events, [])
        self.capture.assert_not_called()
        self.put.assert_not_called()
        self.assertFalse((self.state / 'last-success.json').exists())
        self.assertEqual(json.loads((self.state / 'last-failure.json').read_text())['phase'], 'authorize')

    def test_capture_failure_never_encrypts_or_uploads(self):
        self.capture.side_effect = backup.capture.Failure('Source changed')
        self.invoke(failure=True)
        self.age.assert_not_called()
        self.put.assert_not_called()
        self.assertEqual(list(self.runtime.iterdir()), [])

    def test_encryption_failure_never_uploads_and_private_partial_is_retained(self):
        def fail(*args, **kwargs):
            self.encrypt(*args, **kwargs)
            return subprocess.CompletedProcess(args, 1)
        self.age.side_effect = fail
        self.invoke(failure=True)
        self.put.assert_not_called()
        pending, = self.state.glob('pending-*')
        self.assertEqual(pending.stat().st_mode & 0o777, 0o700)
        self.assertEqual(next(pending.glob('*.partial')).read_bytes(), CIPHER)
        self.assertEqual(list(self.runtime.iterdir()), [])

    def test_unknown_put_preserves_cipher_and_incomplete_receipt_without_retry(self):
        self.put.side_effect = backup.cloud.Failure('Upload timed out')
        self.invoke(failure=True)
        self.assertEqual(self.put.call_count, 1)
        pending, = self.state.glob('pending-*')
        self.assertEqual((pending / 'test-k3s-state.tar.gz.age').read_bytes(), CIPHER)
        self.assertEqual(json.loads((pending / 'receipt.json').read_text())['status'], 'incomplete')
        self.assertFalse((self.state / 'last-success.json').exists())

    def test_wrong_host_and_overlap_do_not_authorize(self):
        with patch.object(backup.socket, 'gethostname', return_value='purrmatch-prod'):
            self.invoke(failure=True)
        with backup.locked(self.state / 'job.lock'):
            self.invoke(failure=True)
        self.auth.assert_not_called()

    def test_pending_budget_stops_before_authorization(self):
        for index in range(4):
            (self.state / f'pending-{index:032x}').mkdir(mode=0o700)
        self.invoke(failure=True)
        self.auth.assert_not_called()
        self.capture.assert_not_called()

    def test_empty_partial_does_not_block_next_attempt(self):
        directory = self.state / ('pending-' + '0' * 32)
        directory.mkdir(mode=0o700)
        partial = directory / 'test-k3s-state.tar.gz.age.partial'
        partial.touch(mode=0o600)
        _, output = self.invoke()
        self.assertIn('PASS:', output)
        self.assertTrue(partial.exists())

    def test_failed_last_success_sync_retains_uploaded_receipt_and_cipher(self):
        real = backup.atomic_json
        def fail(path, value):
            if path.name == 'last-success.json':
                raise OSError('fixture fsync failure')
            real(path, value)
        with patch.object(backup, 'atomic_json', side_effect=fail):
            self.invoke(failure=True)
        pending, = self.state.glob('pending-*')
        self.assertEqual(json.loads((pending / 'receipt.json').read_text())['status'], 'uploaded')
        self.assertEqual((pending / 'test-k3s-state.tar.gz.age').read_bytes(), CIPHER)

    def test_maintenance_lock_stops_capture_without_mutating_source(self):
        with backup.locked(self.state / 'maintenance.lock'):
            self.invoke(failure=True)
        self.capture.assert_not_called()
        self.age.assert_not_called()

    def test_status_checks_snapshot_age_and_latest_failure(self):
        receipt, _ = self.invoke()
        with redirect_stdout(io.StringIO()):
            backup.status(self.config, self.state)
        old = datetime.now(timezone.utc) - timedelta(hours=2)
        receipt['snapshot_utc'] = old.strftime('%Y-%m-%dT%H:%M:%SZ')
        backup.atomic_json(self.state / 'last-success.json', receipt)
        with self.assertRaisesRegex(backup.Failure, 'stale'):
            backup.status(self.config, self.state)
        receipt['snapshot_utc'] = backup.now()
        backup.atomic_json(self.state / 'last-success.json', receipt)
        backup.atomic_json(self.state / 'last-failure.json', {'phase': 'upload'})
        with self.assertRaisesRegex(backup.Failure, 'Last backup attempt failed'):
            backup.status(self.config, self.state)

    def test_interrupted_attempt_is_detected_even_without_failure_file(self):
        self.invoke()
        backup.atomic_json(self.state / 'last-attempt.json', {'attempt_id': 'new-attempt'})
        with self.assertRaisesRegex(backup.Failure, 'did not complete'):
            backup.status(self.config, self.state)
        output = io.StringIO()
        with backup.locked(self.state / 'job.lock'), redirect_stdout(output):
            backup.status(self.config, self.state)
        self.assertIn('INFO: backup in progress', output.getvalue())
        self.assertNotIn('PASS:', output.getvalue())

    def test_config_and_credential_private_modes_and_environment(self):
        path = self.base / 'config.json'
        path.write_text(json.dumps(self.config))
        path.chmod(0o600)
        self.assertEqual(backup.load_config(path), self.config)
        path.chmod(0o644)
        with self.assertRaises(backup.Failure):
            backup.load_config(path)
        path.chmod(0o600)
        path.write_text(json.dumps(dict(self.config, bucket='purrmatch-prod-backups-fixture')))
        with self.assertRaises(backup.Failure):
            backup.load_config(path)
        self.key.chmod(0o400)
        self.assertEqual(backup.authorized_key(self.key)['private_key'], SECRET)


class NativeAgeTests(unittest.TestCase):
    @unittest.skipUnless(Path('/usr/bin/age').is_file() and Path('/usr/bin/age-keygen').is_file(), 'Ubuntu age binaries unavailable')
    def test_job_cipher_is_decryptable_with_disposable_external_identity(self):
        fixture = BackupTests('test_success_receipt_order_cleanup_and_existing_archive_format')
        fixture.setUp()
        self.addCleanup(fixture.tearDown)
        identity = fixture.base / 'external-identity.txt'
        ORIGINAL_RUN(['/usr/bin/age-keygen', '-o', str(identity)], capture_output=True, check=True)
        recipient = ORIGINAL_RUN(['/usr/bin/age-keygen', '-y', str(identity)], capture_output=True, text=True, check=True).stdout.strip()
        fixture.config['age_recipient'] = recipient
        stored = {}
        def upload(data, bucket, key, access, secret, metadata):
            stored['cipher'] = data
            self.assertEqual(metadata['bytes'], str(len(data)))
            self.assertNotIn(SECRET.encode(), data)
            return 'fixture-native-version'
        fixture.put.side_effect = upload
        with patch.object(backup.subprocess, 'run', wraps=ORIGINAL_RUN):
            fixture.invoke()
        decrypted = ORIGINAL_RUN(['/usr/bin/age', '-d', '-i', str(identity)], input=stored['cipher'], capture_output=True, check=True)
        self.assertEqual(decrypted.stdout, SECRET.encode())
        self.assertEqual(list(fixture.runtime.iterdir()), [])
        self.assertEqual(list(fixture.state.glob('pending-*')), [])


if __name__ == '__main__':
    unittest.main()
