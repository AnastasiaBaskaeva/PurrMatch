import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import socket
import stat
import subprocess
import sys
import tempfile
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parent))
import capture
import cloud


OWNER = 0
STATE = Path('/var/lib/purrmatch-backup')
RUNTIME = Path('/run/purrmatch-backup')
AGE = '/usr/bin/age'
MAX_CIPHER = 16 * 1024**2
FIELDS = {'environment', 'service_account_id', 'secret_id', 'bucket',
          'access_key_field', 'secret_key_field', 'age_recipient'}


class Failure(Exception):
    pass


class Busy(Failure):
    pass


def private_dir(path):
    info = path.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != OWNER or stat.S_IMODE(info.st_mode) != 0o700:
        raise Failure('Expected a private owned directory')


def read_private(path, limit, modes=(0o600,), allow_empty=False):
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(descriptor, 'rb') as stream:
        info = os.fstat(stream.fileno())
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != OWNER
                or stat.S_IMODE(info.st_mode) not in modes or not (0 if allow_empty else 1) <= info.st_size <= limit):
            raise Failure('Invalid private input file')
        data = stream.read(limit + 1)
        if len(data) != info.st_size or len(data) > limit:
            raise Failure('Private input changed or exceeds limit')
        return data


def sync_directory(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def atomic_json(path, value):
    descriptor, temporary = tempfile.mkstemp(prefix='.state-', dir=path.parent)
    try:
        os.fchmod(descriptor, 0o600)
        with os.fdopen(descriptor, 'w') as stream:
            stream.write(json.dumps(value, indent=2) + '\n')
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        sync_directory(path.parent)
    finally:
        Path(temporary).unlink(missing_ok=True)


def load_config(path):
    value = json.loads(read_private(path, 16384))
    if not isinstance(value, dict) or value.keys() != FIELDS or not all(isinstance(v, str) for v in value.values()):
        raise Failure('Invalid backup configuration')
    if value['environment'] not in {'test', 'prod'}:
        raise Failure('Invalid backup environment')
    if any(not re.fullmatch(r'[a-z0-9]{20}', value[key]) for key in ('service_account_id', 'secret_id')):
        raise Failure('Invalid service account or secret ID')
    if (not re.fullmatch(r'[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]', value['bucket'])
            or not value['bucket'].startswith(f"purrmatch-{value['environment']}-backups-")):
        raise Failure('Bucket does not match selected environment')
    if any(not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', value[key]) for key in ('access_key_field', 'secret_key_field')):
        raise Failure('Invalid Lockbox field names')
    if value['access_key_field'] == value['secret_key_field']:
        raise Failure('Lockbox field names must differ')
    if not re.fullmatch(r'age1[0-9a-z]{58}', value['age_recipient']):
        raise Failure('Expected a native age public recipient')
    return value


def host_guard(config):
    if os.geteuid() != 0 or socket.gethostname() != f"purrmatch-{config['environment']}":
        raise Failure('Backup requires root on the selected guest')


@contextmanager
def locked(path):
    descriptor = os.open(path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600)
    try:
        info = os.fstat(descriptor)
        if not stat.S_ISREG(info.st_mode) or info.st_uid != OWNER or stat.S_IMODE(info.st_mode) != 0o600:
            raise Failure('Invalid maintenance lock')
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise Busy('Backup or maintenance is already running') from None
        yield
    finally:
        os.close(descriptor)


def now():
    return datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')


def authorized_key(path=None):
    if path is None:
        directory = os.environ.get('CREDENTIALS_DIRECTORY', '')
        if not directory or not Path(directory).is_absolute():
            raise Failure('systemd authorized-key credential is missing')
        path = Path(directory) / 'yc-authorized-key'
    value = json.loads(read_private(path, 65536, (0o400, 0o600)))
    if not isinstance(value, dict):
        raise Failure('Invalid authorized-key credential')
    return value


def credentials(config, key, deny_secret=None):
    return cloud.credentials(key, config['service_account_id'], config['secret_id'],
                             config['access_key_field'], config['secret_key_field'], deny_secret=deny_secret)


def child_environment():
    return {key: os.environ[key] for key in ('HOME', 'PATH', 'LANG', 'LC_ALL') if key in os.environ}


def check_pending(state):
    pending = list(state.glob('pending-*'))
    if len(pending) >= 4:
        raise Failure('Pending backups require operator review')
    for directory in pending:
        if not re.fullmatch(r'pending-[0-9a-f]{32}', directory.name):
            raise Failure('Unexpected pending backup directory')
        private_dir(directory)
        for path in directory.iterdir():
            if path.name not in {'receipt.json', 'test-k3s-state.tar.gz.age', 'prod-k3s-state.tar.gz.age',
                                 'test-k3s-state.tar.gz.age.partial', 'prod-k3s-state.tar.gz.age.partial'}:
                raise Failure('Unexpected pending backup file')
            limit = 8192 if path.name == 'receipt.json' else MAX_CIPHER
            read_private(path, limit, allow_empty=path.name.endswith('.partial'))


def encrypt(archive, recipient, partial):
    result = subprocess.run([AGE, '-r', recipient, '-o', str(partial), str(archive)],
                            stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                            timeout=45, check=False, env=child_environment())
    if result.returncode:
        raise Failure('age encryption failed')
    data = read_private(partial, MAX_CIPHER)
    if not data.startswith(b'age-encryption.org/v1\n'):
        raise Failure('Encrypted output is not native age ciphertext')
    return data


def run(config, key_path=None, state=STATE, runtime=RUNTIME):
    os.umask(0o077)
    host_guard(config)
    private_dir(state)
    private_dir(runtime)
    phase = 'preflight'
    with locked(state / 'job.lock'):
        try:
            identifier = uuid.uuid4()
            atomic_json(state / 'last-attempt.json', {'attempt_id': identifier.hex, 'started_utc': now(),
                                                     'environment': config['environment']})
            check_pending(state)
            phase = 'authorize'
            print(f"CHECK: {phase} {config['environment']}", flush=True)
            access, secret, secret_version = credentials(config, authorized_key(key_path))
            phase = 'capture'
            print(f'CHECK: {phase}', flush=True)
            snapshot_utc = now()
            with tempfile.TemporaryDirectory(prefix='work-', dir=runtime) as temporary:
                work = Path(temporary)
                with locked(state / 'maintenance.lock'):
                    archive = capture.capture(work, config['environment'])
                phase = 'encrypt'
                print(f'CHECK: {phase}', flush=True)
                pending = state / f'pending-{identifier.hex}'
                pending.mkdir(mode=0o700)
                sync_directory(state)
                filename = f"{config['environment']}-k3s-state.tar.gz.age"
                cipher = pending / filename
                partial = pending / (filename + '.partial')
                data = encrypt(archive, config['age_recipient'], partial)
                os.replace(partial, cipher)
                descriptor = os.open(cipher, os.O_RDWR | os.O_NOFOLLOW)
                try:
                    os.fsync(descriptor)
                finally:
                    os.close(descriptor)
            digest = hashlib.sha256(data).hexdigest()
            prefix = f'k3s/{identifier}/'
            receipt = dict(schema='purrmatch-k3s-state-v1', environment=config['environment'], file=filename,
                           sha256=digest, bytes=len(data), bucket=config['bucket'], prefix=prefix,
                           archiveKey=prefix + filename, archiveVersionId=None, status='planned',
                           snapshot_utc=snapshot_utc, lockbox_version=secret_version, attempt_id=identifier.hex)
            receipt_path = pending / 'receipt.json'
            atomic_json(receipt_path, receipt)
            phase = 'upload'
            print(f'CHECK: {phase}', flush=True)
            try:
                version = cloud.put(data, config['bucket'], receipt['archiveKey'], access, secret,
                                    {'sha256': digest, 'environment': config['environment'], 'bytes': str(len(data))})
            except BaseException:
                receipt['status'] = 'incomplete'
                try:
                    atomic_json(receipt_path, receipt)
                except Exception:
                    pass
                raise
            receipt.update(archiveVersionId=version, status='uploaded', uploaded_utc=now())
            atomic_json(receipt_path, receipt)
            atomic_json(state / 'last-success.json', receipt)
            cipher.unlink()
            receipt_path.unlink()
            sync_directory(pending)
            pending.rmdir()
            (state / 'last-failure.json').unlink(missing_ok=True)
            sync_directory(state)
            print(f"PASS: {config['environment']} backup uploaded. Receipt: {state / 'last-success.json'}", flush=True)
            return receipt
        except BaseException:
            try:
                atomic_json(state / 'last-failure.json', {'phase': phase, 'failed_utc': now()})
            except Exception:
                pass
            raise


def status(config, state=STATE, max_age=3600):
    host_guard(config)
    private_dir(state)
    try:
        value = json.loads(read_private(state / 'last-success.json', 8192))
    except FileNotFoundError:
        raise Failure('No successful backup recorded') from None
    if (value.get('status') != 'uploaded' or value.get('environment') != config['environment']
            or value.get('bucket') != config['bucket']):
        raise Failure('Last successful backup has invalid context')
    stamp = datetime.strptime(value['snapshot_utc'], '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=timezone.utc)
    age = (datetime.now(timezone.utc) - stamp).total_seconds()
    if not 0 <= age <= max_age:
        raise Failure('Last successful backup is stale or clock changed')
    attempt = json.loads(read_private(state / 'last-attempt.json', 8192))
    if attempt.get('attempt_id') != value.get('attempt_id'):
        try:
            with locked(state / 'job.lock'):
                raise Failure('Last backup attempt did not complete')
        except Busy:
            print(f"INFO: backup in progress; previous confirmed snapshot age={int(age)}s.")
            return
    failure = state / 'last-failure.json'
    if failure.exists() or failure.is_symlink():
        read_private(failure, 8192)
        raise Failure('Last backup attempt failed; inspect service status')
    print(f"PASS: {config['environment']} last backup age={int(age)}s; upload acknowledged, restore checked separately.")


def main():
    os.umask(0o077)
    def interrupted(unused_signal, unused_frame):
        raise Failure('Backup interrupted')
    signal.signal(signal.SIGTERM, interrupted)
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=('run', 'check-access', 'status'))
    parser.add_argument('--config', type=Path, default=Path('/etc/purrmatch-backup/config.json'))
    parser.add_argument('--authorized-key', type=Path)
    parser.add_argument('--deny-secret')
    args = parser.parse_args()
    config = load_config(args.config)
    host_guard(config)
    if args.mode == 'check-access':
        _, _, version = credentials(config, authorized_key(args.authorized_key), args.deny_secret)
        print(f"PASS: {config['environment']} Lockbox access; version={version}; S3 keys not displayed.")
    elif args.mode == 'status':
        status(config)
    else:
        if args.deny_secret:
            raise Failure('Cross-secret probe is only allowed in check-access mode')
        run(config, args.authorized_key)


if __name__ == '__main__':
    try:
        main()
    except (Exception, KeyboardInterrupt) as error:
        message = str(error) if isinstance(error, (Failure, capture.Failure, cloud.Failure)) else 'local operation failed'
        print(f'FAIL: {message}', file=sys.stderr)
        sys.exit(1)
