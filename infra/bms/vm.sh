#!/usr/bin/env bash
set -Eeuo pipefail
trap 'printf "Failed at line %s (exit %s).\n" "$LINENO" "$?" >&2' ERR
umask 077
export LC_ALL=C
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

usage() { printf 'Usage: %s test|prod start|check|stop | test|prod autostart enable|disable | --help\n' "$0"; }
if [[ ${1:-} == --help ]]; then usage; exit 0; fi
[[ $# -ge 2 ]] || { usage >&2; exit 1; }
case "$2" in
    start|check|stop) [[ $# -eq 2 ]] || { usage >&2; exit 1; } ;;
    autostart) [[ $# -eq 3 && ( $3 == enable || $3 == disable ) ]] || { usage >&2; exit 1; } ;;
    *) usage >&2; exit 1 ;;
esac
select_environment "$1"
action=$2
require_host
for command in virsh python3 timeout ssh; do require_command "$command"; done
lock_operations
[[ -c /dev/kvm ]] || die 'KVM is missing.'
load_vm_identity
configuration=$(virsh_bms dumpxml "$VM_NAME" --inactive | domain_resources)
read -r actual_ram_kib actual_cpus <<< "$configuration"
require_positive_integer configured_RAM_KiB "$actual_ram_kib"
require_positive_integer configured_vCPUs "$actual_cpus"

runtime_check() {
    [[ $(virsh_bms domstate "$VM_NAME") == running ]] || die 'The VM must be running.'
    local pid uid label scheduler throttle
    pid=$(cat "/run/libvirt/qemu/$VM_NAME.pid")
    [[ $pid =~ ^[1-9][0-9]*$ ]] || die 'Invalid QEMU PID.'
    [[ $(readlink "/proc/$pid/exe") == /usr/bin/qemu-system-x86_64 ]] || die 'Unexpected QEMU executable.'
    uid=$(awk '/^Uid:/ {print $3}' "/proc/$pid/status")
    [[ $uid =~ ^[1-9][0-9]*$ ]] || die 'QEMU must have a non-root effective UID.'
    label=$(cat "/proc/$pid/attr/current")
    [[ $label == "libvirt-$VM_UUID (enforce)" ]] || die 'The expected AppArmor profile is not enforcing.'
    scheduler=$(virsh_bms schedinfo "$VM_NAME" --live)
    [[ $(awk '$1 == "global_period" {print $3}' <<< "$scheduler") == 100000 ]] || die 'Unexpected live CPU period.'
    [[ $(awk '$1 == "global_quota" {print $3}' <<< "$scheduler") == "$((actual_cpus * 100000))" ]] || die 'Unexpected live CPU quota.'
    throttle=$(virsh_bms blkdeviotune "$VM_NAME" vda --live)
    [[ $(awk '$1 == "total_bytes_sec" {print $3}' <<< "$throttle") == 104857600 ]] || die 'Unexpected live disk byte limit.'
    [[ $(awk '$1 == "total_iops_sec" {print $3}' <<< "$throttle") == 1000 ]] || die 'Unexpected live disk IOPS limit.'
    printf 'UUID: %s; AppArmor: %s; effective UID: %s\n' "$VM_UUID" "$label" "$uid"
    printf '%s\n%s\n' "$scheduler" "$throttle"
    cat "/proc/$pid/cgroup"
}
ssh_guest() {
    timeout --kill-after=5s 300s ssh -T \
        -i "$VM_STATE_DIR/id_ed25519" -o BatchMode=yes -o IdentitiesOnly=yes \
        -o ForwardAgent=no -o "StrictHostKeyChecking=$host_key_mode" \
        -o "UserKnownHostsFile=$VM_STATE_DIR/known_hosts" -o GlobalKnownHostsFile=/dev/null \
        -o UpdateHostKeys=no -o ConnectTimeout=10 "purrmatch@$VM_IP" "$@"
}
prepare_ssh() {
    [[ -f $VM_STATE_DIR/id_ed25519 && ! -L $VM_STATE_DIR/id_ed25519 ]] || die 'VM operator key is missing.'
    host_key_mode=yes
    if [[ ! -e $VM_STATE_DIR/known_hosts && ! -L $VM_STATE_DIR/known_hosts ]]; then
        host_key_mode=accept-new
    else
        [[ -f $VM_STATE_DIR/known_hosts && ! -L $VM_STATE_DIR/known_hosts && -s $VM_STATE_DIR/known_hosts ]] || die 'Unexpected known_hosts file; inspect instead of replacing it.'
    fi
}

case "$action" in
    start)
        [[ $(virsh_bms domstate "$VM_NAME") == 'shut off' ]] || die 'Start requires a shut-off VM.'
        total_kib=$(awk '/^MemTotal:/ {print $2}' /proc/meminfo)
        require_positive_integer host_RAM_KiB "$total_kib"
        configured_kib=$actual_ram_kib
        active_guests=$(virsh_bms list --name)
        while IFS= read -r guest; do
            [[ -n $guest && $guest != "$VM_NAME" ]] || continue
            guest_configuration=$(virsh_bms dumpxml "$guest" --inactive | domain_resources)
            read -r guest_ram_kib guest_cpus <<< "$guest_configuration"
            require_positive_integer active_guest_RAM_KiB "$guest_ram_kib"
            require_positive_integer active_guest_vCPUs "$guest_cpus"
            configured_kib=$((configured_kib + guest_ram_kib))
        done <<< "$active_guests"
        [[ $((configured_kib + 16777216)) -le $total_kib ]] || die 'Aggregate configured active guest RAM plus 16 GiB headroom exceeds host RAM.'
        available_kib=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
        require_positive_integer available_RAM_KiB "$available_kib"
        [[ $available_kib -ge $((actual_ram_kib + 16777216)) ]] || die 'Insufficient available RAM for configured guest RAM plus 16 GiB headroom.'
        virsh_bms start "$VM_NAME"
        runtime_check
        printf 'Started. Wait for guest boot, then run: %s %s check\n' "$0" "$VM_ENV"
        ;;
    check)
        runtime_check
        prepare_ssh
        ssh_guest "bash -s -- $VM_NAME $VM_IP $actual_cpus" <<'GUEST'
set -euo pipefail
timeout 180s sudo -n cloud-init status --wait --long
[[ $(hostnamectl --static) == "$1" ]]
[[ $(systemd-detect-virt) == kvm ]]
[[ $(nproc) == "$3" ]]
ip -j -4 address show | python3 -c '
import json, sys
addresses = [address.get("local") for interface in json.load(sys.stdin) for address in interface.get("addr_info", [])]
if sys.argv[1] not in addresses:
    raise SystemExit("Expected guest IPv4 address is missing")
' "$2"
[[ $(timedatectl show -p NTP --value) == yes ]]
[[ $(timedatectl show -p NTPSynchronized --value) == yes ]]
hostnamectl --static
nproc
free -h
df -hT /
ip -brief -4 address
ip -4 route
resolvectl dns
getent ahostsv4 registry.yandexcloud.net
date -u
timedatectl show -p NTP -p NTPSynchronized -p TimeUSec
python3 - <<'PY'
import urllib.request
with urllib.request.urlopen("https://cloud-images.ubuntu.com/releases/noble/release/SHA256SUMS", timeout=20) as response:
    if response.status != 200:
        raise SystemExit("Public HTTPS failed")
    print("Public HTTPS:", response.status)
PY
GUEST
        host_key_mode=yes
        boot_id=$(ssh_guest 'cat /proc/sys/kernel/random/boot_id' </dev/null)
        [[ $boot_id =~ ^[0-9a-f-]{36}$ ]] || die 'Invalid guest boot ID.'
        if [[ -f $VM_STATE_DIR/checked-boot-id ]]; then
            printf 'Previously checked boot ID: %s\n' "$(cat "$VM_STATE_DIR/checked-boot-id")"
        fi
        printf '%s\n' "$boot_id" > "$VM_STATE_DIR/checked-boot-id"
        printf 'Guest checks passed. Current boot ID: %s\n' "$boot_id"
        ;;
    stop)
        [[ $(virsh_bms domstate "$VM_NAME") == running ]] || die 'Stop requires a running VM.'
        virsh_bms shutdown "$VM_NAME" --mode acpi
        for ((attempt = 0; attempt < 30; attempt++)); do
            if [[ $(virsh_bms domstate "$VM_NAME") == 'shut off' ]]; then
                printf '%s: shut off\n' "$VM_NAME"
                exit 0
            fi
            sleep 2
        done
        die 'Shutdown not confirmed after 60 seconds; inspect without force-stopping or recreating the VM.'
        ;;
    autostart)
        if [[ $3 == disable ]]; then
            virsh_bms autostart "$VM_NAME" --disable
        else
            [[ -f $VM_STATE_DIR/checked-boot-id && ! -L $VM_STATE_DIR/checked-boot-id ]] || die 'Run a successful guest check before enabling autostart.'
            runtime_check
            prepare_ssh
            [[ $host_key_mode == yes ]] || die 'A verified known_hosts file is required.'
            boot_id=$(ssh_guest 'cat /proc/sys/kernel/random/boot_id' </dev/null)
            [[ $boot_id == "$(cat "$VM_STATE_DIR/checked-boot-id")" ]] || die 'Run guest checks for the current boot before enabling autostart.'
            virsh_bms autostart "$VM_NAME"
        fi
        virsh_bms dominfo "$VM_NAME"
        ;;
esac
