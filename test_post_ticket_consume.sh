#!/usr/bin/env bash
# __enable_bash_strict_mode__

main() {
  cdi.repo_root

  mkdir -p ./.tmp/
  ./run_sonar.sh &> "./.tmp/sonar_run_$(date_now_file_friendly).txt"

  if cat ./_reports/sonar_issues.jsonl | lines.count_greater_than 0; then
    echo "There are issues found in [./_reports/sonar_issues.jsonl]"
    return 1
  fi

  return 0
}

main "${@}"
