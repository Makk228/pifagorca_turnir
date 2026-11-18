#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
umask 077
mkdir -p backups
backup_file="backups/schitay-$(date -u +%Y%m%dT%H%M%SZ).tar.gz"
docker compose stop app
trap 'docker compose start app >/dev/null' EXIT
docker run --rm -v schitay_app_data:/data:ro -v "$PWD/backups:/backup" alpine:3.21 \
  tar czf "/backup/$(basename "$backup_file")" -C /data .
docker compose start app >/dev/null
trap - EXIT
printf 'Backup: %s\n' "$backup_file"
