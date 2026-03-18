#!/usr/bin/env bash
# __enable_bash_strict_mode__

main() {
  ei2 is_in_docker
  cdi.repo_root
  file_verify_exists "./ticket_shepherd_marker.txt"

# shellcheck disable=SC2155
local tmp_file="$(ei2 make_tmp_file)"
cat >> "${tmp_file:?}" <<EOF
    READ specs under FOLDER=[./doc]. FOCUS on the specs and the CODE that exists.

    GOAL: FIX inconsistencies in the code.

### IF very quick to FIX: FIX
    IF the code change is very small/quick, THEN: fix inline right away.

### IF the change is LARGE: DELETE code
   IF the change is large in the code to align it, THEN: delete the code for now. We are going to circle back and re-implement it from spec.

### IF requires some more work: Create ticket
  IF requires some more work to FIX: Create a ticket with Title "CODE_CONSISTENCY_FIX: <XXX>", do not fix right away.


EOF

  local stop_file="$HOME/done_with_code_consistency_check.md"
  if [[ -f "${stop_file}" ]]; then
    rm "${stop_file:?}"
  fi

  for i in {1..1} ; do
      if [[ -f "${stop_file:?}" ]]; then
        echo.green "${stop_file:?} file exists. Exiting."
      	exit 1
      fi

      cdi.repo_root
      git.save

      ai_core --agent CC --model opus "Read and execute instructions in [${tmp_file:?}]"

      git.save
  done
}

main "${@}"
