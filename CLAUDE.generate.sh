#!/usr/bin/env bash
# __enable_bash_strict_mode__

main() {
  eai2 context_generation.claude_md_generate_v2 ./CLAUDE.md
  eai2 agent_md.create_agents_md_links_to_claude_md_for_dir .
}

main "${@}"
