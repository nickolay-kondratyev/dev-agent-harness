#!/usr/bin/env bash
# Fetches a SonarCloud report (quality gate, metrics, issues) and writes it to a JSON file.
#
# Required env vars:
#   SONAR_TOKEN    - SonarCloud authentication token
#
# Optional env vars:
#   PROJECT_KEY    - SonarCloud project key (default: nickolay-kondratyev_dev-agent-harness)
#   REPORT_DIR     - Directory for the report output (default: _reports)
set -euo pipefail

if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "ERROR: SONAR_TOKEN environment variable is not set."
  echo "  Export it before running this script:"
  echo "    export SONAR_TOKEN=<your-sonarcloud-token>"
  exit 1
fi

# Prefer system curl over linuxbrew (linuxbrew curl may have missing shared libs)
CURL="curl"
if [[ -x /usr/bin/curl ]]; then
  CURL="/usr/bin/curl"
fi

PROJECT_KEY="${PROJECT_KEY:-nickolay-kondratyev_dev-agent-harness}"
REPORT_DIR="${REPORT_DIR:-_reports}"
REPORT_FILE="${REPORT_DIR}/sonar_report.json"
SONAR_ISSUES_JSONL="${REPORT_DIR}/sonar_issues.jsonl"

mkdir -p "${REPORT_DIR}"

# fetch_sonar_api: fetches a SonarCloud API endpoint with error handling.
# Usage: fetch_sonar_api <label> <url>
# Returns JSON via stdout. Exits non-zero with clear message on failure.
fetch_sonar_api() {
  local label="$1"
  local url="$2"
  local response

  if ! response=$("${CURL}" -sf \
    -H "Authorization: Bearer ${SONAR_TOKEN}" \
    "${url}"); then
    echo "ERROR: Failed to fetch ${label} from SonarCloud API."
    echo "  URL: ${url}"
    echo "  Check that SONAR_TOKEN is valid and SonarCloud is reachable."
    exit 1
  fi

  # Validate response is valid JSON before returning
  if ! echo "${response}" | jq empty 2>/dev/null; then
    echo "ERROR: ${label} returned non-JSON response."
    echo "  Response body (first 500 chars): ${response:0:500}"
    exit 1
  fi

  echo "${response}"
}

echo "Fetching report from SonarCloud API..."

# Fetch quality gate status
quality_gate=$(fetch_sonar_api "quality gate status" \
  "https://sonarcloud.io/api/qualitygates/project_status?projectKey=${PROJECT_KEY}")

# Fetch key metrics
metrics=$(fetch_sonar_api "metrics" \
  "https://sonarcloud.io/api/measures/component?component=${PROJECT_KEY}&metricKeys=bugs,vulnerabilities,code_smells,coverage,duplicated_lines_density,ncloc")

# Fetch open issues (first page, up to 500)
issues=$(fetch_sonar_api "issues" \
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

echo "SonarCloud report written to: ${REPORT_FILE}"

cat "${REPORT_FILE}" | jq .issues.issues[] -c > "${SONAR_ISSUES_JSONL:?}"