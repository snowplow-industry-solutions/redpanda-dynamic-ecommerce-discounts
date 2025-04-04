#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

if [ $# -lt 2 ]; then
  echo "Error: Missing required parameters"
  echo "Usage: $0 <behavior> <mode>"
  echo "  behavior: frequent | long | normal"
  echo "  mode: kafka | ui"
  exit 1
fi

docker_dir=${docker_dir:-../../docker}
discounts_processor_logs_dir=${discounts_processor_logs_dir:-../discounts-processor/logs}

case "${1:-}" in
redpanda)
  group=$1
  shift
  case "${1:-}" in
  up | down)
    $docker_dir/$1.sh $group-services
    exit 0
    ;;
  logs)
    $docker_dir/logs.sh discounts-processor
    exit 0
    ;;
  esac
  ;;
esac

export KAFKAJS_NO_PARTITIONER_WARNING=1
[ -d node_modules ] || npm install

behavior=$1
mode=$2

cleanup() {
  pkill -P $$
  exit 0
}

trap cleanup SIGINT SIGTERM

case $mode in
ui)
  behavior_file=tests/${behavior}.spec.ts
  [ -f $behavior_file ] || { echo "File not found: $behavior_file" && exit 1; }
  NODE_OPTIONS="--no-warnings --no-deprecation" \
    ./node_modules/.bin/playwright test $behavior_file
  ;;
kafka)
  latest_file=$discounts_processor_logs_dir/latest
  if [ -f $latest_file ]; then
    logs_file=./logs/$(<$latest_file)
    echo Generating logs in file $logs_file ...
    mkdir -p ./logs
    NODE_OPTIONS="--no-warnings --no-deprecation --loader ts-node/esm" \
      LOG_FILE="$logs_file" node --experimental-specifier-resolution=node src/index.ts "$behavior"
  else
    echo "File $latest_file not found!"
  fi
  ;;
*)
  echo "Error: Invalid mode: $mode"
  ;;
esac
