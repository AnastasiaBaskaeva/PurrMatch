import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]


def run_script(name, *arguments, env=None):
    process_env = os.environ.copy()
    for key in list(process_env):
        if key.startswith("PURRMATCH_"):
            del process_env[key]
    process_env.update(env or {})
    return subprocess.run(
        ["bash", str(ROOT / name), *arguments], env=process_env,
        text=True, capture_output=True, timeout=120,
    )


def host_function(name):
    source = (ROOT / "prepare-host.sh").read_text()
    return re.search(r"^" + name + r"\(\) \{\n.*?^\}\n", source, re.M | re.S)[0]


class BmsChecks(unittest.TestCase):
    def test_syntax_and_help(self):
        for script in ROOT.glob("*.sh"):
            with self.subTest(script=script.name):
                subprocess.run(["bash", "-n", str(script)], check=True)
                if script.name != "common.sh":
                    result = run_script(script.name, "--help")
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertIn("Usage:", result.stdout)

    def test_network_address_plans(self):
        for cidr in ("172.30.240.0/20", "10.20.0.0/20", "192.168.16.0/20"):
            for environment, offset, suffix in (("test", 0, "01"), ("prod", 16, "02")):
                with self.subTest(cidr=cidr, environment=environment):
                    result = run_script("create-networks.sh", "--render", environment, "etx2",
                                        env={"PURRMATCH_NETWORK_CIDR": cidr})
                    self.assertEqual(result.returncode, 0, result.stderr)
                    network = ET.fromstring(result.stdout)
                    base = ipaddress.ip_network(cidr).network_address + offset
                    self.assertEqual(network.findtext("name"), f"purrmatch-{environment}")
                    self.assertEqual(network.find("forward").attrib, {"mode": "nat", "dev": "etx2"})
                    self.assertEqual(network.find("bridge").get("stp"), "off")
                    self.assertEqual(network.find("mtu").get("size"), "1500")
                    self.assertEqual(network.find("ip").get("address"), str(base + 1))
                    self.assertEqual(network.find("ip/dhcp/host").get("ip"), str(base + 2))
                    self.assertEqual(network.find("ip/dhcp/host").get("mac"), "52:54:00:50:4d:" + suffix)
                    self.assertFalse(network.findall("ip/dhcp/range"))

    def test_invalid_addresses_and_interfaces(self):
        for cidr in ("8.8.0.0/20", "100.64.0.0/20", "172.30.240.1/20", "172.30.240.0/21", "fd00::/20"):
            result = run_script("create-networks.sh", "--render", "test", "etx2",
                                env={"PURRMATCH_NETWORK_CIDR": cidr})
            self.assertNotEqual(result.returncode, 0, cidr)
        for interface in ("../etx2", "etx2'", "etx2&", "a" * 16):
            self.assertNotEqual(run_script("create-networks.sh", "--render", "test", interface).returncode, 0)

    def test_domain_resources_and_boundaries(self):
        for environment, cpus, ram, suffix in (("test", 4, 8192, "01"), ("prod", 12, 32768, "02")):
            result = run_script("create-vm.sh", environment, "--render")
            self.assertEqual(result.returncode, 0, result.stderr)
            domain = ET.fromstring(result.stdout)
            self.assertEqual(domain.findtext("vcpu"), str(cpus))
            self.assertEqual(domain.findtext("memory"), str(ram))
            self.assertEqual(domain.findtext("cputune/global_quota"), str(cpus * 100000))
            self.assertEqual(domain.findtext("cputune/global_period"), "100000")
            self.assertIsNotNone(domain.find("memoryBacking/nosharepages"))
            self.assertEqual(domain.find("devices/memballoon").get("model"), "none")
            self.assertFalse(domain.findall("devices/filesystem"))
            self.assertFalse(domain.findall("devices/graphics"))
            self.assertFalse(domain.findall("devices/hostdev"))
            self.assertEqual({node.get("model") for node in domain.findall("seclabel")}, {"apparmor", "dac"})
            self.assertEqual(domain.find("devices/interface/mac").get("address"), "52:54:00:50:4d:" + suffix)
            for source in domain.findall("devices/disk/source"):
                self.assertTrue(source.get("file").startswith(f"/var/lib/libvirt/images/purrmatch-{environment}/"))
            disk = domain.find("devices/disk[@device='disk']")
            self.assertEqual(disk.find("driver").get("discard"), "ignore")
            self.assertEqual(disk.find("driver").get("detect_zeroes"), "off")
            self.assertEqual(disk.findtext("iotune/total_bytes_sec"), "104857600")
            self.assertEqual(disk.findtext("iotune/total_iops_sec"), "1000")

    def test_resource_overrides_and_invalid_values(self):
        result = run_script("create-vm.sh", "test", "--render",
                            env={"PURRMATCH_TEST_VCPUS": "2", "PURRMATCH_TEST_RAM_MIB": "4096"})
        domain = ET.fromstring(result.stdout)
        self.assertEqual(domain.findtext("vcpu"), "2")
        self.assertEqual(domain.findtext("memory"), "4096")
        for value in ("0", "-1", "1;id", "2147483648", "999999999999999999999"):
            self.assertNotEqual(run_script("create-vm.sh", "test", "--render",
                                          env={"PURRMATCH_TEST_VCPUS": value}).returncode, 0)
        self.assertNotEqual(run_script("create-vm.sh", "../prod", "--render").returncode, 0)

    @unittest.skipUnless(shutil.which("virt-xml-validate"), "libvirt XML validator is not installed")
    def test_libvirt_xml_schema(self):
        with tempfile.TemporaryDirectory() as directory:
            for environment in ("test", "prod"):
                for command, arguments, schema in (
                    ("create-vm.sh", (environment, "--render"), "domain"),
                    ("create-networks.sh", ("--render", environment, "etx2"), "network"),
                ):
                    result = run_script(command, *arguments)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    path = Path(directory) / f"{environment}-{schema}.xml"
                    path.write_text(result.stdout)
                    validation = subprocess.run(["virt-xml-validate", str(path), schema], text=True, capture_output=True)
                    self.assertEqual(validation.returncode, 0, validation.stderr)

    def test_apt_transaction_refuses_upgrades_and_removals(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "simulation"
            for text, valid in (
                ("Inst qemu-utils (1:8.2.2+ds-0ubuntu1.18 Ubuntu:24.04/noble [amd64])\n", True),
                ("Inst qemu-utils [1:8.2.2] (1:8.2.3 Ubuntu:24.04/noble [amd64])\n", False),
                ("Remv important-service [1.0]\n", False),
            ):
                path.write_text(text)
                result = subprocess.run(["bash", "-c", "set -euo pipefail\n" + host_function("transaction") + '\ntransaction "$1"', "test", str(path)], text=True, capture_output=True)
                self.assertEqual(result.returncode == 0, valid, result.stderr)

    def test_probe_errors_cannot_be_treated_as_empty_success(self):
        setup = '''
die() { printf '%s\\n' "$*" >&2; exit 1; }
systemctl() { if [[ $1 == is-enabled ]]; then printf 'disabled\\n'; else printf 'inactive\\n'; fi; }
ss() { return 7; }
'''
        result = subprocess.run(["bash", "-c", "set -euo pipefail\n" + setup + host_function("management_closed") + "\nmanagement_closed\nprintf unexpected"], text=True, capture_output=True)
        self.assertEqual(result.returncode, 7)
        self.assertNotIn("unexpected", result.stdout)

    def test_specific_routes_and_connected_networks_reject_overlap(self):
        source = (ROOT / "create-networks.sh").read_text()
        snippets = re.findall(r"ip -j -4 (?:address show|route show table all) \| python3 -c '\n(.*?)\n'", source, re.S)
        self.assertEqual(len(snippets), 2)
        cases = (
            (snippets[0], [{"ifname": "etx1", "addr_info": [{"family": "inet", "local": "10.0.1.10", "prefixlen": 24}]}], True),
            (snippets[0], [{"ifname": "vpn", "addr_info": [{"family": "inet", "local": "172.30.0.1", "prefixlen": 16}]}], False),
            (snippets[1], [{"dst": "default"}, {"dst": "10.0.0.0/8"}, {"dst": "172.16.0.0/12"}, {"dst": "192.168.0.0/16"}], True),
            (snippets[1], [{"dst": "172.30.240.0/24"}], False),
        )
        for snippet, data, valid in cases:
            result = subprocess.run(["python3", "-c", snippet, "172.30.240.0/20"], input=json.dumps(data), text=True, capture_output=True)
            self.assertEqual(result.returncode == 0, valid, result.stderr)


CONTAINER_TESTS = os.environ.get("PURRMATCH_BMS_TEST_SANDBOX") == "1" and Path("/.dockerenv").exists() and os.geteuid() == 0


@unittest.skipUnless(CONTAINER_TESTS, "isolated disposable Docker container is required")
class BmsContainerChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fixture = Path(self.temp.name)
        self.state = self.fixture / "virsh-state.json"
        self.log = self.fixture / "virsh-log"
        self.state.write_text("{}")
        owned = [Path("/root/purrmatch-bms"), Path("/var/lib/purrmatch-bms"), Path("/var/lib/libvirt/images/purrmatch-test"), Path("/var/lib/libvirt/images/purrmatch-prod")]
        for path in owned:
            self.assertFalse(path.exists(), f"Fixture requires an empty disposable path: {path}")
            self.addCleanup(lambda path=path: shutil.rmtree(path, ignore_errors=True))
        if subprocess.run(["id", "libvirt-qemu"], capture_output=True).returncode:
            subprocess.run(["groupadd", "--system", "libvirt-qemu"], check=True)
            subprocess.run(["useradd", "--system", "--gid", "libvirt-qemu", "--no-create-home", "libvirt-qemu"], check=True)
        Path("/var/lib/libvirt/images").mkdir(parents=True, exist_ok=True)
        cache = Path("/var/lib/purrmatch-bms/noble-amd64")
        cache.mkdir(parents=True, mode=0o700)
        cache.parent.chmod(0o700)
        image = cache / "ubuntu-24.04-server-cloudimg-amd64.img"
        subprocess.run(["qemu-img", "create", "-f", "qcow2", str(image), "16M"], check=True, capture_output=True)
        (cache / "image.sha256").write_text(hashlib.sha256(image.read_bytes()).hexdigest() + "  " + image.name + "\n")
        (cache / "source-url").write_text("https://cloud-images.ubuntu.com/releases/noble/release/" + image.name + "\n")
        (cache / "complete").touch()
        self.bin = self.fixture / "bin"
        self.bin.mkdir()
        scripts = {
            "findmnt": "#!/bin/bash\nprintf 'ext4\\n'\n",
            "curl": "#!/bin/bash\nprintf curl-called >> \"$BMS_FIXTURE_LOG\"\nexit 71\n",
            "awk": "#!/bin/bash\nif [[ ${!#} == /proc/meminfo ]]; then if [[ $1 == *MemTotal* ]]; then printf '65011712\\n'; else printf '62914560\\n'; fi; else exec /usr/bin/awk \"$@\"; fi\n",
            "virsh": '''#!/usr/bin/python3
import json, os, subprocess, sys
from pathlib import Path
import xml.etree.ElementTree as ET
args = sys.argv[1:]
if args[:1] == ["-c"]:
    args = args[2:]
with open(os.environ["BMS_FIXTURE_LOG"], "a") as log:
    log.write(" ".join(args) + "\\n")
path = Path(os.environ["BMS_FIXTURE_STATE"])
state = json.loads(path.read_text())
cmd = args[0]
if cmd == "list":
    print("\\n".join(name for name, item in state.items() if "--all" in args or item["state"] == "running"))
elif cmd == "net-list":
    print("purrmatch-test\\npurrmatch-prod")
elif cmd == "net-dumpxml":
    env = args[1].removeprefix("purrmatch-")
    print(subprocess.check_output(["bash", os.environ["BMS_SOURCE"] + "/create-networks.sh", "--render", env, "etx2"], text=True))
elif cmd == "capabilities":
    print("<capabilities><host><secmodel><model>apparmor</model></secmodel><secmodel><model>dac</model></secmodel></host></capabilities>")
elif cmd == "define":
    text = Path(args[1]).read_text()
    root = ET.fromstring(text)
    state[root.findtext("name")] = {"xml": text, "state": "shut off"}
    path.write_text(json.dumps(state))
elif cmd in ("domuuid", "domstate", "dumpxml", "dominfo"):
    item = state[args[1]]
    root = ET.fromstring(item["xml"])
    print({"domuuid": root.findtext("uuid"), "domstate": item["state"], "dumpxml": item["xml"], "dominfo": args[1]}[cmd])
elif cmd == "start":
    raise SystemExit(42)
else:
    raise SystemExit("Unexpected fake libvirt call: " + cmd)
''',
        }
        for name, content in scripts.items():
            path = self.bin / name
            path.write_text(content)
            path.chmod(0o755)
        self.env = {"PATH": str(self.bin) + ":" + os.environ["PATH"], "BMS_FIXTURE_STATE": str(self.state), "BMS_FIXTURE_LOG": str(self.log), "BMS_SOURCE": str(ROOT), "PURRMATCH_TEST_VCPUS": "1", "PURRMATCH_TEST_RAM_MIB": "32768", "PURRMATCH_TEST_DISK_GIB": "1", "PURRMATCH_PROD_VCPUS": "1", "PURRMATCH_PROD_DISK_GIB": "1"}

    def test_prepare_identity_repeat_refusal_and_ram_budget(self):
        first = run_script("create-vm.sh", "test", env=self.env)
        self.assertEqual(first.returncode, 0, first.stderr)
        state_dir = Path("/root/purrmatch-bms/vms/test")
        key = state_dir / "id_ed25519"
        self.assertEqual(key.stat().st_mode & 0o777, 0o600)
        self.assertEqual(state_dir.stat().st_mode & 0o777, 0o700)
        original_key = key.read_bytes()
        original_uuid = (state_dir / "uuid").read_text()
        repeated = run_script("create-vm.sh", "test", env=self.env)
        self.assertNotEqual(repeated.returncode, 0)
        self.assertEqual(key.read_bytes(), original_key)
        self.assertEqual((state_dir / "uuid").read_text(), original_uuid)
        second = run_script("create-vm.sh", "prod", env=self.env)
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertNotEqual(Path("/root/purrmatch-bms/vms/prod/id_ed25519").read_bytes(), original_key)
        state = json.loads(self.state.read_text())
        state["purrmatch-prod"]["state"] = "running"
        self.state.write_text(json.dumps(state))
        self.log.write_text("")
        rejected = run_script("vm.sh", "test", "start", env=self.env)
        self.assertNotEqual(rejected.returncode, 0)
        self.assertNotIn("start purrmatch-test", self.log.read_text())
        root = ET.fromstring(state["purrmatch-prod"]["xml"])
        root.find("memory").text = "8192"
        state["purrmatch-prod"]["xml"] = ET.tostring(root, encoding="unicode")
        self.state.write_text(json.dumps(state))
        accepted = run_script("vm.sh", "test", "start", env=self.env)
        self.assertEqual(accepted.returncode, 42, accepted.stderr)
        self.assertIn("start purrmatch-test", self.log.read_text())

    def test_incomplete_cache_is_not_overwritten_or_downloaded(self):
        Path("/var/lib/purrmatch-bms/noble-amd64/complete").unlink()
        result = run_script("create-vm.sh", "test", env=self.env)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Incomplete", result.stderr)
        self.assertNotIn("curl-called", self.log.read_text())
        self.assertFalse(Path("/root/purrmatch-bms/vms/test").exists())


if __name__ == "__main__":
    unittest.main()
