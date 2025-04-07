#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

usage() {
  echo "Usage: $0 <up|down>"
}

if ! [ "$#" -eq 1 ]; then
  usage
  exit 1
else
  case $1 in
  up | down) : ;;
  *)
    usage
    exit 1
    ;;
  esac
fi

case $1 in
up)
  docker compose $1 -d --build
  ;;
down)
  docker compose $1 --volumes --remove-orphans
  ;;
esac
