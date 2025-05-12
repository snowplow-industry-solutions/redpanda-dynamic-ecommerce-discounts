#!/usr/bin/env bash
cd $(dirname $0)
f=../docs/common/functions.sh
[ -f $f ] || exit 1
sync_fn() {
  cp \
    ../discounts-processor/e2e/data/MultiProduct.json \
    ../discounts-processor/e2e/data/MostViewedMultipleViewsPerProduct.json \
    $1/

  cp \
    ./com.snowplow/shopper_discount_applied/jsonschema/1-0-0 \
    $1/
}
. $f && readme-build sync_fn
