#!/usr/bin/env bash
cd $(dirname $0)
f=../../docs/common/functions.sh
[ -f $f ] || exit 1
sync_fn() {
  cp frequent.most-viewed.json $1/
}
. $f && readme-build sync_fn
