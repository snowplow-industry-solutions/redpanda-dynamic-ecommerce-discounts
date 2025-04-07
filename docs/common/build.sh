#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

common_dir=$(readlink ${BASH_SOURCE[0]})
common_dir=${common_dir%/build.sh}
source $common_dir/README.sh

build-docs "$@"
