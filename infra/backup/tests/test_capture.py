from contextlib import ExitStack, closing, redirect_stderr, redirect_stdout
import hashlib
import importlib.util
import io
import os
from pathlib import Path
import sqlite3
import stat
import tarfile
import tempfile
import threading
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("backup_capture", Path(__file__).resolve().parents[1] / "capture.py")
CAPTURE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CAPTURE)
SECRET = b"DISPOSABLE SECRET NEVER PRINT\n"
NODE_UID, NS_UID = "11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222"


class CaptureTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="purrmatch-capture-fixture-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.root.chmod(0o700)
        self.work = self.root / "run/purrmatch-backup/work"
        self.work.mkdir(parents=True, mode=0o700)
        self.work.parent.chmod(0o700)
        self.original = {}
        for name in CAPTURE.SOURCES + ("etc/rancher/k3s/k3s.yaml", "usr/local/bin/k3s"):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            data = b"" if name.endswith("k3s.service.env") else SECRET
            path.write_bytes(data)
            path.chmod(0o644 if name.endswith("k3s.service") else 0o600)
            self.original[name] = data
        self.database = self.root / CAPTURE.DB
        self.database.parent.mkdir(parents=True)
        self.connection = sqlite3.connect(self.database)
        self.addCleanup(self.connection.close)
        self.connection.execute("PRAGMA journal_mode=WAL")
        self.connection.execute("CREATE TABLE baseline(id INTEGER)")
        self.connection.commit()
        self.connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        self.connection.execute("CREATE TABLE kine(id INTEGER PRIMARY KEY, value TEXT)")
        self.connection.execute("INSERT INTO kine VALUES (1, 'before')")
        self.connection.commit()
        self.database.chmod(0o600)
        self.commands, self.state_queries, self.fault = [], 0, None
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(patch.object(CAPTURE, "ROOT", self.root))
        self.stack.enter_context(patch.object(CAPTURE, "OWNER", os.geteuid()))
        self.stack.enter_context(patch.object(CAPTURE.socket, "gethostname", return_value="purrmatch-test"))
        self.stack.enter_context(patch.object(CAPTURE, "run", self.fake_run))

    def fake_run(self, args):
        self.commands.append(args)
        if args[0] == "/usr/bin/systemctl":
            self.assertEqual(args[1:4], ["--no-pager", "show", "k3s.service"])
            self.assertFalse(any(value in args for value in ("start", "stop", "restart", "enable")))
            self.state_queries += 1
            invocation = "b" * 32 if self.fault == "restart" and self.state_queries > 1 else "a" * 32
            enabled = "disabled" if self.fault == "disabled" else "enabled"
            return f"ActiveState=active\nSubState=running\nUnitFileState={enabled}\nMainPID=123\nInvocationID={invocation}"
        self.assertEqual(args[0], str(self.root / "usr/local/bin/k3s"))
        if args[1:] == ["--version"]:
            return "k3s version v1.36.6+k3s1 (fixture)\ngo version fixture"
        if args[1:3] == ["secrets-encrypt", "status"]:
            self.assertEqual(args[3:], ["--server=https://172.30.240.2:6443", "--data-dir=" + str(self.root / "var/lib/rancher/k3s")])
            stage = "reencrypt_active" if self.fault == "encryption" else "start"
            if self.fault == "encryption-drift" and self.state_queries > 1:
                stage = "reencrypt_finished"
            provider = "AESCBC" if self.fault == "provider" else "XSalsa20-POLY1305"
            return f"Encryption Status: Enabled\nCurrent Rotation Stage: {stage}\nServer Encryption Hashes: All hashes match\n\nActive Key Type Name\n * {provider} fixture-key"
        self.assertEqual(args[1:5], ["kubectl", "--kubeconfig=" + str(self.root / "etc/rancher/k3s/k3s.yaml"), "--server=https://172.30.240.2:6443", "--request-timeout=5s"])
        if args[5:6] == ["get"] and args[6].startswith("--raw="):
            self.assertIn(args[6], ("--raw=/readyz", "--raw=/api/v1/nodes/purrmatch-test/proxy/healthz"))
            return "not ready" if self.fault == "health" else "ok"
        if args[5:8] == ["get", "node", "purrmatch-test"]:
            return "33333333-3333-4333-8333-333333333333" if self.fault == "uid" and self.state_queries > 1 else NODE_UID
        self.assertEqual(args[5:8], ["get", "namespace", "kube-system"])
        return NS_UID

    def invoke(self, failure=False):
        output = io.StringIO()
        with redirect_stdout(output), redirect_stderr(output):
            if failure:
                with self.assertRaises(CAPTURE.Failure) as error:
                    CAPTURE.capture(self.work, "test")
                self.assertNotIn(SECRET.decode().strip(), str(error.exception))
                self.assertFalse((self.work / "test-k3s-state.tar.gz").exists())
                archive = None
            else:
                archive = CAPTURE.capture(self.work, "test")
        self.assertEqual(output.getvalue(), "")
        return archive

    def files(self, archive):
        with tarfile.open(archive, "r:gz") as stream:
            members = stream.getmembers()
            expected = {"files/" + name for name in CAPTURE.SOURCES} | {"files/" + CAPTURE.DB, "metadata/context.tsv", "metadata/k3s-version.txt", "metadata/k3s-binary.sha256", "SHA256SUMS"}
            self.assertEqual({member.name for member in members}, expected)
            self.assertEqual(len(members), 12)
            for member in members:
                self.assertTrue(member.isfile())
                self.assertEqual((member.uid, member.gid, member.mode), (0, 0, 0o600))
            return {member.name: stream.extractfile(member).read() for member in members}

    def test_wal_only_kine_is_merged_and_archive_matches_verifier_contract(self):
        main_only = self.root / "main-only.db"
        main_only.write_bytes(self.database.read_bytes())
        with closing(sqlite3.connect(main_only)) as connection:
            self.assertEqual(connection.execute("SELECT name FROM sqlite_master WHERE name='kine'").fetchall(), [])
        original_database = self.database.read_bytes()
        original_wal = Path(str(self.database) + "-wal").read_bytes()
        archive = self.invoke()
        files = self.files(archive)
        expected_hashes = b"".join(hashlib.sha256(data).hexdigest().encode() + b"  " + name.encode() + b"\n" for name, data in sorted(files.items()) if name != "SHA256SUMS")
        self.assertEqual(files["SHA256SUMS"], expected_hashes)
        context = dict(line.split("\t") for line in files["metadata/context.tsv"].decode().splitlines())
        self.assertEqual(context.keys(), {"schema", "environment", "node", "snapshot_utc", "node_uid", "kube_system_uid"})
        self.assertEqual((context["schema"], context["environment"], context["node"], context["node_uid"], context["kube_system_uid"]), ("purrmatch-k3s-state-v1", "test", "purrmatch-test", NODE_UID, NS_UID))
        self.assertRegex(context["snapshot_utc"], r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$")
        self.assertRegex(files["metadata/k3s-binary.sha256"].decode(), r"^[a-f0-9]{64}  /usr/local/bin/k3s\n$")
        self.assertEqual(self.database.read_bytes(), original_database)
        self.assertEqual(Path(str(self.database) + "-wal").read_bytes(), original_wal)
        for name, data in self.original.items():
            self.assertEqual((self.root / name).read_bytes(), data)
        with closing(sqlite3.connect((self.work / "state.db").as_uri() + "?mode=rw", uri=True)) as connection:
            self.assertEqual(connection.execute("SELECT value FROM kine").fetchall(), [("before",)])
            self.assertEqual(connection.execute("PRAGMA journal_mode").fetchone(), ("delete",))
        for path in self.work.iterdir():
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)

    def test_concurrent_writer_commits_while_snapshot_stays_at_first_read(self):
        started, committed = threading.Event(), threading.Event()
        errors = []
        def writer():
            try:
                if not started.wait(3):
                    raise AssertionError("snapshot not started")
                with closing(sqlite3.connect(self.database, timeout=1)) as connection:
                    connection.execute("INSERT INTO kine VALUES (2, 'after')")
                    connection.commit()
                committed.set()
            except Exception as error:
                errors.append(error)
                committed.set()
        real_connect = sqlite3.connect
        class Source:
            def __init__(self, connection):
                self.connection = connection
            def __getattr__(self, name):
                return getattr(self.connection, name)
            def backup(self, destination, **options):
                started.set()
                if not committed.wait(3) or errors:
                    raise AssertionError("writer did not commit")
                return self.connection.backup(destination, **options)
        def connect(path, *args, **kwargs):
            connection = real_connect(path, *args, **kwargs)
            return Source(connection) if isinstance(path, str) and path.endswith("?mode=ro") else connection
        thread = threading.Thread(target=writer)
        thread.start()
        try:
            with patch.object(CAPTURE.sqlite3, "connect", side_effect=connect):
                files = self.files(self.invoke())
        finally:
            started.set()
            thread.join(4)
        self.assertFalse(thread.is_alive())
        self.assertEqual(errors, [])
        self.assertEqual(self.connection.execute("SELECT count(*) FROM kine").fetchone(), (2,))
        snapshot = self.root / "checked-snapshot.db"
        snapshot.write_bytes(files["files/" + CAPTURE.DB])
        with closing(real_connect(snapshot)) as connection:
            self.assertEqual(connection.execute("SELECT value FROM kine").fetchall(), [("before",)])

    def test_drift_restart_identity_and_encryption_changes_reject_archive(self):
        for fault in ("restart", "uid", "encryption-drift"):
            with self.subTest(fault=fault):
                work = self.work / fault
                work.mkdir(mode=0o700)
                old_work, self.work = self.work, work
                self.fault, self.state_queries = fault, 0
                self.invoke(failure=True)
                self.work = old_work
        self.fault, self.state_queries = None, 0
        real_backup = CAPTURE.database_backup
        def drift(destination):
            stamp = real_backup(destination)
            (self.root / CAPTURE.SOURCES[0]).write_bytes(b"DISPOSABLE CHANGED TOKEN")
            return stamp
        with patch.object(CAPTURE, "database_backup", side_effect=drift):
            self.invoke(failure=True)

    def test_invalid_baselines_and_source_links_fail_before_database_write(self):
        for fault in ("disabled", "encryption", "provider", "health"):
            with self.subTest(fault=fault):
                self.fault, self.state_queries = fault, 0
                self.invoke(failure=True)
                self.assertFalse((self.work / "state.db").exists())
        self.fault = None
        token = self.root / CAPTURE.SOURCES[0]
        token.unlink()
        token.symlink_to(self.root / "etc/rancher/k3s/config.yaml")
        self.invoke(failure=True)
        self.assertFalse((self.work / "state.db").exists())

    def test_busy_database_fails_without_service_mutation(self):
        self.connection.close()
        self.connection = sqlite3.connect(self.database)
        self.addCleanup(self.connection.close)
        self.connection.execute("PRAGMA locking_mode=EXCLUSIVE")
        self.connection.execute("BEGIN EXCLUSIVE")
        self.invoke(failure=True)

    def test_deadline_and_member_size_limit_fail_closed(self):
        with patch.object(CAPTURE, "BACKUP_SECONDS", 0):
            self.invoke(failure=True)
        second = self.work / "second"
        second.mkdir(mode=0o700)
        self.work = second
        with patch.object(CAPTURE, "MAX_DB", 1):
            self.invoke(failure=True)

    def test_database_sidecar_symlink_rejected(self):
        self.connection.close()
        self.connection = sqlite3.connect(self.database)
        self.addCleanup(self.connection.close)
        sidecar = Path(str(self.database) + "-wal")
        sidecar.unlink(missing_ok=True)
        sidecar.symlink_to(self.root / CAPTURE.SOURCES[0])
        self.invoke(failure=True)


if __name__ == "__main__":
    unittest.main()
