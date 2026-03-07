#!/usr/bin/env bash
# __enable_bash_strict_mode__

main() {
  ./gradlew :app:run
}

main "${@}"
