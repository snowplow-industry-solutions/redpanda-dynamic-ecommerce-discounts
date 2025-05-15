#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() {
  local color="$1"
  local message="$2"
  echo -e "${color}${message}${NC}"
}

check_npm_install() {
  if [ ! -d node_modules ]; then
    log "$YELLOW" "Directory node_modules not found. Running npm install..."
    npm install
    log "$GREEN" "Dependencies installed successfully!"
    return
  fi

  if [ package.json -nt node_modules ]; then
    log "$YELLOW" "package.json is newer than node_modules. Running npm install..."
    npm install
    log "$GREEN" "Dependencies updated successfully!"
    return
  fi

  log "$GREEN" "Dependencies are up to date."
}

main() {
  check_npm_install
  if [ "${1:-}" != now ]; then
    log "$GREEN" "Running: node index.js $*"
    node index.js "$@"
  else
    shift
    node send-now.js "$@"
  fi
}

main "$@"
