#!/usr/bin/env bash
set -eou pipefail
BASE_DIR=$(dirname $(command -v $0))
source $BASE_DIR/common.sh

set-services "$@"
show-services Starting

if $SHOW_LOGS; then
  log-info You can type Ctrl+C at any time to stop showing logs \(this will not stop the containers\)
  docker compose up ${services:-} --build
else
  docker compose up ${services:-} --build -d
fi
