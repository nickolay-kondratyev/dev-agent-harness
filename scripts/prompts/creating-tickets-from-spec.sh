#!/usr/bin/env bash
# __enable_bash_strict_mode__

_make_instructions(){
  local -r spec_to_process="$(rgfiles "#need-tickets" ./doc/ | grep_format.file | head -n 1| path | echom)"
  file_verify_exists "${spec_to_process:?}" </dev/null

  # shellcheck disable=SC2155
local tmp_file="$(ei2 make_tmp_file)"
cat >> "${tmp_file:?}" <<EOF
  READ the high level design=[./doc/high-level.md].
  READ the spec_to_process=[${spec_to_process}]
  READ any other specifications from [./doc] to get better understanding of the context.
  READ any necessary relevant code to the spec_to_process=[${spec_to_process}]

  **GOAL**: Create tickets for deeper design & implementation that needs to happen for spec_to_process=[${spec_to_process}].

  APPROACH:
    Take ONE spec that hasn't been processed yet SPEC to process=[${spec_to_process}], and create **tickets** for that specification. Slice the tickets so that each ticket can fit into a single 200K context of an agent without requiring compaction. If there is PLANNING to be done for that ticket with deeper design, create a separate planning ticket and make each implementation ticket depend on the planning ticket: [ticket dep <impl-id> <planning-id>] — so planning is completed before implementation begins. We do NOT have to reference all the tickets from the specs, (Example if there are multiple tickets sliced for a single line of the spec only reference one relevant ticket).

    Slice the implementation tickets also so that each can fully fit into 200K context without triggering compaction. Use **dependency** addition=[ticket dep <id> <dep-id>] between tickets to create a hierarchy of implementation.

    Focus on addition of tickets for just one spec. Separate iteration will create tickets for another spec.

    Review the tickets created to make sure the split makes the most sense.

    Make sure to remove #need-tickets tag after you have added tickets from the spec_to_process.

  IN **sub-agent** REVIEW the tickets that were created.
  THEN address the sub-agents feedback.
EOF

echo "${tmp_file:?}"
}

main() {
  ei2 is_in_docker
  cdi.repo_root
  file_verify_exists "./ticket_shepherd_marker.txt"

  #need-tickets
  local tmp_file="$(ei2 _make_instructions)"

  for i in {1..20} ; do
      cdi.repo_root
      git.save

      ai_core --agent CC --model opus --effort medium "Read and execute instructions in [${tmp_file:?}]"

      git.save
  done
}

main "${@}"
