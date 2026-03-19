#!/usr/bin/env bash
# __enable_bash_strict_mode__

main() {
  cdi.repo_root

  mkdir -p ./.tmp/
  local sonar_out="./.tmp/sonar_run_$(date_now_file_friendly).txt"
  local test_out="./.tmp/test_run_$(date_now_file_friendly).txt"

  local failures=()


  ./test.sh &> ${test_out:?} || {
    failures+=("[./test.sh] - FAILED to run. Need to ROOT cause and fix. (Output in [${test_out:?}])")
  }

  ./run_sonar.sh &> "${sonar_out:?}" || {
    failures+=("[./run_sonar.sh] - FAILED to run. Need to ROOT cause and fix. (Output in [${sonar_out:?}])")
  }

  if cat ./_reports/sonar_issues.jsonl | lines.count_greater_than 0; then
    failures+=("[./run_sonar.sh] - There are issues found in [./_reports/sonar_issues.jsonl] Need to root cause and fix.")
  fi

  if [[ ${#failures[@]} -eq 0 ]]; then
      return 0
  else
    array_print "${failures[@]}"
    return 1
  fi
}

main "${@}"
