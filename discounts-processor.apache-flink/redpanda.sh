#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

case ${1:-} in
up) docker compose -f compose.redpanda.yaml $1 -d ;;
down) docker compose -f compose.redpanda.yaml $1 -v --remove-orphans ;;
produce)
  docker exec -i redpanda rpk topic produce snowplow-enriched-good < \
    ../scripts/user-behavior-simulator/data/long.jsonl
  ;;
consume)
  docker exec -i redpanda rpk topic consume snowplow-enriched-good
  ;;
*) echo "Usage: $0 [up|down|produce|consume]" ;;
esac
