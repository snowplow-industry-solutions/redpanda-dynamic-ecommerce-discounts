#!/usr/bin/env bash

PROJECT_DIR=$(
  cd ../..
  pwd
)
PROJECT_DOCS_DIR=$PROJECT_DIR/docs

readme-build() {
  local readme_dir=$PWD

  readme_dir=${readme_dir##$PROJECT_DIR}
  echo Syncing $readme_dir to $DOCS_DIR...
  mkdir -p $PROJECT_DOCS_DIR/${readme_dir##*/}
  cp README.adoc $PROJECT_DOCS_DIR/${readme_dir##*/}
  cd $PROJECT_DOCS_DIR/${readme_dir##*/}

  ln -sf ../common/build.sh
  ./build.sh

  cd $OLDPWD
  echo Syncing $PROJECT_DOCS_DIR/${readme_dir##*/}/ to .
  cp $PROJECT_DOCS_DIR/${readme_dir##*/}/README.{html,pdf} .
  cp $PROJECT_DOCS_DIR/${readme_dir##*/}/*.css .
  echo Removing $PROJECT_DOCS_DIR/${readme_dir##*/}
  rm -rf $PROJECT_DOCS_DIR/${readme_dir##*/}
}

readme-html-view() {
  ! [ -f README.html ] || open ./README.html &>/dev/null
}

readme-pdf-view() {
  ! [ -f README.pdf ] || open ./README.pdf &>/dev/null
}
