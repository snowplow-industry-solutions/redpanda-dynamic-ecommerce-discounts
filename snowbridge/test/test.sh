#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

[ -f data.tsv ] || {
    echo "Linking data.tsv ..."
    ln -s ../../scripts/ecommerce-events-viewer/all.tsv data.tsv
}

[ -f script.js ] || {
    echo "Linking script.js ..."
    ln -s ../product_view.js script.js
}

echo "Running snowbridge ..."
cat data.tsv | docker run --rm -i \
    --env ACCEPT_LIMITED_USE_LICENSE=yes \
    --mount type=bind,source=$(pwd)/config.hcl,target=/tmp/config.hcl \
    --mount type=bind,source=$(pwd)/script.js,target=/tmp/script.js \
    snowplow/snowbridge:3.2.0 > result.txt

echo "Done"
