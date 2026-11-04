#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
umask 077
mkdir -p backups
backup_file="backups/schitay-$(date -u +%Y%m%dT%H%M%SZ).dump"
docker compose exec -T db pg_dump -U schitay -d schitay --format=custom > "$backup_file.tmp"
mv "$backup_file.tmp" "$backup_file"
printf 'Backup: %s\n' "$backup_file"
