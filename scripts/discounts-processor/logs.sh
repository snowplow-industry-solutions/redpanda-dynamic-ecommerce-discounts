#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)
mkdir -p logs
tstamp=$(date +%s)
service=discounts-processor
log=logs/$tstamp.log
echo "${log##*/}" >logs/latest
echo Log file: $log
docker_logs=../../docker/logs.sh

if [ "${1:-}" = raw ]; then
  $docker_logs $service | tee $log
  exit 0
fi

$docker_logs $service | tee $log | ./console.awk
