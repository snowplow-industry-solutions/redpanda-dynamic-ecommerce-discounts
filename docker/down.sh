#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)
source ./common.sh

set-services "$@"
show-services Stopping
[ "${options:-}" ] || options="-v"

docker compose down ${services:-} $options
