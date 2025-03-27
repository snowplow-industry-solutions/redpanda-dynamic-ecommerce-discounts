#!/usr/bin/env bash

# This function is called by ../docker/common.sh
setup() {
  local initial_dir=$PWD
  log-info Calling $FUNCNAME function ...
  if ! [ -d $REPO_BASE_DIR/$REPO_DIR ]; then
    log-info Cloning $ECOMMERCE_NEXTJS_EXAMPLE_STORE_GIT_REPO to $REPO_BASE_DIR
    cd $REPO_BASE_DIR
    git clone $ECOMMERCE_NEXTJS_EXAMPLE_STORE_GIT_REPO $REPO_DIR
    cd $REPO_DIR
  else
    cd $REPO_BASE_DIR/$REPO_DIR
  fi
  log-info Syncing $CONFIG_DIR/$REPO_DIR/ to $REPO_BASE_DIR/$REPO_DIR/ ...
  rsync -a $CONFIG_DIR/$REPO_DIR/ ./
  cd $initial_dir
}
