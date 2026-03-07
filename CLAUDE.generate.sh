#!/usr/bin/env bash
# __enable_bash_strict_mode__

main() {
  eai2 context_generation.claude_md_generate_v2 ./CLAUDE.md
}

main "${@}"
