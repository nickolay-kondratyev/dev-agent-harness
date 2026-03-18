#!/usr/bin/env bash
# __enable_bash_strict_mode__

main() {
  ei2 is_in_docker
  cdi.repo_root
  file_verify_exists "./ticket_shepherd_marker.txt"

# shellcheck disable=SC2155
local tmp_file="$(ei2 make_tmp_file)"
cat >> "${tmp_file:?}" <<EOF
    READ specifications under FOLDER=[./doc].

    GOAL: Create tickets for deeper design & implementation that needs to happen.

    APPROACH:
      Take ONE spec that hasn't been processed yet (doesn't have ticket coverage reference from it), and create **tickets** for that specification. Slice the tickets so that each ticket can fit into a single 200K context of an agent without requiring compaction. If there is PLANNING to be done for that ticket with deeper design then create a separate ticket for planning and make the implementation tickets a dependency of the planning ticket. We do NOT have to reference all the tickets from the specs, (Example if there are multiple tickets sliced for a single line of the spec only reference one relevant ticket).

      Slice the implementation tickets also so that each can fully fit into 200K context without triggering compaction. Use **dependency** addition=[ticket dep <id> <dep-id>] between tickets to create a hierarchy of implementation.

      Focus on addition of tickets for just one spec. Separate iteration will create tickets for another spec.

      Review the tickets created to make sure the split makes the most sense.

      IF there is no more tickets to be created write [$HOME/done_with_ticket_creation.md] marker file.
EOF

  local stop_file="$HOME/done_with_ticket_creation.md"
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
