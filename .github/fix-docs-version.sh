#!/bin/bash
#
# This script takes care of setting proper version in docs.
#
set -eo pipefail

VERSION=$1

update_file_versions() {
  local VERSION="$1"
  local FILE="$2"
  sed -i "s/dm3270\(.*\):.*'/dm3270\1:${VERSION}'/g" "${FILE}"
  sed -i "/dm3270*<\/artifactId>$/{N;s/dm3270\(.*\)<\/artifactId>\n  <version>.*<\/version>/dm3270\1<\/artifactId>\n  <version>${VERSION}<\/version>/}" "${FILE}"
}

update_file_versions ${VERSION} README.md

git add README.md
git config --local user.email "$(git log --format='%ae' HEAD^!)"
git config --local user.name "$(git log --format='%an' HEAD^!)"
git commit -m "[skip ci] Updated docs artifacts versions"
git push origin HEAD:master
