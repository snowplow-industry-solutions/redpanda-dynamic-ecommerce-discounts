#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

source ./common.sh
check_usage "$@"
behaviour=$1

DATA_DIR=$DATA_DIR/$behaviour
mkdir -p $DATA_DIR

cd ../../docker

consume_topic() {
  local topic=$1
  local output_file=$2
  local tmout=${tmout:-5}

  echo $'\nConsuming' topic $topic to ${output_file#$BASE_DIR/}.rpk for $tmout seconds...
  docker compose exec redpanda rpk topic consume $topic --brokers localhost:9092 --format json >"$output_file.rpk" &
  local consumer_pid=$!
  sleep $tmout

  kill -INT $consumer_pid 2>/dev/null || :
  wait $consumer_pid 2>/dev/null || :

  echo Extracting only the value from ${output_file#$BASE_DIR/}.rpk to ${output_file#$BASE_DIR/}...
  jq -rs '.[].value' $output_file.rpk |
    case "$topic" in
    snowplow-enriched-good)
      jq -c 'if .event_name == "product_view" then
                {collector_tstamp, event_name, user_id, webpage_id, product_id, product_name, product_price}
              else
                {collector_tstamp, event_name, user_id, webpage_id}
              end' >$output_file
      ;;
    shopper-discounts)
      jq -c '{generated_at, user_id, product_id, discount}' >$output_file
      ;;
    esac

  f1=$output_file.rpk
  f2=${f1%.jsonl.rpk}.rpk.jsonl
  mv $f1 $f2
  echo File ${f1#$BASE_DIR/} renamed to ${f2#$BASE_DIR/}
}

echo "Saving topic events to JSONL files (in $DATA_DIR directory)..."
for topic in snowplow-enriched-good shopper-discounts; do
  consume_topic $topic $DATA_DIR/$topic.jsonl
done
