#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)
log_file=logs/$(<logs/latest)
echo Latest log is $log_file. Contents: >&2
echo "$(<$log_file)"
