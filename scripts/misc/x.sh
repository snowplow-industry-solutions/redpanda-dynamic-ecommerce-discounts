#!/usr/bin/env bash
set -eou pipefail

tmp_dir=${tmp_dir:-./private/tmp}
mkdir -p $tmp_dir

if [[ "${1:-}" =~ ^[0-9]+$ ]] && [ -f "$tmp_dir/$1.txt" ]; then
  output_file="$tmp_dir/$1.txt"
  shift
elif ! [ "${f:-}" ]; then
  last_num=0
  if ls $tmp_dir/*.txt >/dev/null 2>&1; then
    last_num=$(ls $tmp_dir/*.txt | sed "s,$tmp_dir\/,,g" | sed 's/.txt$//' | sort -n | tail -n 1)
  fi
  next_num=$((last_num + 1))
  output_file="$tmp_dir/${next_num}.txt"
else
  output_file="$tmp_dir/$f.txt"
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
