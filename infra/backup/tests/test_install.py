import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[1]


@unittest.skipUnless(sys.platform == 'linux' and os.geteuid() == 0, 'Linux root fixture required')
class InstallTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='purrmatch-install-fixture-')
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.fs, self.bin, self.source = (self.base / name for name in ('fs', 'bin', 'source'))
        for directory in (self.fs, self.bin, self.source):
            directory.mkdir(mode=0o700)
        for name in ('opt', 'etc', 'var', 'var/lib', 'etc/systemd', 'etc/systemd/system'):
            (self.fs / name).mkdir(mode=0o755, exist_ok=True)
        for name in ('backup.py', 'capture.py', 'cloud.py', 'purrmatch-backup.service',
                     'purrmatch-backup.timer', 'config.example.json'):
            shutil.copyfile(SOURCE / name, self.source / name)
        self.log = self.base / 'systemctl.jsonl'
        self.env = dict(os.environ, FIXTURE_HOST='purrmatch-test', FIXTURE_ACTIVE='', FIXTURE_LOG=str(self.log))
        self.executable('hostname', '#!/bin/sh\nprintf "%s\\n" "$FIXTURE_HOST"\n')
        self.executable('systemctl', '''#!/usr/bin/python3
import json, os, sys
args = sys.argv[1:]
assert args.pop(0) == '--no-pager'
with open(os.environ['FIXTURE_LOG'], 'a') as stream:
    stream.write(json.dumps(args) + '\\n')
if args[0] in ('list-units', 'list-unit-files'):
    print('purrmatch-backup.service loaded inactive dead')
    print('purrmatch-backup.timer loaded inactive dead')
elif args[0] == 'show':
    assert args[2:] == ['-p', 'ActiveState', '--value']
    print('active' if args[1] == os.environ['FIXTURE_ACTIVE'] else 'inactive')
elif args != ['daemon-reload']:
    sys.exit('Forbidden systemctl fixture operation')
''')
        original = (SOURCE / 'install.sh').read_text()
        mapped = re.sub(r'(?<![A-Za-z0-9_/])/(?:opt|etc|var)(?=/|[\s"\'])',
                        lambda match: str(self.fs) + match[0], original)
        mapped = mapped.replace('export PATH=/usr/sbin:/usr/bin:/sbin:/bin',
                                f'export PATH={self.bin}:/usr/sbin:/usr/bin:/sbin:/bin')
        mapped = mapped.replace('/usr/bin/systemctl', str(self.bin / 'systemctl'))
        self.assertNotRegex(mapped, r'(?<![A-Za-z0-9_/])/(?:opt|etc|var)(?=/|[\s"\'])')
        self.assertNotIn('/usr/bin/systemctl', mapped)
        self.assertEqual(mapped.count('/usr/bin/python3'), original.count('/usr/bin/python3'))
        self.assertIn(f'export PATH={self.bin}:', mapped)
        self.script = self.source / 'install.sh'
        self.script.write_text(mapped)

    def executable(self, name, content):
        path = self.bin / name
        path.write_text(content)
        path.chmod(0o700)

    def invoke(self, environment='test'):
        return subprocess.run(['/bin/bash', str(self.script), environment], env=self.env,
                              stdin=subprocess.DEVNULL, capture_output=True, text=True, timeout=30)

    def snapshot(self):
        return {str(path.relative_to(self.fs)): (path.lstat().st_mode, path.read_bytes() if path.is_file() else None)
                for path in self.fs.rglob('*')}

    def test_first_install_private_config_code_units_and_no_activation(self):
        self.env['FIXTURE_HOST'] = 'purrmatch-prod'
        result = self.invoke('prod')
        self.assertEqual(result.returncode, 0, result.stderr)
        for name in ('opt/purrmatch-backup', 'etc/purrmatch-backup', 'var/lib/purrmatch-backup'):
            info = (self.fs / name).stat()
            self.assertEqual((info.st_uid, info.st_gid, stat.S_IMODE(info.st_mode)),
                             (0, 0, 0o755 if name.startswith('opt/') else 0o700))
        for path in self.fs.rglob('*'):
            if path.is_file():
                self.assertEqual((path.stat().st_uid, path.stat().st_gid), (0, 0))
                self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600 if path.name == 'config.json' else 0o644)
        self.assertEqual(json.loads((self.fs / 'etc/purrmatch-backup/config.json').read_text())['environment'], 'prod')
        self.assertEqual(json.loads(self.log.read_text().splitlines()[-1]), ['daemon-reload'])
        for name in ('backup.py', 'capture.py', 'cloud.py'):
            self.assertEqual((self.fs / 'opt/purrmatch-backup' / name).read_bytes(), (SOURCE / name).read_bytes())
        for name in ('purrmatch-backup.service', 'purrmatch-backup.timer'):
            self.assertEqual((self.fs / 'etc/systemd/system' / name).read_bytes(), (SOURCE / name).read_bytes())
        self.assertFalse((self.fs / 'etc/purrmatch-backup/authorized-key.json').exists())

    def test_update_preserves_existing_config_and_key(self):
        self.assertEqual(self.invoke().returncode, 0)
        directory = self.fs / 'etc/purrmatch-backup'
        contents = {'config.json': b'{"environment":"test","fixture":"preserve"}\n',
                    'authorized-key.json': b'DISPOSABLE FIXTURE ONLY\n'}
        for name, data in contents.items():
            (directory / name).write_bytes(data)
            (directory / name).chmod(0o600)
        result = self.invoke()
        self.assertEqual(result.returncode, 0, result.stderr)
        for name, data in contents.items():
            self.assertEqual((directory / name).read_bytes(), data)
            self.assertEqual(stat.S_IMODE((directory / name).stat().st_mode), 0o600)

    def test_active_service_or_timer_refuses_before_writes(self):
        before = self.snapshot()
        for unit in ('purrmatch-backup.service', 'purrmatch-backup.timer'):
            with self.subTest(unit=unit):
                self.env['FIXTURE_ACTIVE'] = unit
                self.assertNotEqual(self.invoke().returncode, 0)
                self.assertEqual(self.snapshot(), before)

    def test_wrong_guest_refuses_before_writes_or_systemctl(self):
        before = self.snapshot()
        self.env['FIXTURE_HOST'] = 'purrmatch-prod'
        self.assertNotEqual(self.invoke().returncode, 0)
        self.assertEqual(self.snapshot(), before)
        self.assertFalse(self.log.exists())
