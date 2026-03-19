# Implementation Review: Align Coverage with Sonar

## Summary

Single-line property move in `build.gradle.kts`: `sonar.coverage.jacoco.xmlReportPaths` relocated from the root-level `sonar {}` block to a `project(":app") { sonar { ... } }` block.

**Verdict: APPROVE.** The change is correct, minimal, and addresses the root cause.

## Analysis

### Does this fix the root cause?

Yes. The Sonar Gradle plugin processes each Gradle module independently. When `sonar.coverage.jacoco.xmlReportPaths` was set on the root project, Sonar tried to match coverage entries (which reference `:app` source files like `com/glassthought/shepherd/cli/AppMain.kt`) against the root project's indexed sources -- which are empty. Moving the property to `project(":app")` associates the coverage report with the module that actually owns the source files.

### Is it correct Sonar Gradle plugin API usage?

Yes. The `project(":app") { sonar { properties { ... } } }` pattern is the documented way to set per-module Sonar properties from the root `build.gradle.kts` in multi-module builds. The SonarQube Gradle plugin applies a `SonarExtension` to every project, and configuring it via `project(":app")` is standard.

### Any unintended side effects?

None identified:
- The root-level `sonar {}` block retains all global properties (host, org, project key, token, exclusions).
- The `tasks.named("sonar") { dependsOn(":app:koverXmlReport") }` wiring is unchanged.
- No other properties were added or removed.
- All tests pass (`./gradlew :app:test` exit code 0, `./sanity_check.sh` exit code 0).

### Correctness of the coverage path

`${rootDir}/.out/coverage.xml` is resolved at Gradle configuration time to an absolute path. This is correct regardless of whether the property is on the root or `:app` project, since `rootDir` is the same in both contexts.

## Issues

No CRITICAL, IMPORTANT, or suggestion-level issues found. This is a clean, well-scoped fix.

## Documentation Updates Needed

None. The inline comments on the new `project(":app")` block adequately explain the WHY.
