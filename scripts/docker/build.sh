#!/usr/bin/env bash
set -eou pipefail
BASE_DIR=$(dirname $(command -v $0))
source $BASE_DIR/common.sh

set-services "$@"
show-services Building

docker compose build ${services:-}
