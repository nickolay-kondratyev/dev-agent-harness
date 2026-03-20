#!/usr/bin/env bash
set -euo pipefail

# Ensure asgard libs are in maven local before Gradle starts (ref.ap.gtpABfFlF4RE1SITt7k1P.E).
# shellcheck source=_prepare_pre_build.sh
source "$(dirname "${BASH_SOURCE[0]}")/_prepare_pre_build.sh"
_prepare_asgard_dependencies

mkdir -p .tmp/

# Build the binary distribution before integration tests.
# E2E tests (e.g., StraightforwardWorkflowE2EIntegTest) run the binary via ProcessBuilder
# and require it to be installed at app/build/install/app/bin/app.
./gradlew :app:installDist 2>&1 | tee .tmp/installDist.txt

./gradlew :app:test -PrunIntegTests=true 2>&1 | tee .tmp/test_with_integ.txt
