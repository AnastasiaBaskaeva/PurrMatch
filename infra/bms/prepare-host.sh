#!/usr/bin/env bash
set -Eeuo pipefail
trap 'printf "Error: host preparation stopped at line %s. Preserve state and gates; inspect before retrying.\n" "$LINENO" >&2' ERR
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source "$SCRIPT_DIR/common.sh"

if [[ ${1:-} == --help ]]; then
    printf 'Usage: bash infra/bms/prepare-host.sh plan|install|start\nFresh replacement BMS only. Review plan before install. Never retry a partial install blindly.\n'
    exit 0
fi
[[ $# -eq 1 ]] || die 'Choose plan, install or start.'
case "$1" in plan|install|start) ;; *) die 'Choose plan, install or start.' ;; esac
require_host
export LC_ALL=C
umask 077
for command_name in apt-get apt-cache dpkg dpkg-query dpkg-deb sha256sum systemctl ip ss flock; do
    require_command "$command_name"
done
ensure_state_dir
lock_operations
HOST_STATE=$BMS_STATE_DIR/host
HOST_PACKAGES=(qemu-system-x86 qemu-utils cloud-image-utils libvirt-daemon-system libvirt-clients systemd-container dnsmasq-base curl ca-certificates)
GATED_UNITS=(libvirtd.service libvirt-guests.service)

fresh_host() {
    local path unit status existing_packages audit
    for path in /etc/default/qemu-kvm /etc/libvirt /var/lib/libvirt /etc/default/libvirtd /etc/default/libvirt-guests /etc/default/virtlockd /etc/default/virtlogd; do
        require_new_path "$path"
    done
    if getent passwd libvirt-qemu >/dev/null || getent group libvirt-qemu >/dev/null || getent passwd 64055 >/dev/null || getent group 64055 >/dev/null; then
        die 'Existing or conflicting libvirt-qemu identity; inspect separately.'
    fi
    status=$(dpkg-query -W -f='${binary:Package}\t${db:Status-Status}\n' 'qemu-*' 'libvirt*' 2>/dev/null || true)
    existing_packages=$(printf '%s\n' "$status" | awk 'NF && $2 != "not-installed"')
    [[ -z $existing_packages ]] || die 'Existing QEMU/libvirt package state; this installer cannot adopt it.'
    for unit in qemu-kvm.service libvirtd.service libvirt-guests.service libvirtd.socket libvirtd-ro.socket libvirtd-admin.socket libvirtd-tcp.socket libvirtd-tls.socket virtlockd.service virtlogd.service; do
        [[ $(systemctl show "$unit" -p LoadState --value) == not-found ]] || die "Existing unit: $unit"
        require_new_path "/etc/systemd/system/$unit"
        require_new_path "/etc/systemd/system/$unit.d"
    done
    audit=$(dpkg --audit)
    [[ -z $audit ]] || die 'Resolve pending dpkg work before planning.'
    [[ $(cat /sys/kernel/mm/ksm/run) =~ ^[01]$ ]] || die 'KSM run must be 0 or 1.'
    [[ $(cat /sys/module/apparmor/parameters/enabled) == Y ]] || die 'AppArmor must be enabled.'
    systemctl is-active --quiet apparmor.service
}

transaction() {
    awk '$1 == "Remv" {exit 1} $1 == "Inst" {if ($3 !~ /^\(/) exit 1; version=$3; sub(/^\(/,"",version); print $2 "=" version}' "$1" | sort
}

unchanged_host() {
    cmp "$HOST_STATE/ksm-run" /sys/kernel/mm/ksm/run
    cmp "$HOST_STATE/ksm-sleep" /sys/kernel/mm/ksm/sleep_millisecs
    ip -o link show type bridge > "$HOST_STATE/bridges-current"
    cmp "$HOST_STATE/bridges-before" "$HOST_STATE/bridges-current"
}

management_closed() {
    local unit listeners
    for unit in libvirtd-tcp.socket libvirtd-tls.socket; do
        [[ $(systemctl show "$unit" -p ActiveState --value) == inactive ]] || die "Unexpected active management socket: $unit"
        [[ $(systemctl is-enabled "$unit" 2>/dev/null || true) == disabled ]] || die "Unexpected enabled management socket: $unit"
    done
    listeners=$(ss -H -lnt '( sport = :16509 or sport = :16514 )')
    [[ -z $listeners ]] || die 'Management TCP listener exists.'
}

check_gate() {
    local unit
    [[ -f $HOST_STATE/pending && ! -L $HOST_STATE/pending ]] || die 'Owned pending gate is missing.'
    for unit in "${GATED_UNITS[@]}"; do
        cmp "$HOST_STATE/first-start.conf" "/etc/systemd/system/$unit.d/90-purrmatch-first-install.conf"
        [[ $(systemctl show "$unit" -p LoadState --value) == loaded ]] || die "Unit not loaded: $unit"
        [[ $(systemctl show "$unit" -p ActiveState --value) == inactive ]] || die "Unit started before gate release: $unit"
        [[ $(systemctl show "$unit" -p MainPID --value) == 0 ]] || die "Unexpected PID: $unit"
    done
    if pgrep -x libvirtd >/dev/null; then die 'Unexpected libvirtd process.'; fi
}

case "$1" in
    plan)
        fresh_host
        require_new_path "$HOST_STATE"
        mkdir -m 0700 "$HOST_STATE" "$HOST_STATE/packages" "$HOST_STATE/review"
        bash "$SCRIPT_DIR/check-host.sh" > "$HOST_STATE/preflight.txt"
        cat /sys/kernel/mm/ksm/run > "$HOST_STATE/ksm-run"
        cat /sys/kernel/mm/ksm/sleep_millisecs > "$HOST_STATE/ksm-sleep"
        ip -o link show type bridge > "$HOST_STATE/bridges-before"
        dpkg-query -W -f='${binary:Package}\t${Version}\t${db:Status-Status}\n' > "$HOST_STATE/packages-before.tsv"
        sha256sum /var/lib/dpkg/status > "$HOST_STATE/dpkg-status.sha256"
        apt-get update </dev/null
        apt-cache policy "${HOST_PACKAGES[@]}" > "$HOST_STATE/apt-policy.txt"
        apt-get --simulate --no-install-recommends --no-remove install "${HOST_PACKAGES[@]}" </dev/null > "$HOST_STATE/simulation.txt"
        transaction "$HOST_STATE/simulation.txt" > "$HOST_STATE/versions.txt" || die 'APT proposed removals/upgrades. Resolve separately and make a new reviewed plan.'
        [[ -s $HOST_STATE/versions.txt ]] || die 'APT transaction is empty.'
        mapfile -t versions < "$HOST_STATE/versions.txt"
        (cd "$HOST_STATE/packages" && apt-get download "${versions[@]}" </dev/null)
        (cd "$HOST_STATE/packages" && sha256sum ./*.deb) > "$HOST_STATE/packages.sha256"
        for deb in "$HOST_STATE/packages/"*.deb; do
            package=$(dpkg-deb -f "$deb" Package)
            mkdir "$HOST_STATE/review/$package"
            dpkg-deb -e "$deb" "$HOST_STATE/review/$package/control"
            dpkg-deb -x "$deb" "$HOST_STATE/review/$package/payload"
        done
        grep -Fxq /etc/default/qemu-kvm "$HOST_STATE/review/qemu-system-common/control/conffiles"
        find "$HOST_STATE/review" -path '*/payload/*/systemd/system/libvirtd.socket' -type f > "$HOST_STATE/socket-paths.txt"
        mapfile -t socket_files < "$HOST_STATE/socket-paths.txt"
        [[ ${#socket_files[@]} -eq 1 ]] || die 'Cannot identify one libvirtd socket payload.'
        units=$(dirname "${socket_files[0]}")
        for socket in libvirtd.socket libvirtd-ro.socket libvirtd-admin.socket; do
            grep -Fxq Service=libvirtd.service "$units/$socket"
        done
        touch "$HOST_STATE/planned"
        printf 'Review %s/{simulation.txt,versions.txt,apt-policy.txt,review/}.\n' "$HOST_STATE"
        printf 'Review all maintainer scripts, QEMU KSM init/conffile, libvirt units and default-network creation before install.\nNext: bash infra/bms/prepare-host.sh install\n'
        ;;
    install)
        [[ -f $HOST_STATE/planned ]] || die 'Complete and review plan first.'
        require_new_path "$HOST_STATE/install-attempted"
        fresh_host
        unchanged_host
        sha256sum --check "$HOST_STATE/dpkg-status.sha256"
        (cd "$HOST_STATE/packages" && sha256sum --check "$HOST_STATE/packages.sha256")
        debs=("$HOST_STATE/packages/"*.deb)
        apt-get --simulate --no-install-recommends --no-remove install "${debs[@]}" </dev/null > "$HOST_STATE/install-simulation.txt"
        transaction "$HOST_STATE/install-simulation.txt" > "$HOST_STATE/install-versions.txt"
        cmp "$HOST_STATE/versions.txt" "$HOST_STATE/install-versions.txt"
        touch "$HOST_STATE/install-attempted" "$HOST_STATE/pending"
        printf '[Unit]\nConditionPathExists=!%s/pending\n' "$HOST_STATE" > "$HOST_STATE/first-start.conf"
        for unit in "${GATED_UNITS[@]}"; do
            mkdir -m 0755 "/etc/systemd/system/$unit.d"
            install -o root -g root -m 0644 "$HOST_STATE/first-start.conf" "/etc/systemd/system/$unit.d/90-purrmatch-first-install.conf"
        done
        printf 'KSM_ENABLED=%s\nSLEEP_MILLISECS=\n' "$(cat "$HOST_STATE/ksm-run")" > "$HOST_STATE/qemu-kvm"
        install -o root -g root -m 0644 "$HOST_STATE/qemu-kvm" /etc/default/qemu-kvm
        systemctl daemon-reload
        DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-download --no-install-recommends --no-remove -o Dpkg::Options::=--force-confold "${debs[@]}" </dev/null
        cmp "$HOST_STATE/qemu-kvm" /etc/default/qemu-kvm
        systemctl is-active --quiet qemu-kvm.service
        [[ $(systemctl show qemu-kvm.service -p Result --value) == success ]]
        [[ $(systemctl show qemu-kvm.service -p ExecMainStatus --value) == 0 ]]
        check_gate
        management_closed
        unchanged_host
        touch "$HOST_STATE/installed"
        printf 'Installed; first-start gate remains. Next: bash infra/bms/prepare-host.sh start\n'
        ;;
    start)
        [[ -f $HOST_STATE/installed ]] || die 'Installation did not finish; inspect manually without removing gates.'
        require_new_path "$HOST_STATE/start-attempted"
        check_gate
        management_closed
        unchanged_host
        require_new_path "$HOST_STATE/default-autostart.xml"
        default=/etc/libvirt/qemu/networks/autostart/default.xml
        [[ -L $default && $(readlink "$default") == /etc/libvirt/qemu/networks/default.xml ]] || die 'Unexpected default network autostart entry.'
        [[ -f /etc/libvirt/qemu/networks/default.xml ]] || die 'Default network definition is missing.'
        guest_definition=$(find /etc/libvirt/qemu -maxdepth 1 -name '*.xml' -print -quit)
        [[ -z $guest_definition ]] || die 'Unexpected guest definition.'
        touch "$HOST_STATE/start-attempted"
        mv "$default" "$HOST_STATE/default-autostart.xml"
        network_autostart=$(find /etc/libvirt/qemu/networks/autostart -mindepth 1 -maxdepth 1 -print -quit)
        [[ -z $network_autostart ]] || die 'Unexpected network autostart entry; gate retained.'
        mv "$HOST_STATE/pending" "$HOST_STATE/starting"
        systemctl start libvirtd.service libvirt-guests.service
        systemctl is-active --quiet libvirtd.service
        guests=$(virsh_bms list --all --name)
        [[ -z $guests ]] || die 'Unexpected guest exists.'
        active_networks=$(virsh_bms net-list --name)
        [[ -z $active_networks ]] || die 'Unexpected active network.'
        autostart_networks=$(virsh_bms net-list --all --name --autostart)
        [[ -z $autostart_networks ]] || die 'Unexpected autostart network.'
        virsh_bms net-info default
        virsh_bms net-list --all --persistent --name | grep -Fxq default
        management_closed
        unchanged_host
        for unit in "${GATED_UNITS[@]}"; do
            cmp "$HOST_STATE/first-start.conf" "/etc/systemd/system/$unit.d/90-purrmatch-first-install.conf"
            mv "/etc/systemd/system/$unit.d/90-purrmatch-first-install.conf" "$HOST_STATE/$unit.first-start.conf"
        done
        systemctl daemon-reload
        mv "$HOST_STATE/starting" "$HOST_STATE/completed"
        printf 'Host prepared. No guests or active networks; default network remains persistent without autostart.\n'
        ;;
esac
