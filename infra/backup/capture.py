from contextlib import closing
import datetime
import hashlib
import io
import os
from pathlib import Path
import re
import socket
import sqlite3
import stat
import subprocess
import tarfile
import time
import uuid


ROOT, OWNER = Path("/"), 0
NODES = {"test": ("purrmatch-test", "172.30.240.2"), "prod": ("purrmatch-prod", "172.30.240.18")}
SOURCES = ("var/lib/rancher/k3s/server/token", "var/lib/rancher/k3s/server/cred/encryption-config.json",
           "var/lib/rancher/k3s/server/cred/encryption-state.json", "etc/rancher/k3s/config.yaml",
           "etc/rancher/node/password", "etc/systemd/system/k3s.service", "etc/systemd/system/k3s.service.env")
DB = "var/lib/rancher/k3s/server/db/state.db"
MAX_DB, MAX_ARCHIVE, BACKUP_SECONDS = 64 * 1024**2, 16 * 1024**2, 60


class Failure(Exception):
    pass


def guard(path, directory=False, private=False):
    info = path.lstat()
    kind = stat.S_ISDIR if directory else stat.S_ISREG
    if (not kind(info.st_mode) or info.st_uid != OWNER or stat.S_IMODE(info.st_mode) & 0o022
            or private and stat.S_IMODE(info.st_mode) != (0o700 if directory else 0o600)):
        raise Failure("Unsafe capture path")
    parent = path.parent
    while path != ROOT:
        info = parent.lstat()
        if not stat.S_ISDIR(info.st_mode) or info.st_uid != OWNER or stat.S_IMODE(info.st_mode) & 0o022:
            raise Failure("Unsafe capture path parent")
        if parent == ROOT:
            break
        path, parent = parent, parent.parent


def read_source(path, limit=1024**2, empty=False):
    guard(path)
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(descriptor, "rb") as stream:
        info = os.fstat(stream.fileno())
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != OWNER or stat.S_IMODE(info.st_mode) & 0o022
                or not (0 if empty else 1) <= info.st_size <= limit):
            raise Failure("Invalid capture source")
        data = stream.read(limit + 1)
        if len(data) != info.st_size or len(data) > limit:
            raise Failure("Capture source changed or exceeds limit")
    return data


def binary_digest():
    path = ROOT / "usr/local/bin/k3s"
    guard(path)
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    digest, count = hashlib.sha256(), 0
    with os.fdopen(descriptor, "rb") as stream:
        info = os.fstat(stream.fileno())
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != OWNER or stat.S_IMODE(info.st_mode) & 0o022
                or not 0 < info.st_size <= 256 * 1024**2):
            raise Failure("Invalid K3s binary")
        while data := stream.read(1024**2):
            count += len(data)
            if count > 256 * 1024**2:
                raise Failure("K3s binary exceeds limit")
            digest.update(data)
        if count != info.st_size:
            raise Failure("K3s binary changed")
    return digest.hexdigest()


def run(arguments):
    environment = {"PATH": "/usr/sbin:/usr/bin:/sbin:/bin", "LANG": "C", "LC_ALL": "C"}
    if "HOME" in os.environ:
        environment["HOME"] = os.environ["HOME"]
    try:
        result = subprocess.run(arguments, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                env=environment, timeout=20, check=False, shell=False)
        if result.returncode or len(result.stdout) > 65536:
            raise Failure("K3s status command failed")
        return result.stdout.decode("utf-8").strip()
    except (OSError, UnicodeError, subprocess.TimeoutExpired):
        raise Failure("K3s status command failed") from None


def cluster_state(environment):
    node, address = NODES[environment]
    selected = ("ActiveState", "SubState", "UnitFileState", "MainPID", "InvocationID")
    output = run(["/usr/bin/systemctl", "--no-pager", "show", "k3s.service", "--property=" + ",".join(selected)])
    fields = dict(line.split("=", 1) for line in output.splitlines())
    if (fields.keys() != set(selected) or fields["ActiveState"] != "active" or fields["SubState"] != "running"
            or fields["UnitFileState"] != "enabled" or not re.fullmatch(r"[1-9][0-9]*", fields["MainPID"])
            or not re.fullmatch(r"[0-9a-f]{32}", fields["InvocationID"])):
        raise Failure("K3s service baseline rejected")
    k3s = str(ROOT / "usr/local/bin/k3s")
    base = [k3s, "kubectl", "--kubeconfig=" + str(ROOT / "etc/rancher/k3s/k3s.yaml"),
            "--server=https://" + address + ":6443", "--request-timeout=5s"]
    if run(base + ["get", "--raw=/readyz"]) != "ok" or run(base + ["get", f"--raw=/api/v1/nodes/{node}/proxy/healthz"]) != "ok":
        raise Failure("K3s API or kubelet is not healthy")
    identities = [run(base + ["get", kind, name, "-o", "jsonpath={.metadata.uid}"])
                  for kind, name in (("node", node), ("namespace", "kube-system"))]
    if any(str(uuid.UUID(value)) != value for value in identities):
        raise Failure("Invalid cluster identity")
    version = run([k3s, "--version"])
    if not version.startswith("k3s version v1.36.6+k3s1 "):
        raise Failure("Unexpected K3s version")
    encryption = run([k3s, "secrets-encrypt", "status", "--server=https://" + address + ":6443",
                      "--data-dir=" + str(ROOT / "var/lib/rancher/k3s")])
    if ("Encryption Status: Enabled" not in encryption.splitlines()
            or "Server Encryption Hashes: All hashes match" not in encryption.splitlines()
            or not re.search(r"(?m)^Current Rotation Stage: (start|reencrypt_finished)$", encryption)
            or not re.search(r"(?m)^\s*\*\s+XSalsa20-POLY1305\s+\S+\s*$", encryption)):
        raise Failure("K3s encryption baseline rejected")
    return fields, identities, version, encryption


def database_backup(destination):
    source = ROOT / DB
    guard(source)
    if not 0 < source.stat().st_size <= MAX_DB or (source.parent / "etcd").exists() or (source.parent / "etcd").is_symlink():
        raise Failure("Unsupported K3s datastore")
    for suffix in ("-wal", "-shm"):
        sidecar = source.with_name(source.name + suffix)
        if sidecar.exists() or sidecar.is_symlink():
            guard(sidecar)
            if sidecar.stat().st_size > 2 * MAX_DB:
                raise Failure("SQLite sidecar exceeds limit")
    deadline = time.monotonic() + BACKUP_SECONDS
    def deadline_check(*unused):
        if time.monotonic() >= deadline:
            raise Failure("SQLite capture deadline exceeded")
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    os.close(descriptor)
    try:
        with closing(sqlite3.connect(source.as_uri() + "?mode=ro", uri=True, timeout=1, isolation_level=None)) as src:
            src.execute("PRAGMA trusted_schema=OFF")
            src.execute("PRAGMA query_only=ON")
            if src.execute("PRAGMA journal_mode").fetchone() != ("wal",):
                raise Failure("Online capture requires SQLite WAL mode")
            src.execute("BEGIN")
            stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            src.execute("SELECT rootpage FROM sqlite_schema LIMIT 1").fetchall()
            page_size = src.execute("PRAGMA page_size").fetchone()[0]
            if src.execute("PRAGMA page_count").fetchone()[0] * page_size > MAX_DB:
                raise Failure("SQLite snapshot exceeds limit")
            with closing(sqlite3.connect(destination, timeout=1, isolation_level=None)) as dst:
                src.backup(dst, pages=128, progress=deadline_check, sleep=0.05)
                src.rollback()
                deadline_check()
                dst.set_progress_handler(lambda: int(time.monotonic() >= deadline), 1000)
                if dst.execute("PRAGMA journal_mode=DELETE").fetchone() != ("delete",):
                    raise Failure("SQLite snapshot journal mode rejected")
                if dst.execute("PRAGMA integrity_check").fetchall() != [("ok",)]:
                    raise Failure("SQLite integrity check failed")
                if dst.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='kine'").fetchall() != [(1,)]:
                    raise Failure("SQLite kine table missing")
                deadline_check()
        if not 0 < destination.stat().st_size <= MAX_DB:
            raise Failure("SQLite snapshot exceeds limit")
        return stamp
    except sqlite3.Error:
        raise Failure("SQLite capture failed") from None


def capture(work: Path, environment: str) -> Path:
    try:
        os.umask(0o077)
        if environment not in NODES or os.geteuid() != OWNER or socket.gethostname() != NODES[environment][0]:
            raise Failure("Capture requires root on the selected guest")
        work = Path(work)
        guard(work, directory=True, private=True)
        guard(ROOT / "etc/rancher/k3s/k3s.yaml", private=True)
        before = {name: read_source(ROOT / name, empty=name.endswith("k3s.service.env")) for name in SOURCES}
        state, digest = cluster_state(environment), binary_digest()
        database = work / "state.db"
        stamp = database_backup(database)
        after = {name: read_source(ROOT / name, empty=name.endswith("k3s.service.env")) for name in SOURCES}
        if before != after or state != cluster_state(environment) or digest != binary_digest():
            raise Failure("Cluster or sensitive configuration changed during capture")
        files = {"files/" + name: data for name, data in before.items()}
        files["files/" + DB] = read_source(database, MAX_DB)
        files["metadata/context.tsv"] = ("schema\tpurrmatch-k3s-state-v1\nenvironment\t" + environment + "\nnode\t" + NODES[environment][0]
            + "\nsnapshot_utc\t" + stamp + "\nnode_uid\t" + state[1][0] + "\nkube_system_uid\t" + state[1][1] + "\n").encode()
        files["metadata/k3s-version.txt"] = (state[2] + "\n").encode()
        files["metadata/k3s-binary.sha256"] = (digest + "  /usr/local/bin/k3s\n").encode()
        files["SHA256SUMS"] = b"".join(hashlib.sha256(data).hexdigest().encode() + b"  " + name.encode() + b"\n" for name, data in sorted(files.items()))
        partial = work / (environment + "-k3s-state.tar.gz.partial")
        final = work / (environment + "-k3s-state.tar.gz")
        if final.exists() or final.is_symlink():
            raise Failure("Capture destination already exists")
        with partial.open("xb") as stream, tarfile.open(fileobj=stream, mode="w:gz", format=tarfile.USTAR_FORMAT) as archive:
            for name, data in sorted(files.items()):
                member = tarfile.TarInfo(name)
                member.mode, member.uid, member.gid, member.size = 0o600, 0, 0, len(data)
                archive.addfile(member, io.BytesIO(data))
        if not 0 < partial.stat().st_size <= MAX_ARCHIVE:
            raise Failure("Compressed capture exceeds limit")
        os.replace(partial, final)
        return final
    except Failure:
        raise
    except Exception:
        raise Failure("K3s capture failed") from None
