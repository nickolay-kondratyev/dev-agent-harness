#!/usr/bin/env bash
set -euo pipefail

export THORG_ROOT=$PWD/submodules/thorg-root

mkdir -p .tmp/
./gradlew :app:test -PrunIntegTests=true > .tmp/test_with_integ.txt 2>&1
