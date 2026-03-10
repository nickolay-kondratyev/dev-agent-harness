#!/usr/bin/env bash
set -euo pipefail

# Ensure asgard libs are in maven local before Gradle starts (ref.ap.gtpABfFlF4RE1SITt7k1P.E).
# shellcheck source=_prepare_pre_build.sh
source "$(dirname "${BASH_SOURCE[0]}")/_prepare_pre_build.sh"
_prepare_asgard_dependencies

mkdir -p .tmp/
./gradlew :app:test 2>&1 | tee .tmp/test.txt
