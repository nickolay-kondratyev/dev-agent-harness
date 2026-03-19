#!/usr/bin/env bash
set -euo pipefail

# Generates Kover coverage XML (if not fresh) and displays per-file coverage report.
# Files with lowest coverage appear first.
#
# Usage:
#   ./coverage_report.sh              # Run coverage + show report (80% threshold)
#   ./coverage_report.sh --threshold 60   # Custom threshold
#   ./coverage_report.sh --skip-run   # Show report from existing XML (skip Gradle)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

SKIP_RUN=false
EXTRA_ARGS=()

for arg in "$@"; do
    if [[ "$arg" == "--skip-run" ]]; then
        SKIP_RUN=true
    else
        EXTRA_ARGS+=("$arg")
    fi
done

if [[ "$SKIP_RUN" == false ]]; then
    echo "Running coverage..."
    bash coverage.sh
    echo ""
fi

python3 coverage_report.py "${EXTRA_ARGS[@]}"
