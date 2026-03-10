#!/usr/bin/env bash
# Pre-build preparation: ensures asgard libraries are present in maven local (~/.m2).
#
# Background: Gradle resolves dependencies at CONFIGURATION time, before any task runs.
# A `dependsOn ensureAsgardInMavenLocal` wiring in compileKotlin cannot self-heal a
# missing dependency — Gradle fails at configuration before the healing task can execute.
# This script must be called BEFORE invoking ./gradlew.
#
# ap.gtpABfFlF4RE1SITt7k1P.E
#
# DRY note: artifact names and ~/.m2 path pattern are duplicated from:
#   ref.ap.luMV9nN9bCUVxYfZkAVYR.E  (checkAsgardInMavenLocal in build.gradle.kts)
#   ref.ap.VZk3hR8tJmPcXqYsNvLbW.E  (ensureAsgardInMavenLocal in build.gradle.kts)
# Those Gradle tasks cannot share this logic — this must execute before Gradle starts.

# Repo root is this script's directory (survives being sourced from any working directory).
_PREPARE_PRE_BUILD_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Returns 0 if all required asgard artifacts are present in ~/.m2, 1 otherwise.
_asgard_all_present() {
  local m2_asgard="${HOME}/.m2/repository/com/asgard"
  [[ -d "${m2_asgard}/asgardCore/1.0.0" ]] && [[ -d "${m2_asgard}/asgardTestTools/1.0.0" ]]
}

# Ensures asgard libraries are present in maven local (~/.m2).
#
# Fast path (<1s): all artifacts already present — returns immediately (file-stat only).
# Slow path: any artifact missing — publishes via publishAsgardToMavenLocal.
# THORG_ROOT is set automatically; no manual export needed.
#
# ref.ap.gtpABfFlF4RE1SITt7k1P.E
_prepare_asgard_dependencies() {
  if _asgard_all_present; then
    return 0
  fi

  echo "asgard_status=[missing] — auto-publishing to maven local..."

  # Subshell: cd + env var scoped to just this publish invocation.
  (
    cd "${_PREPARE_PRE_BUILD_REPO_ROOT}"
    THORG_ROOT="${HOME}/thorg-root" \
      ./gradlew publishAsgardToMavenLocal
  )

  echo "asgard_status=[published]"
}

# Run when executed directly (not sourced).
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  set -euo pipefail
  _prepare_asgard_dependencies
fi
