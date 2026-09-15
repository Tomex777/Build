#!/usr/bin/env bash
set -euo pipefail

rm -rf libxposed/api libxposed/service
mkdir -p libxposed

git clone --depth 1 --branch 100 https://github.com/libxposed/api.git libxposed/api
git clone --depth 1 --branch 100 https://github.com/libxposed/service.git libxposed/service

(
  cd libxposed/api
  ./gradlew --no-daemon :api:publishApiPublicationToMavenLocal
)
(
  cd libxposed/service
  ./gradlew --no-daemon publishToMavenLocal
)

# Fail early if either exact API-100 coordinate was not published.
test -d "$HOME/.m2/repository/io/github/libxposed/api/100"
test -d "$HOME/.m2/repository/io/github/libxposed/service/100-1.0.0"
