#!/usr/bin/env bash

# Interactive Gradle task runner.
#
# Fetches all tasks via gradle_tasks_jsonl_cached.sh (Gradle-level caching),
# presents them with fzf for selection, then runs the chosen task.
#
# Requires: fzf, jq, ./gradlew
# Optional: eai2 (falls back to ./gradlew), history_add, shell.is_command_defined

main() {
  local chosen_path tasks_jsonl
  tasks_jsonl="$(./gradle_tasks_jsonl_cached.sh)"
  chosen_path=$(echo "${tasks_jsonl:?}" | jq .path -r | fzf)

  # -n: return true when value is not empty.
  if [[ -n "${chosen_path}" ]]; then
    if command -v shell.is_command_defined &>/dev/null && shell.is_command_defined history_add; then
      history_add ./gradle_run.sh
      history_add ./gradlew "${chosen_path:?}"
    fi

    ./gradlew "${chosen_path:?}"
  fi
}

main "$@"
