#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)
source ./common.sh

set-services "$@"
show-services Starting

docker compose up ${services:-} --build -d

! $SHOW_LOGS || $DOCKER_DIR/logs.sh "$@"
