#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)/../..

command -v devcontainer &>/dev/null || {
  command -v npm || {
    echo You need to install Node.js first!
    exit 1
  }
  npm install -g @devcontainers/cli
}

op=${1:-'exec-bash'}
case "${op:-}" in
up) devcontainer up --workspace-folder . ;;
exec-bash) devcontainer exec --workspace-folder . bash ;;
esac
