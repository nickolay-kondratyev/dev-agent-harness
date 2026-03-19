#!/usr/bin/env bash
# __enable_bash_strict_mode__

_make_instructions(){
  local -r spec_to_process="$(rgfiles "#need-tickets" ./doc/ | grep_format.file | head -n 5 | path | echom)"
  # file_verify_exists "${spec_to_process:?}" </dev/null

  # shellcheck disable=SC2155
local tmp_file="$(ei2 make_tmp_file)"
cat >> "${tmp_file:?}" <<EOF
    READ the high level design=[./doc/high-level.md].
    READ the specs_to_process=[${spec_to_process}]
    READ any other specifications from [./doc] to get better understanding of the context.
    READ any necessary relevant code to the specs_to_process=[${spec_to_process}]

    **GOAL**: Create tickets for deeper design & implementation that needs to happen for each spec in specs_to_process=[${spec_to_process}].

    APPROACH:
      Create one SUB-AGENT per spec in specs_to_process=[${spec_to_process}]. Each sub-agent processes a single spec and creates **tickets** for it. Slice the tickets so that each ticket can fit into a single 200K context of an agent without requiring compaction. If there is PLANNING to be done for a ticket with deeper design, create a separate planning ticket and make each implementation ticket depend on the planning ticket: [ticket dep <impl-id> <planning-id>] — so planning is completed before implementation begins. We do NOT have to reference all the tickets from the specs (example: if multiple tickets are sliced for a single line of the spec, only reference one relevant ticket).

      Slice the implementation tickets so that each can fully fit into 200K context without triggering compaction. Use **dependency** addition=[ticket dep <id> <dep-id>] between tickets to create a hierarchy of implementation.

      Each sub-agent focuses on tickets for its assigned spec only. Separate sub-agents handle separate specs.

      After all sub-agents complete, review the full set of tickets created across all specs to make sure the splits and dependencies make sense holistically.

      Make sure to remove #need-tickets tag from each spec after its tickets have been added.

    IN each **sub-agent** REVIEW the tickets that were created for that spec.
    THEN address each sub-agent's feedback before proceeding to the next spec.
EOF

echo "${tmp_file:?}"
}

main() {
  ei2 is_in_docker
  cdi.repo_root
  file_verify_exists "./ticket_shepherd_marker.txt"

 # for i in {1..20} ; do
      cdi.repo_root
      #need-tickets
      local tmp_file="$(ei2 _make_instructions)"

      git.save


      ai_core --agent CC --model opus --effort medium "Read and execute instructions in [${tmp_file:?}]"

      git.save
 # done
}

main "${@}"
