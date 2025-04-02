#!/usr/bin/env bash
# Usage: . README.sh # NOTE: you show be in the same directory as this file
cd $(dirname $0)
source ../../docs/common/functions.sh
(
  set -eou pipefail
  readme-build
)
