#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)/..

DOCKER_DIR=$PWD

env_file=./.env
[ -f $env_file ] || {
  echo File $env_file not found. Aborting!
  exit 1
}
source $env_file

if ! [ -d $REPO_BASE_DIR/$REPO_DIR ]; then
  echo Cloning $ECOMMERCE_NEXTJS_EXAMPLE_STORE_GIT_REPO to $REPO_BASE_DIR
  cd $REPO_BASE_DIR
  git clone $ECOMMERCE_NEXTJS_EXAMPLE_STORE_GIT_REPO $REPO_DIR
  cd $REPO_DIR
else
  cd $REPO_BASE_DIR/$REPO_DIR
fi

if [ "${CODESPACES:-}" = "true" ]; then
  codespace=$(gh codespace list --json name --jq '.[0].name')
  echo You are using the codespace "$codespace". We need an additional configuration...
  browserUrl=$(gh codespace ports --codespace $codespace --json sourcePort,browseUrl --jq '.[] | select(.sourcePort == 9090) | .browseUrl')
else
  browserUrl=http://localhost:9090
fi

echo Syncing $DOCKER_DIR/$REPO_DIR/ to ./ ...
rsync -a --exclude=setup.sh $DOCKER_DIR/$REPO_DIR/ ./

sed -i "s,browserUrl,$browserUrl,g" .env.local
