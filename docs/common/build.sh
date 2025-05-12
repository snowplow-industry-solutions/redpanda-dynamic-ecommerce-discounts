#!/usr/bin/env bash
set -eou pipefail
BASE_DIR=$(
  cd $(dirname $0)
  pwd
)

common_dir=$(readlink ${BASH_SOURCE[0]})
common_dir=${common_dir%/build.sh}

cd $BASE_DIR
source $common_dir/README.sh

build-docs "$@"
