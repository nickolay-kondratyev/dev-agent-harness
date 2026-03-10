#!/usr/bin/env bash
set -euo pipefail

# Emits all Gradle tasks as JSONL (one JSON object per line).
#
# Caching strategy: Gradle's UP-TO-DATE mechanism.
# `./gradlew tasksJson` completes in <1s when no build files have changed,
# reusing build/tasks-json/tasks.json from the previous run.
# This avoids brittle shell-level memoization (e.g. memoize_by_pwd)
# that cannot detect when tasks actually change.

main() {
  # Run tasksJson; Gradle skips execution if inputs (build files) are unchanged.
  ./gradlew --quiet --console=plain tasksJson

  # Stream the JSON array as JSONL: one compact JSON object per line.
  jq '.[]' -c build/tasks-json/tasks.json
}

main "$@"
