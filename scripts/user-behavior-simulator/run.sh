#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

docker_dir=${docker_dir:-../../docker}

case "${1:-}" in
snowplow | redpanda)
  group=$1
  shift
  case "${1:-}" in
  up | down)
    $docker_dir/$1.sh $group-services
    exit 0
    ;;
  logs)
    ! [[ $group = redpanda ]] || $docker_dir/logs.sh discounts-processor
    exit 0
    ;;
  esac
  ;;
esac

export KAFKAJS_NO_PARTITIONER_WARNING=1
[ -d node_modules ] || npm install
node index.js "$@"
