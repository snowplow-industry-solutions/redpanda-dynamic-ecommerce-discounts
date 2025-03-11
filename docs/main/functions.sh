#!/usr/bin/env bash

before-build-docs() {
  local architecture_image=./architecture/images/architecture.png
  [ -f $architecture_image ] || {
    echo Generating $architecture_image ...
    ./architecture/generate-images.sh
    echo Copying $architecture_image to images/ ...
    cp $architecture_image images/
  }
}
