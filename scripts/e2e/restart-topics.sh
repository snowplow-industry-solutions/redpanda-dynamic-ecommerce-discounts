#!/usr/bin/env bash
set -eou pipefail
cd "$(dirname "$0")"
BASE_DIR=$(pwd)

cd ../../docker

echo Stopping discounts-processor service...
docker compose stop discounts-processor

echo $'\nStarting' redpanda-topics-removal service...
docker compose --profile test up redpanda-topics-removal

echo $'\nStarting' redpanda-topics-creator service...
docker compose up -d redpanda-topics-creator discounts-processor
