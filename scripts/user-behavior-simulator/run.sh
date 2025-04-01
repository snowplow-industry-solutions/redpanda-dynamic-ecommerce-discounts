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

case $mode in
ui)
  NODE_OPTIONS="--no-warnings --no-deprecation" \
    ./node_modules/.bin/playwright test tests/${behavior}.spec.ts
  ;;
kafka)
  NODE_OPTIONS="--no-warnings --no-deprecation --loader ts-node/esm" \
    ./node_modules/.bin/ts-node --esm src/index.ts "$behavior"
  ;;
*)
  echo "Error: Invalid mode: $mode"
  ;;
esac
