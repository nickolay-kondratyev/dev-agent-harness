# Align Coverage With Sonar - Implementation

## What Was Done

Moved the `sonar.coverage.jacoco.xmlReportPaths` property from the root-level `sonar {}` block to a
`project(":app") { sonar { properties { ... } } }` block in `build.gradle.kts`.

### Root Cause

The Sonar plugin was configured with the JaCoCo coverage report path at the **root project** level.
Since the root project has no source files, Sonar could not match coverage entries (which reference
`:app` source files) against any indexed sources, resulting in "File not found in project sources"
warnings and zero coverage reported.

### Fix

By setting `sonar.coverage.jacoco.xmlReportPaths` on the `:app` subproject instead, coverage data
is correctly associated with the module that owns the source files.

## Files Modified

- `build.gradle.kts` (root) -- Moved coverage report path from root sonar block to `project(":app")` sonar block.

## Tests

- `:app:test` -- All tests pass (exit code 0).
