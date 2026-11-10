#!/usr/bin/env bash
set -Eeuo pipefail
trap 'printf "Error: network rollback stopped at line %s. Preserve the saved state.\n" "$LINENO" >&2' ERR
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source "$SCRIPT_DIR/common.sh"

if [[ ${1:-} == --help ]]; then
    printf 'Usage: bash infra/bms/rollback-networks.sh\nStop owned empty networks and restore saved IPv4 settings. Definitions and state remain.\n'
    exit 0
fi
[[ $# -eq 0 ]] || die 'This command accepts only --help or no arguments.'
require_host
for command_name in virsh ip python3; do require_command "$command_name"; done
lock_operations
require_private_directory "$BMS_STATE_DIR"
state=$BMS_STATE_DIR/networks
require_private_directory "$state"
[[ -f $state/baseline-ready && ! -L $state/baseline-ready ]] || die 'Network baseline is missing.'
domains=$(virsh_bms list --all --name)
[[ -z $domains ]] || die 'Rollback requires no defined guests. Preserve disks and inspect manually.'
existing=$(virsh_bms net-list --all --name)
active=$(virsh_bms net-list --name)
for name in $active; do
    [[ $name == purrmatch-test || $name == purrmatch-prod ]] || die 'Another active network prevents rollback.'
done
for environment in test prod; do
    name=purrmatch-$environment
    if printf '%s\n' "$existing" | grep -Fxq "$name"; then
        virsh_bms net-dumpxml "$name" | python3 -c '
import sys
import xml.etree.ElementTree as ET

actual = ET.parse(sys.stdin).getroot()
expected = ET.parse(sys.argv[1]).getroot()
for path in ("name", "bridge", "forward", "ip"):
    left, right = actual.find(path), expected.find(path)
    if left is None or right is None:
        raise SystemExit("Unexpected network definition")
    for key, value in right.attrib.items():
        if left.get(key) != value:
            raise SystemExit("Network differs from the saved definition")
    if path == "name" and left.text != right.text:
        raise SystemExit("Network name differs")
' "$state/$environment.xml"
        virsh_bms net-autostart "$name" --disable
    fi
    if printf '%s\n' "$active" | grep -Fxq "$name"; then
        virsh_bms net-destroy "$name"
    fi
done
ip -4 route show | LC_ALL=C sort > "$state/routes-stopped"
cmp "$state/routes-before" "$state/routes-stopped"
for environment in test prod; do
    [[ ! -e /sys/class/net/virbr-pm-$environment ]] || die 'An owned bridge still exists.'
done
for key in /proc/sys/net/ipv4/conf/*/forwarding /proc/sys/net/ipv4/conf/*/accept_redirects; do
    printf '%s\n' "$key"
done | LC_ALL=C sort > "$state/ipv4-keys-now"
cut -d ' ' -f 1 "$state/ipv4-before" | LC_ALL=C sort > "$state/ipv4-keys-before"
cmp "$state/ipv4-keys-before" "$state/ipv4-keys-now"
forward_before=$(cat "$state/ip-forward-before")
[[ $forward_before == 0 || $forward_before == 1 ]] || die 'Invalid saved forwarding value.'
printf '%s\n' "$forward_before" > /proc/sys/net/ipv4/ip_forward
while read -r key value; do
    case "$key" in
        /proc/sys/net/ipv4/conf/*/forwarding|/proc/sys/net/ipv4/conf/*/accept_redirects) ;;
        *) die 'Unexpected saved IPv4 setting.' ;;
    esac
    [[ $value =~ ^[0-9]+$ ]] || die 'Invalid saved IPv4 value.'
    current=$(cat "$key")
    if [[ $current != "$value" ]]; then printf '%s\n' "$value" > "$key"; fi
done < "$state/ipv4-before"
virsh_bms net-list --all
printf 'Owned networks stopped. Definitions/state preserved; IPv4 forwarding restored to %s.\n' "$forward_before"
