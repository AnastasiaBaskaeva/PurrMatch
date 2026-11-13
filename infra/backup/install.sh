#!/usr/bin/env bash
set +x
set -Eeuo pipefail
umask 077
export LC_ALL=C
export PATH=/usr/sbin:/usr/bin:/sbin:/bin

fail() {
    printf 'STOP: %s\n' "$1" >&2
    exit 1
}

[[ $(id -u) == 0 ]] || fail 'Run as root on the intended guest.'
[[ $# == 1 ]] || fail 'Usage: bash install.sh test|prod'
environment=$1
case "$environment" in
    test|prod) ;;
    *) fail 'Environment must be test or prod.' ;;
esac
[[ $(hostname) == "purrmatch-$environment" ]] || fail 'Guest hostname does not match environment.'

for command in timeout systemctl install stat dirname awk age; do
    command -v "$command" >/dev/null || fail 'Required system tool is missing; install dependencies separately.'
done
[[ -x /usr/bin/python3 ]] || fail 'System Python is missing.'
/usr/bin/python3 -I -c 'import sys; assert sys.version_info[:2] == (3, 12); import jwt, cryptography, boto3' \
    >/dev/null 2>&1 || fail 'Python 3.12 with jwt, cryptography and boto3 is required.'

source_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
for name in backup.py capture.py cloud.py purrmatch-backup.service purrmatch-backup.timer config.example.json; do
    [[ -f $source_dir/$name && ! -L $source_dir/$name ]] || fail 'Installer source files are incomplete or symlinked.'
done

systemctl_bounded() {
    timeout --kill-after=3s 20s /usr/bin/systemctl --no-pager "$@" </dev/null
}

loaded_units=$(systemctl_bounded list-units --all --plain --no-legend)
unit_files=$(systemctl_bounded list-unit-files --no-legend)
for unit in purrmatch-backup.service purrmatch-backup.timer; do
    if printf '%s\n%s\n' "$loaded_units" "$unit_files" | awk -v wanted="$unit" '$1 == wanted { found=1 } END { exit !found }'; then
        state=$(systemctl_bounded show "$unit" -p ActiveState --value)
        case "$state" in
            inactive|failed) ;;
            *) fail 'Backup service and timer must be inactive before installation.' ;;
        esac
    fi
done

check_directory() {
    local path=$1 mode=$2
    if [[ -e $path || -L $path ]]; then
        [[ -d $path && ! -L $path ]] || fail 'Installation directory is not a regular directory.'
        [[ $(stat -c '%u:%g:%a' "$path") == "0:0:$mode" ]] || fail 'Installation directory ownership or mode is unsafe.'
    fi
}

check_file() {
    local path=$1 mode=$2
    if [[ -e $path || -L $path ]]; then
        [[ -f $path && ! -L $path ]] || fail 'Installation file is not a regular file.'
        [[ $(stat -c '%u:%g:%a' "$path") == "0:0:$mode" ]] || fail 'Installation file ownership or mode is unsafe.'
    fi
}

for path in /opt /etc /var /var/lib /etc/systemd /etc/systemd/system; do
    [[ -d $path && ! -L $path ]] || fail 'Installation parent is not a regular directory.'
    [[ $(stat -c '%u:%g' "$path") == 0:0 ]] || fail 'Installation parent must be root-owned.'
    mode=$(stat -c '%a' "$path")
    (( (8#$mode & 0022) == 0 )) || fail 'Installation parent must not be group/world writable.'
done
check_directory /opt/purrmatch-backup 755
check_directory /etc/purrmatch-backup 700
check_directory /var/lib/purrmatch-backup 700
for name in backup.py capture.py cloud.py; do
    check_file "/opt/purrmatch-backup/$name" 644
done
for name in purrmatch-backup.service purrmatch-backup.timer; do
    check_file "/etc/systemd/system/$name" 644
done
check_file /etc/purrmatch-backup/config.json 600
check_file /etc/purrmatch-backup/authorized-key.json 600

if [[ -f /etc/purrmatch-backup/config.json ]]; then
    /usr/bin/python3 -I - "$environment" <<'PY'
import json
import sys
try:
    with open('/etc/purrmatch-backup/config.json') as stream:
        config = json.load(stream)
    valid = isinstance(config, dict) and config.get('environment') == sys.argv[1]
except Exception:
    valid = False
if not valid:
    sys.exit('STOP: Existing configuration is invalid or belongs to another environment.')
PY
fi

install -d -o root -g root -m 0755 /opt/purrmatch-backup
install -d -o root -g root -m 0700 /etc/purrmatch-backup /var/lib/purrmatch-backup
for name in backup.py capture.py cloud.py; do
    install -o root -g root -m 0644 "$source_dir/$name" "/opt/purrmatch-backup/$name"
done
for name in purrmatch-backup.service purrmatch-backup.timer; do
    install -o root -g root -m 0644 "$source_dir/$name" "/etc/systemd/system/$name"
done
if [[ ! -e /etc/purrmatch-backup/config.json ]]; then
    /usr/bin/python3 -I - "$source_dir/config.example.json" "$environment" <<'PY'
import json
import os
import sys
with open(sys.argv[1]) as stream:
    config = json.load(stream)
config['environment'] = sys.argv[2]
descriptor = os.open('/etc/purrmatch-backup/config.json', os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
with os.fdopen(descriptor, 'w') as stream:
    json.dump(config, stream, indent=2)
    stream.write('\n')
PY
fi
systemctl_bounded daemon-reload
printf 'Installed for %s; existing config and authorized key preserved. No units started or enabled.\n' "$environment"
