# Clarification: Add Kover for Code Coverage

## Requirements (Clear, No Ambiguity)
1. Add Kover Gradle plugin for Kotlin code coverage
2. JSON report output to `./.out/` directory (repo root)
3. Explicit Gradle task — NOT a dependency of other tasks (test, build, etc.)
4. `coverage.sh` shell wrapper at repo root to run the coverage task

## Decisions Made
- Kover version: Use latest stable (0.9.x series for Kotlin 2.x compatibility)
- Report location: `./.out/` from repo root
- Task independence: No `dependsOn` wiring to test/build tasks
- The coverage task will implicitly run tests (Kover needs test execution to measure coverage)
