#!/usr/bin/env bash
# __enable_bash_strict_mode__

main() {
  ./gradlew :app:installDist
  # Runs the main function at ref.ap.4JVSSyLwZXop6hWiJNYevFQX.E
  ./app/build/install/app/bin/app
}

main "${@}"
