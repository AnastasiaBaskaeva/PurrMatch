#!/usr/bin/env bash
set -Eeuo pipefail
trap 'printf "Failed at line %s (exit %s).\n" "$LINENO" "$?" >&2' ERR
umask 077
export LC_ALL=C
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

usage() { printf 'Usage: %s UPLINK | --render test|prod UPLINK | --help\n' "$0"; }
validate_uplink() { [[ $1 =~ ^[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,14}$ ]] || die 'Invalid uplink interface name.'; }
render_network() {
    select_environment "$1"
    cat <<XML
<network>
  <name>$VM_NAME</name>
  <forward mode='nat' dev='$UPLINK'/>
  <bridge name='virbr-pm-$VM_ENV' stp='off' delay='0'/>
  <mtu size='1500'/>
  <ip address='$VM_GATEWAY' netmask='255.255.255.240'>
    <dhcp><host mac='$VM_MAC' name='$VM_NAME' ip='$VM_IP'/></dhcp>
  </ip>
</network>
XML
}
if [[ ${1:-} == --help ]]; then usage; exit 0; fi
if [[ ${1:-} == --render ]]; then
    [[ $# -eq 3 ]] || { usage >&2; exit 1; }
    UPLINK=$3
    validate_uplink "$UPLINK"
    render_network "$2"
    exit 0
fi
[[ $# -eq 1 ]] || { usage >&2; exit 1; }
UPLINK=$1
validate_uplink "$UPLINK"
require_host
for command in virsh ip python3 dnsmasq; do require_command "$command"; done
lock_operations
[[ -c /dev/kvm && -d /sys/class/net/$UPLINK ]] || die 'KVM or the requested uplink is missing.'
select_environment test
domains=$(virsh_bms list --all --name)
[[ -z $domains ]] || die 'Existing domains: inspect instead of bootstrapping networks.'
active=$(virsh_bms net-list --name)
[[ -z $active ]] || die 'An active virtual network already exists.'
autostart=$(virsh_bms net-list --all --name --autostart)
[[ -z $autostart ]] || die 'An autostart virtual network already exists.'
existing=$(virsh_bms net-list --all --name)
for environment in test prod; do
    ! printf '%s\n' "$existing" | grep -Fxq "purrmatch-$environment" || die 'A PurrMatch network already exists.'
    require_new_path "/sys/class/net/virbr-pm-$environment"
done
ip -j -4 address show | python3 -c '
import ipaddress, json, sys
project = ipaddress.ip_network(sys.argv[1])
for interface in json.load(sys.stdin):
    for address in interface.get("addr_info", []):
        if address.get("family") == "inet":
            subnet = ipaddress.ip_network("{local}/{prefixlen}".format(**address), strict=False)
            if subnet.overlaps(project):
                raise SystemExit("Project prefix overlaps an interface: " + interface["ifname"])
' "$BMS_NETWORK_CIDR"
ip -j -4 route show table all | python3 -c '
import ipaddress, json, sys
project = ipaddress.ip_network(sys.argv[1])
aggregates = {"10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"}
for route in json.load(sys.stdin):
    destination = route.get("dst", "default")
    if destination == "default" or destination in aggregates:
        continue
    if ipaddress.ip_network(destination, strict=False).overlaps(project):
        raise SystemExit("Project prefix overlaps a specific route: " + destination)
' "$BMS_NETWORK_CIDR"
state=$BMS_STATE_DIR/networks
require_new_path "$state"
mkdir -m 0700 "$state"
trap 'printf "Failed at line %s (exit %s). Network bootstrap stopped. Preserve %s and inspect partial definitions; no automatic rollback.\n" "$LINENO" "$?" "$state" >&2' ERR
printf '%s\n' "$UPLINK" > "$state/uplink"
printf '%s\n' "$BMS_NETWORK_CIDR" > "$state/project-cidr"
cat /proc/sys/net/ipv4/ip_forward > "$state/ip-forward-before"
for key in /proc/sys/net/ipv4/conf/*/forwarding /proc/sys/net/ipv4/conf/*/accept_redirects; do
    value=$(cat "$key")
    printf '%s %s\n' "$key" "$value"
done > "$state/ipv4-before"
ip -4 route show | sort > "$state/routes-before"
ip -4 route show default > "$state/default-route-before"
touch "$state/baseline-ready"
for environment in test prod; do
    render_network "$environment" > "$state/$environment.xml"
    virsh_bms net-define "$state/$environment.xml" --validate
done
for environment in test prod; do virsh_bms net-start "purrmatch-$environment"; done
ip -4 route show default > "$state/default-route-after"
cmp "$state/default-route-before" "$state/default-route-after"
ip -4 route show | awk '$0 !~ / dev virbr-pm-(test|prod)( |$)/' | sort > "$state/routes-after-existing"
cmp "$state/routes-before" "$state/routes-after-existing"
[[ $(cat /proc/sys/net/ipv4/ip_forward) == 1 ]] || die 'IPv4 forwarding is not enabled.'
for key in /proc/sys/net/ipv4/conf/*/forwarding /proc/sys/net/ipv4/conf/*/accept_redirects; do
    value=$(cat "$key")
    printf '%s %s\n' "$key" "$value"
done > "$state/ipv4-after"
for environment in test prod; do virsh_bms net-autostart "purrmatch-$environment"; done
virsh_bms net-list --all
printf 'Networks created; original routes preserved. Baseline: %s\n' "$state"
