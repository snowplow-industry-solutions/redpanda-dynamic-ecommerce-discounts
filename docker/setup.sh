#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

[ -f .env ] || {
  echo Generating a valid .env file in $PWD...
  sed 's/false/true/g' .env.sample >.env
}
./ecommerce-nextjs-example-store/setup.sh
