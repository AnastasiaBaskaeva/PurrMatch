#!/usr/bin/env bash
set -Eeuo pipefail
trap 'printf "Failed at line %s (exit %s).\n" "$LINENO" "$?" >&2' ERR
umask 077
export LC_ALL=C
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

usage() { printf 'Usage: %s test|prod [--render] | --help\n' "$0"; }
render_domain() {
    cat <<XML
<domain type='kvm'>
  <name>$VM_NAME</name>
  <uuid>$VM_UUID</uuid>
  <memory unit='MiB'>$VM_RAM_MIB</memory>
  <currentMemory unit='MiB'>$VM_RAM_MIB</currentMemory>
  <memoryBacking><nosharepages/></memoryBacking>
  <vcpu placement='static'>$VM_CPUS</vcpu>
  <cputune><global_period>100000</global_period><global_quota>$((VM_CPUS * 100000))</global_quota></cputune>
  <os><type arch='x86_64' machine='q35'>hvm</type><boot dev='hd'/></os>
  <features><acpi/><apic/></features>
  <cpu mode='host-passthrough'><topology sockets='1' cores='$VM_CPUS' threads='1'/></cpu>
  <clock offset='utc'/>
  <on_poweroff>destroy</on_poweroff><on_reboot>restart</on_reboot><on_crash>destroy</on_crash>
  <devices>
    <emulator>/usr/bin/qemu-system-x86_64</emulator>
    <disk type='file' device='disk'>
      <driver name='qemu' type='qcow2' cache='none' discard='ignore' detect_zeroes='off'/>
      <source file='$VM_IMAGE_DIR/disk.qcow2'/><target dev='vda' bus='virtio'/>
      <iotune><total_bytes_sec>104857600</total_bytes_sec><total_iops_sec>1000</total_iops_sec></iotune>
    </disk>
    <disk type='file' device='cdrom'>
      <driver name='qemu' type='raw'/><source file='$VM_IMAGE_DIR/seed.img'/>
      <target dev='sda' bus='sata'/><readonly/>
    </disk>
    <interface type='network'><mac address='$VM_MAC'/><source network='$VM_NAME'/><model type='virtio'/></interface>
    <serial type='pty'><target port='0'/></serial>
    <console type='pty'><target type='serial' port='0'/></console>
    <video><model type='none'/></video><memballoon model='none'/>
    <rng model='virtio'><backend model='random'>/dev/urandom</backend></rng>
  </devices>
  <seclabel type='dynamic' model='apparmor' relabel='yes'/>
  <seclabel type='dynamic' model='dac' relabel='yes'/>
</domain>
XML
}
if [[ ${1:-} == --help ]]; then usage; exit 0; fi
[[ $# -eq 1 || ( $# -eq 2 && $2 == --render ) ]] || { usage >&2; exit 1; }
select_environment "$1"
if [[ ${2:-} == --render ]]; then
    VM_UUID=00000000-0000-4000-8000-000000000000
    render_domain
    exit 0
fi
require_host
for command in virsh qemu-img cloud-localds curl ssh-keygen sha256sum python3 nproc findmnt; do require_command "$command"; done
lock_operations
[[ -c /dev/kvm ]] || die 'KVM is missing.'
host_cpus=$(nproc)
[[ $VM_CPUS -le $host_cpus ]] || die 'Selected vCPU count exceeds the available host CPUs.'
existing=$(virsh_bms list --all --name)
! printf '%s\n' "$existing" | grep -Fxq "$VM_NAME" || die 'Domain already exists.'
require_new_path "$VM_STATE_DIR"
require_new_path "$VM_IMAGE_DIR"
virsh_bms net-list --name | grep -Fxq "$VM_NAME" || die 'The matching network must be active.'
virsh_bms net-dumpxml "$VM_NAME" | python3 -c '
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.stdin).getroot()
host = root.find("./ip/dhcp/host")
if host is None or host.get("mac") != sys.argv[1] or host.get("ip") != sys.argv[2]:
    raise SystemExit("Network DHCP reservation differs from the selected environment")
' "$VM_MAC" "$VM_IP"
virsh_bms capabilities | python3 -c '
import sys, xml.etree.ElementTree as ET
models = {node.findtext("model") for node in ET.parse(sys.stdin).findall("./host/secmodel")}
if not {"apparmor", "dac"}.issubset(models):
    raise SystemExit("Libvirt requires AppArmor and DAC security models")
'
getent passwd libvirt-qemu >/dev/null
getent group libvirt-qemu >/dev/null
[[ $(id -u libvirt-qemu) -ne 0 ]] || die 'libvirt-qemu must be non-root.'
[[ -d $BMS_VM_IMAGE_DIR && ! -L $BMS_VM_IMAGE_DIR && $(stat -c %u "$BMS_VM_IMAGE_DIR") == 0 ]] || die 'Unexpected libvirt image directory.'
[[ $(findmnt -n -o FSTYPE --target "$BMS_VM_IMAGE_DIR") == ext4 ]] || die 'Guest images require ext4 storage for the tested allocation workflow.'
free_bytes=$(df -B1 --output=avail "$BMS_VM_IMAGE_DIR" | tail -n 1)
[[ $free_bytes -ge $(((VM_DISK_GIB + 20) * 1073741824)) ]] || die 'Insufficient disk space including 20 GiB reserve.'
if [[ -e $BMS_CACHE_DIR || -L $BMS_CACHE_DIR ]]; then
    [[ -d $BMS_CACHE_DIR && ! -L $BMS_CACHE_DIR && $(stat -c %u:%a "$BMS_CACHE_DIR") == 0:700 ]] || die 'Unexpected image cache directory.'
else
    mkdir -m 0700 "$BMS_CACHE_DIR"
fi
cache=$BMS_CACHE_DIR/noble-amd64
image=ubuntu-24.04-server-cloudimg-amd64.img
source_url=https://cloud-images.ubuntu.com/releases/noble/release
if [[ ! -e $cache && ! -L $cache ]]; then
    mkdir -m 0700 "$cache"
    curl --fail --show-error --location --proto '=https' --proto-redir '=https' --connect-timeout 15 --max-time 60 --retry 3 --retry-max-time 180 --output "$cache/SHA256SUMS" "$source_url/SHA256SUMS"
    python3 - "$cache/SHA256SUMS" "$image" > "$cache/image.sha256" <<'PY'
import re, sys
lines = []
for line in open(sys.argv[1], encoding="ascii"):
    match = re.fullmatch(r"([0-9a-f]{64}) [ *](.+)\n?", line)
    if match and match[2] == sys.argv[2]:
        lines.append(match[1] + "  " + match[2])
if len(lines) != 1:
    raise SystemExit("Canonical manifest must contain exactly one matching image")
print(lines[0])
PY
    curl --fail --show-error --location --proto '=https' --proto-redir '=https' --connect-timeout 15 --max-time 900 --retry 3 --retry-max-time 1800 --output "$cache/$image" "$source_url/$image"
    (cd "$cache" && sha256sum --check --strict image.sha256)
    printf '%s/%s\n' "$source_url" "$image" > "$cache/source-url"
    touch "$cache/complete"
fi
[[ -d $cache && ! -L $cache && $(stat -c %u:%a "$cache") == 0:700 && -f $cache/complete ]] || die 'Incomplete or unexpected image cache; inspect without overwriting.'
for file in "$image" image.sha256 source-url; do
    [[ -f $cache/$file && ! -L $cache/$file && $(stat -c %u "$cache/$file") == 0 ]] || die 'Unexpected cached image file.'
done
[[ $(cat "$cache/source-url") == "$source_url/$image" ]] || die 'Unexpected cached source URL.'
(cd "$cache" && sha256sum --check --strict image.sha256)
source_bytes=$(qemu-img info --output=json "$cache/$image" | python3 -c '
import json, sys
image = json.load(sys.stdin)
if image.get("format") != "qcow2" or image.get("backing-filename"):
    raise SystemExit("Canonical cache must contain a standalone qcow2 image")
if not isinstance(image.get("virtual-size"), int) or image["virtual-size"] <= 0:
    raise SystemExit("Canonical image has an invalid virtual size")
print(image["virtual-size"])
')
[[ $((VM_DISK_GIB * 1073741824)) -ge $source_bytes ]] || die 'Selected disk size would shrink the verified Canonical source image.'
if [[ -e $BMS_STATE_DIR/vms || -L $BMS_STATE_DIR/vms ]]; then
    [[ -d $BMS_STATE_DIR/vms && ! -L $BMS_STATE_DIR/vms && $(stat -c %u:%a "$BMS_STATE_DIR/vms") == 0:700 ]] || die 'Unexpected VM state parent directory.'
else
    mkdir -m 0700 "$BMS_STATE_DIR/vms"
fi
mkdir -m 0700 "$VM_STATE_DIR"
mkdir -m 0711 "$VM_IMAGE_DIR"
trap 'printf "Failed at line %s (exit %s). VM preparation stopped. Preserve %s and %s; do not recreate or start partial artifacts.\n" "$LINENO" "$?" "$VM_STATE_DIR" "$VM_IMAGE_DIR" >&2' ERR
ssh-keygen -q -t ed25519 -N '' -C "$VM_NAME-operator" -f "$VM_STATE_DIR/id_ed25519"
public_key=$(cat "$VM_STATE_DIR/id_ed25519.pub")
VM_UUID=$(cat /proc/sys/kernel/random/uuid)
instance=$(cat /proc/sys/kernel/random/uuid)
cat > "$VM_STATE_DIR/user-data" <<EOF
#cloud-config
hostname: $VM_NAME
manage_etc_hosts: true
disable_root: true
ssh_pwauth: false
ssh_deletekeys: true
users:
  - name: purrmatch
    shell: /bin/bash
    sudo: ["ALL=(ALL) NOPASSWD:ALL"]
    lock_passwd: true
    ssh_authorized_keys:
      - $public_key
EOF
printf 'instance-id: %s-%s\nlocal-hostname: %s\n' "$VM_NAME" "$instance" "$VM_NAME" > "$VM_STATE_DIR/meta-data"
cat > "$VM_STATE_DIR/network-config" <<EOF
version: 2
ethernets:
  purrmatch:
    match:
      macaddress: "$VM_MAC"
    dhcp4: true
    dhcp6: false
EOF
cloud-localds --network-config="$VM_STATE_DIR/network-config" "$VM_IMAGE_DIR/seed.img" "$VM_STATE_DIR/user-data" "$VM_STATE_DIR/meta-data"
qemu-img convert -f qcow2 -O qcow2 -t writeback -S 0 -o preallocation=falloc "$cache/$image" "$VM_IMAGE_DIR/disk.qcow2"
qemu-img resize -f qcow2 --preallocation=falloc "$VM_IMAGE_DIR/disk.qcow2" "${VM_DISK_GIB}G"
qemu-img check -f qcow2 "$VM_IMAGE_DIR/disk.qcow2"
qemu-img info --output=json "$VM_IMAGE_DIR/disk.qcow2" > "$VM_STATE_DIR/disk-info.json"
python3 - "$VM_STATE_DIR/disk-info.json" "$VM_DISK_GIB" <<'PY'
import json, sys
with open(sys.argv[1]) as source:
    image = json.load(source)
if image.get("format") != "qcow2" or image.get("virtual-size") != int(sys.argv[2]) * 1073741824 or image.get("backing-filename"):
    raise SystemExit("Unexpected prepared disk format, size or backing file")
PY
[[ $(($(stat -c %b "$VM_IMAGE_DIR/disk.qcow2") * 512)) -ge $((VM_DISK_GIB * 1073741824)) ]] || die 'Guest disk is not fully allocated.'
chown libvirt-qemu:libvirt-qemu "$VM_IMAGE_DIR/disk.qcow2" "$VM_IMAGE_DIR/seed.img"
chmod 0600 "$VM_IMAGE_DIR/disk.qcow2" "$VM_IMAGE_DIR/seed.img"
cp "$cache/image.sha256" "$VM_STATE_DIR/source-image.sha256"
cp "$cache/source-url" "$VM_STATE_DIR/source-url"
render_domain > "$VM_STATE_DIR/domain.xml"
existing=$(virsh_bms list --all --name)
! printf '%s\n' "$existing" | grep -Fxq "$VM_NAME" || die 'A domain appeared during preparation; preserve artifacts and inspect.'
virsh_bms define "$VM_STATE_DIR/domain.xml" --validate
[[ $(virsh_bms domuuid "$VM_NAME") == "$VM_UUID" && $(virsh_bms domstate "$VM_NAME") == 'shut off' ]] || die 'Unexpected defined domain identity/state.'
printf '%s\n' "$VM_IP" > "$VM_STATE_DIR/ip"
printf '%s\n' "$VM_UUID" > "$VM_STATE_DIR/uuid"
virsh_bms dominfo "$VM_NAME"
printf 'Defined without starting or enabling autostart. State: %s\n' "$VM_STATE_DIR"
