# Implementation: Add Kover for Code Coverage

## What Was Done
Added JetBrains Kover code coverage plugin to the project with XML report output to `.out/coverage.xml` at repo root.

## Deviation From Requirements
The task specified **JSON** report format, but Kover does not support JSON reports. Kover supports: XML, HTML, binary (IC format), and log output. **XML was chosen** as the machine-readable alternative — it contains the same coverage data (line, branch, instruction counters) in JaCoCo-compatible XML format.

## Files Modified

### `gradle/libs.versions.toml`
- Added `kover = "0.9.1"` to `[versions]`
- Added `kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }` to `[plugins]`

### `app/build.gradle.kts`
- Applied Kover plugin via `alias(libs.plugins.kover)` in plugins block
- Added `kover { reports { total { xml { ... } } } }` configuration block
- Report output set to `${rootDir}/.out/coverage.xml`
- Kover is NOT wired as dependency of any other task

### `coverage.sh` (new)
- Shell wrapper following `test.sh` pattern
- Sources `_prepare_pre_build.sh` for asgard dependency self-healing
- Runs `./gradlew :app:koverXmlReport`

## Verification
- Kover tasks registered successfully (12 tasks including `koverXmlReport`, `koverHtmlReport`, etc.)
- Existing tests pass unchanged
- Coverage report generated at `.out/coverage.xml` (374KB)
- `.out/` directory already in `.gitignore`
