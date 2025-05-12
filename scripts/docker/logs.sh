#!/usr/bin/env bash
set -eou pipefail
BASE_DIR=$(dirname $(command -v $0))
source $BASE_DIR/common.sh

set-services "$@"
show-services 'Showing logs for'
[ "${options:-}" ] || options="-f"

echo Press Ctrl+C to free your terminal ...

docker compose logs ${services:-} $options
