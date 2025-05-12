#!/usr/bin/env bash
set -eou pipefail
BASE_DIR=$(dirname $(command -v $0))
source $BASE_DIR/common.sh

available-ops() {
  echo "build|down|lazy|logs|ps|up|stats"
}

help() {
  cat <<EOF
Usage:
$0 <$(available-ops)>
EOF
  exit 1
}

op=${1:-}
[ "$op" ] || help
shift

if [[ "$op" =~ ^($(available-ops))$ ]]; then
  sudo=
  [ "$op" = lazy ] && sudo=sudo
  $sudo ./$op.sh "$@"
else
  help
fi
