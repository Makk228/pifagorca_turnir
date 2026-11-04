#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
[ -f .env ] || { echo 'Create .env from .env.example first' >&2; exit 1; }
if grep -q 'REPLACE_WITH\|arithmetic.example.org\|admin@example.org' .env; then
 echo 'Fill all .env values before deployment' >&2; exit 1
fi
chmod 600 .env
docker compose config --quiet
docker compose build --pull
docker compose up -d --wait --wait-timeout 180
echo 'Deployment healthy. HTTPS is managed by Caddy.'
