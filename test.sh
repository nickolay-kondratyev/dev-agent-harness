#!/usr/bin/env bash
set -euo pipefail

export THORG_ROOT=$PWD/submodules/thorg-root

mkdir -p .tmp/
./gradlew :app:test 2>&1 | tee .tmp/test.txt
