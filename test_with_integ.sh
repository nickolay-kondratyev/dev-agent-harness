#!/usr/bin/env bash
set -euo pipefail

export THORG_ROOT=${THORG_ROOT:-$HOME/thorg-root}

mkdir -p .tmp/
./gradlew :app:test -PrunIntegTests=true 2>&1 | tee .tmp/test_with_integ.txt
