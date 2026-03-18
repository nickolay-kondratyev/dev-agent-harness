#!/usr/bin/env bash
# __enable_bash_strict_mode__

main() {
  ei2 is_in_docker
  cdi.repo_root

# shellcheck disable=SC2155
local tmp_file="$(ei2 make_tmp_file)"
cat >> "${tmp_file:?}" <<EOF
    READ specs under FOLDER=[./doc]. FOCUS on specs not code.

    GOAL: FIX inconsistencies in the specs.

### IF obvious how to FIX: FIX
    IF there is obvious and easy inconsistency to fix such as non-authoritative spec (spec that is not focused on the topic). Mentioning outdated info when compared to authoritative spec (spec focused on the particular topic).

    THEN FIX the non-authoritative spec in two possible ways 1) update it to align with authoritative spec if it makes sense to mention the info. 2) slim down non-authoritative spec so that it only references the authorative spec but does not clutter itself with such details that could change.

### IF not-obvious how to FIX: Create a ticket
  IF not-obvious how to FIX: Create a ticket with Title "CONSISTENCY_DECISION: <XXX>", do not fix right away.

EOF

  local stop_file="$HOME/done_with_consistency_check.md"
  if [[ -f "${stop_file}" ]]; then
    rm "${stop_file:?}"
  fi

  for i in {1..1} ; do
      if [[ -f "${stop_file:?}" ]]; then
        echo.green "${stop_file:?} file exists. Exiting."
      	exit 1
      fi

      git.save

      cdi /home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1

      ai_core --agent CC --model opus --print "Read and execute instructions in [${tmp_file:?}]"

      git.save
  done
}

main "${@}"
