#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

source ./common.sh

check_usage "$@"
handle_redpanda "$@"

export KAFKAJS_NO_PARTITIONER_WARNING=1
[ -d node_modules ] || npm install

behavior=$1
mode=$2

cleanup() {
  pkill -P $$
  exit 0
}

trap cleanup SIGINT SIGTERM

validate_mode "$mode" || exit 1

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
    NODE_OPTIONS="--no-warnings --no-deprecation --loader ts-node/esm" \
      LOG_FILE="$logs_file" node --experimental-specifier-resolution=node src/index.ts "$behavior"
  else
    echo "File $latest_file not found!"
  fi
  ;;
esac
