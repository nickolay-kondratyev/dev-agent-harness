# Exploration: Add Kover for Code Coverage

## Project Structure
- Single-module Gradle project (root + `app/` subproject)
- Kotlin 2.2.20, Java 21, Gradle 9.2.1
- Version catalog at `gradle/libs.versions.toml`

## Current Build Setup
- **Root build.gradle.kts**: SonarQube plugin, custom asgard publishing tasks
- **App build.gradle.kts**: Kotlin JVM, Application, Detekt plugins
- Plugins applied via `alias(libs.plugins.NAME)` pattern
- No existing code coverage tool

## Key Patterns
- Plugins declared in version catalog `[plugins]` section
- Detekt config example: baseline file, config file, parallel execution
- Test task wiring: `tasks.named("test") { dependsOn(...) }`
- **IMPORTANT**: Kover task must NOT be wired as dependency of test or other tasks

## Relevant Files
- `gradle/libs.versions.toml` — add kover plugin entry
- `app/build.gradle.kts` — apply plugin, configure report output
- `coverage.sh` — new shell wrapper (root level)

## Output Directory
- Ticket says `./.out/` directory — this is likely `app/.out/` (app module working dir)
- Actually the ticket says `./.out/` which from repo root means `${repoRoot}/.out/`
