#!/usr/bin/env bash
set -Eeuo pipefail
trap 'printf "Error: host check stopped at line %s.\n" "$LINENO" >&2' ERR
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source "$SCRIPT_DIR/common.sh"

if [[ ${1:-} == --help ]]; then
    printf 'Usage: bash infra/bms/check-host.sh\nRead-only Ubuntu 24.04 x86_64 BMS checks; run as root.\n'
    exit 0
fi
[[ $# -eq 0 ]] || die 'This command accepts only --help or no arguments.'
require_host
for command_name in lscpu free lsblk findmnt df ip python3 stat systemctl; do
    require_command "$command_name"
done
printf 'Operating system: '
cat /etc/os-release
uname -r
lscpu
free -h
lsblk -e 7 -o NAME,TYPE,SIZE,FSTYPE,MOUNTPOINTS,ROTA
findmnt /
df -h /
printf '\nSwap:\n'
cat /proc/swaps
printf '\nSoftware RAID:\n'
cat /proc/mdstat
printf '\nRoutes and existing bridges:\n'
ip -4 route show
ip -6 route show
ip -o link show type bridge
printf '\nKVM API:\n'
[[ -c /dev/kvm ]] || die '/dev/kvm is missing; do not fall back to software emulation.'
python3 - <<'PY'
import fcntl
import os

fd = os.open('/dev/kvm', os.O_RDWR | os.O_CLOEXEC)
try:
    version = fcntl.ioctl(fd, 0xAE00, 0)
    print(f'KVM API version: {version}')
    if version != 12:
        raise SystemExit('KVM API 12 is required')
finally:
    os.close(fd)
PY
printf '\nCgroup filesystem: '
stat -fc %T /sys/fs/cgroup
[[ $(stat -fc %T /sys/fs/cgroup) == cgroup2fs ]] || die 'Cgroup v2 is required.'
printf '\nAppArmor enabled: '
[[ -r /sys/module/apparmor/parameters/enabled ]] || die 'Cannot read AppArmor state.'
cat /sys/module/apparmor/parameters/enabled
[[ $(cat /sys/module/apparmor/parameters/enabled) == Y ]] || die 'AppArmor must be enabled.'
systemctl is-active apparmor.service
printf '\nKSM run/sleep_millisecs:\n'
cat /sys/kernel/mm/ksm/run /sys/kernel/mm/ksm/sleep_millisecs
printf '\nRead-only checks completed. Guest boot, isolation and capacity remain separate checks.\n'
