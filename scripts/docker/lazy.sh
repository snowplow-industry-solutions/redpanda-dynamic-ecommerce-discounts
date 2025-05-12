#!/usr/bin/env bash
set -eou pipefail

mkdir -p ~/.lazydocker

docker run --rm -it \
  -u $(id -u):$(id -g) \
  -v ~/.lazydocker:/.config/jesseduffield/lazydocker \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --name lazydocker \
  lazyteam/lazydocker
