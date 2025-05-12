#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)/..

git config core.hooksPath .hooks
echo "Git hooks configured successfully!"
