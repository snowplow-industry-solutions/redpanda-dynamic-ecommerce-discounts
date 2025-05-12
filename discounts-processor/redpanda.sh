#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

produce_topic=${produce_topic:-'snowplow-enriched-good'}
consume_topic=${consume_topic:-'shopper-discounts'}
data_file=${data_file:-'long-1'}

case ${1:-} in
up) docker compose -f compose.redpanda.yaml $1 -d ;;
down) docker compose -f compose.redpanda.yaml $1 -v --remove-orphans ;;
restart)
  $0 down
  $0 up
  ;;
produce)
  data_file=./data-samples/$data_file.jsonl
  [ -f $data_file ] || {
    echo "Data file $data_file not found"
    exit 1
  }
  echo "Sending $data_file events to $produce_topic topic..."
  sed 's/snowplow_ecommerce_action/product_view/g' $data_file |
    while read -r line; do
      echo "$line" | jq -r --arg line "$line" '.user_id + " " + $line'
    done |
    docker exec -i redpanda rpk topic produce $produce_topic -f '%k %v\n'
  ;;
consume)
  echo "Loading events from $consume_topic topic..."
  docker exec -i redpanda rpk topic consume $consume_topic
  ;;
*) echo "Usage: $0 [up|down|produce|consume]" ;;
esac
