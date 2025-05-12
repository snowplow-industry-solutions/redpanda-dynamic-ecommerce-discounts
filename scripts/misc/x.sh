#!/usr/bin/env bash
set -eou pipefail

log_dir=${log_dir:-private/logs}
mkdir -p $log_dir

if [[ "${1:-}" =~ ^[0-9]+$ ]] && [ -f "$log_dir/$1.log" ]; then
  output_file="$log_dir/$1.log"
  shift
elif ! [ "${f:-}" ]; then
  last_num=0
  if ls $log_dir/*.log >/dev/null 2>&1; then
    last_num=$(ls $log_dir/*.log | sed "s,$log_dir\/,,g" | sed 's/.log$//' | sort -n | tail -n 1)
  fi
  next_num=$((last_num + 1))
  output_file="$log_dir/${next_num}.log"
else
  output_file="$log_dir/$f.log"
fi

echo Output file: "$output_file" Contents: $'\n'
printf '$ %s\n' "$*" | tee "$output_file"
if ! command -v "${1:-}" &>/dev/null; then
  echo "command not found: $1" | tee -a "$output_file"
  exit 127
fi
exec "$@" 2>&1 | tee -a "$output_file" || {
  exit_code=$?
  exit $exit_code
}
