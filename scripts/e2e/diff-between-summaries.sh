#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

source ./common.sh
check_usage "$@"
behaviour=$1

tool=${tool:-diff}
for version in kt js; do
  f=data/$behaviour/snowplow-enriched-good.summary-by-$version.json
  [ -f $f ] || {
    echo Aborting: File $f not found!
    exit 1
  }
done

$tool \
  <(jq . data/$behaviour/snowplow-enriched-good.summary-by-kt.json) \
  <(jq . data/$behaviour/snowplow-enriched-good.summary-by-js.json)
