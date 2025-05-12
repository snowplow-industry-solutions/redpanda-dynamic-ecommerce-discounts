#!/usr/bin/env bash
set -eou pipefail
BASE_DIR=$(dirname $(command -v $0))
source $BASE_DIR/common.sh

[ "${1:-}" ] || set -- local
case "$1" in
local | all) : ;;
*) echo Invalid option: $1 ;;
esac

yes | docker volume prune || :

echo Removing $1 images ...
docker compose down --rmi $1
