#!/usr/bin/env bash
set -euo pipefail

# Validate SONAR_TOKEN is set (fail fast)
if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "ERROR: SONAR_TOKEN environment variable is not set."
  echo "  Export it before running this script:"
  echo "    export SONAR_TOKEN=<your-sonarcloud-token>"
  exit 1
fi

mkdir -p .tmp/

# Run SonarCloud analysis (configuration cache is incompatible with sonar plugin)
echo "Running SonarCloud analysis..."
./gradlew sonar --no-configuration-cache 2>&1 | tee .tmp/sonar_analysis.txt

echo ""
bash "$(dirname "$0")/scripts/tools/sonar/fetch_sonar_report.sh"
