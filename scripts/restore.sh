#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ "$#" -ne 2 ] || [ "$2" != "--confirm-replace-database" ] || [ ! -f "$1" ]; then
 echo 'Usage: scripts/restore.sh backups/file.tar.gz --confirm-replace-database' >&2
 echo 'Stops the application and replaces the database. Take a fresh backup first.' >&2
 exit 2
fi
docker compose stop app
backup_dir=$(cd "$(dirname "$1")" && pwd)
backup_name=$(basename "$1")
docker run --rm -v schitay_app_data:/data -v "$backup_dir:/backup:ro" alpine:3.21 \
  sh -c 'find /data -mindepth 1 -maxdepth 1 -delete && tar xzf "/backup/$1" -C /data' sh "$backup_name"
docker compose start app
