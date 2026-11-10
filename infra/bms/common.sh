#!/usr/bin/env bash

BMS_STATE_DIR=/root/purrmatch-bms
BMS_CACHE_DIR=/var/lib/purrmatch-bms
BMS_VM_IMAGE_DIR=/var/lib/libvirt/images
BMS_URI=qemu:///system
BMS_NETWORK_CIDR=${PURRMATCH_NETWORK_CIDR:-172.30.240.0/20}

die() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

require_root() {
    [[ $(id -u) -eq 0 ]] || die 'Run this command as root.'
}

require_host() {
    require_root
    [[ $(uname -m) == x86_64 ]] || die 'An x86_64 host is required.'
    [[ -r /etc/os-release ]] || die 'Cannot identify the operating system.'
    local ID VERSION_ID
    source /etc/os-release
    [[ $ID == ubuntu && $VERSION_ID == 24.04 ]] || die 'Only Ubuntu 24.04 is supported.'
}

virsh_bms() {
    virsh -c "$BMS_URI" "$@"
}

require_command() {
    command -v "$1" >/dev/null || die "Required command is missing: $1"
}

require_private_directory() {
    [[ -d $1 && ! -L $1 ]] || die "Unexpected private directory: $1"
    [[ $(stat -c %u:%a "$1") == 0:700 ]] || die "Private directory must belong to root with mode 0700: $1"
}

ensure_state_dir() {
    if [[ -e $BMS_STATE_DIR || -L $BMS_STATE_DIR ]]; then
        require_private_directory "$BMS_STATE_DIR"
        [[ -f $BMS_STATE_DIR/.purrmatch-bms && ! -L $BMS_STATE_DIR/.purrmatch-bms ]] || die 'State directory is not owned by this bootstrap.'
    else
        mkdir -m 0700 "$BMS_STATE_DIR"
        printf 'purrmatch-bms-v1\n' > "$BMS_STATE_DIR/.purrmatch-bms"
    fi
}

require_new_path() {
    [[ ! -e $1 && ! -L $1 ]] || die "Path already exists: $1"
}

lock_operations() {
    ensure_state_dir
    require_command flock
    [[ ! -L $BMS_STATE_DIR/.operation.lock ]] || die 'Unexpected operation lock.'
    exec 8> "$BMS_STATE_DIR/.operation.lock"
    flock -n 8 || die 'Another BMS operation is running. Wait for it to finish.'
}

require_positive_integer() {
    [[ $2 =~ ^[1-9][0-9]*$ && ${#2} -le 10 ]] || die "$1 must be a positive integer."
    [[ $2 -le 2147483647 ]] || die "$1 must not exceed 2147483647."
}

domain_resources() {
    python3 -c '
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.stdin).getroot()
memory = root.find("memory")
units = {"KiB": 1, "k": 1, "MiB": 1024, "M": 1024, "GiB": 1048576, "G": 1048576}
if memory is None or memory.get("unit", "KiB") not in units:
    raise SystemExit("Unexpected domain memory unit")
print(int(memory.text) * units[memory.get("unit", "KiB")], int(root.findtext("vcpu")))
'
}

select_environment() {
    VM_ENV=${1:-}
    case "$VM_ENV" in
        test)
            VM_CPUS=${PURRMATCH_TEST_VCPUS:-4}
            VM_RAM_MIB=${PURRMATCH_TEST_RAM_MIB:-8192}
            VM_DISK_GIB=${PURRMATCH_TEST_DISK_GIB:-100}
            VM_MAC=52:54:00:50:4d:01
            VM_OFFSET=0
            ;;
        prod)
            VM_CPUS=${PURRMATCH_PROD_VCPUS:-12}
            VM_RAM_MIB=${PURRMATCH_PROD_RAM_MIB:-32768}
            VM_DISK_GIB=${PURRMATCH_PROD_DISK_GIB:-300}
            VM_MAC=52:54:00:50:4d:02
            VM_OFFSET=16
            ;;
        *) die 'Environment must be test or prod.' ;;
    esac
    require_positive_integer vCPUs "$VM_CPUS"
    require_positive_integer RAM_MiB "$VM_RAM_MIB"
    require_positive_integer disk_GiB "$VM_DISK_GIB"
    VM_NAME=purrmatch-$VM_ENV
    VM_STATE_DIR=$BMS_STATE_DIR/vms/$VM_ENV
    VM_IMAGE_DIR=$BMS_VM_IMAGE_DIR/$VM_NAME
    local addresses
    addresses=$(python3 - "$BMS_NETWORK_CIDR" "$VM_OFFSET" <<'PY'
import ipaddress
import sys

try:
    network = ipaddress.ip_network(sys.argv[1])
    private = [ipaddress.ip_network(value) for value in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")]
    if network.version != 4 or network.prefixlen != 20 or not any(network.subnet_of(value) for value in private):
        raise ValueError("Choose an aligned RFC1918 IPv4 /20 network")
    subnet = ipaddress.ip_network((int(network.network_address) + int(sys.argv[2]), 28))
    print(subnet, subnet.network_address + 1, subnet.network_address + 2)
except ValueError as error:
    raise SystemExit(str(error))
PY
    )
    read -r VM_CIDR VM_GATEWAY VM_IP <<< "$addresses"
}

load_vm_identity() {
    require_private_directory "$BMS_STATE_DIR"
    require_private_directory "$BMS_STATE_DIR/vms"
    require_private_directory "$VM_STATE_DIR"
    local file
    for file in uuid ip; do
        [[ -f $VM_STATE_DIR/$file && ! -L $VM_STATE_DIR/$file ]] || die 'VM definition did not finish successfully.'
        [[ $(stat -c %u:%a "$VM_STATE_DIR/$file") == 0:600 ]] || die 'Unexpected saved VM identity permissions.'
    done
    VM_UUID=$(cat "$VM_STATE_DIR/uuid")
    VM_IP=$(cat "$VM_STATE_DIR/ip")
    [[ $VM_UUID =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]] || die 'Invalid saved VM UUID.'
    [[ $VM_IP =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || die 'Invalid saved VM address.'
    local actual_uuid
    actual_uuid=$(virsh_bms domuuid "$VM_NAME")
    [[ $actual_uuid == "$VM_UUID" ]] || die 'The VM UUID differs from the saved bootstrap state.'
}
