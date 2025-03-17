#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

docker-asciidoctor-builder "$@"
#../../docs/common/build.sh
