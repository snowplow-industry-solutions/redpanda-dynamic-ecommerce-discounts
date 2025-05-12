#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

source ./common.sh
check_usage "$@"
behaviour=$1

input_file=$DATA_DIR/$behaviour/snowplow-enriched-good.jsonl

[ -f $input_file ] || {
  echo Aborting! File ${input_file#$BASE_DIR/} not found.
  exit 1
}

version=${version:-kt} \
  ../../discounts-processor/e2e/summarize.sh $input_file
