#!/usr/bin/env bash
# __enable_bash_strict_mode__

main() {
    context_generation.templatize_entire_directory_to_another_dir \
      "${MY_ENV}"/config/chainsaw/agents/input \
      "${MY_ENV}"/config/chainsaw/agents/_generated
}

main "${@}"
