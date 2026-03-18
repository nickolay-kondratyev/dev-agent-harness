#!/usr/bin/env bash
set -euo pipefail

# Validate SONAR_TOKEN is set (fail fast)
if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "ERROR: SONAR_TOKEN environment variable is not set."
  echo "  Export it before running this script:"
  echo "    export SONAR_TOKEN=<your-sonarcloud-token>"
  exit 1
fi

PROJECT_KEY="nickolay-kondratyev_dev-agent-harness"
REPORT_DIR="_reports"
REPORT_FILE="${REPORT_DIR}/sonar_report.json"

mkdir -p "${REPORT_DIR}"
mkdir -p .tmp/

# Run SonarCloud analysis (configuration cache is incompatible with sonar plugin)
echo "Running SonarCloud analysis..."
./gradlew sonar --no-configuration-cache 2>&1 | tee .tmp/sonar_analysis.txt

echo ""
echo "Analysis uploaded. Fetching report from SonarCloud API..."

# Fetch quality gate status
quality_gate=$(curl -s \
  -H "Authorization: Bearer ${SONAR_TOKEN}" \
  "https://sonarcloud.io/api/qualitygates/project_status?projectKey=${PROJECT_KEY}")

# Fetch key metrics
metrics=$(curl -s \
  -H "Authorization: Bearer ${SONAR_TOKEN}" \
  "https://sonarcloud.io/api/measures/component?component=${PROJECT_KEY}&metricKeys=bugs,vulnerabilities,code_smells,coverage,duplicated_lines_density,ncloc")

# Fetch open issues (first page, up to 500)
issues=$(curl -s \
  -H "Authorization: Bearer ${SONAR_TOKEN}" \
  "https://sonarcloud.io/api/issues/search?componentKeys=${PROJECT_KEY}&resolved=false&ps=500&p=1")

# Combine all three API responses into a single JSON report
jq -n \
  --argjson quality_gate "${quality_gate}" \
  --argjson metrics "${metrics}" \
  --argjson issues "${issues}" \
  '{
    quality_gate: $quality_gate,
    metrics: $metrics,
    issues: $issues
  }' > "${REPORT_FILE}"

echo ""
echo "SonarCloud report written to: ${REPORT_FILE}"
