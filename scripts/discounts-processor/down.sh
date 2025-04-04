#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)/../../config

! [ "${1:-}" = all ] || {
  docker compose --profile tools down --remove-orphans -v
  exit 0
}

docker compose down discounts-processor memcached
docker compose --profile tools up topics-remover -d
