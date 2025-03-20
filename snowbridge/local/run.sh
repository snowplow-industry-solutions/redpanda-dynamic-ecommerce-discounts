#!/usr/bin/env bash
# https://docs.snowplow.io/docs/destinations/forwarding-events/snowbridge/testing/
set -eou pipefail
cd $(dirname $0)

echo "Running Snowplow Micro to collect data.tsv ..."
docker run --rm -p 9090:9090 snowplow/snowplow-micro:2.1.3 --output-tsv > data.tsv
