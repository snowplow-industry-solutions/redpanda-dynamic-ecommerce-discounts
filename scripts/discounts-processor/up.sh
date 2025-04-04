#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)/../../config

docker compose up discounts-processor -d
