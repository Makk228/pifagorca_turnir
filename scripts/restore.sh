#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ "$#" -ne 2 ] || [ "$2" != "--confirm-replace-database" ] || [ ! -f "$1" ]; then
 echo 'Usage: scripts/restore.sh backups/file.dump --confirm-replace-database' >&2
 echo 'Stops the application and replaces the database. Take a fresh backup first.' >&2
 exit 2
fi
docker compose stop app
docker compose exec -T db pg_restore -U schitay -d schitay --clean --if-exists --no-owner --single-transaction < "$1"
docker compose start app
