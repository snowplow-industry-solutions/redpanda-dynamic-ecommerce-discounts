#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

data_type=${data_type:-long}

case ${1:-} in
up) docker compose -f compose.redpanda.yaml $1 -d ;;
down) docker compose -f compose.redpanda.yaml $1 -v --remove-orphans ;;
produce)
  data_file=./data-samples/$data_type.jsonl
  [ -f $data_file ] || {
    echo "Data file $data_file not found"
    exit 1
  }
  echo "Sending $data_file events to snowplow-enriched-good topic..."
  sed 's/snowplow_ecommerce_action/product_view/g' $data_file |
    docker exec -i redpanda rpk topic produce snowplow-enriched-good
  ;;
consume)
  docker exec -i redpanda rpk topic consume snowplow-enriched-good
  ;;
*) echo "Usage: $0 [up|down|produce|consume]" ;;
esac
