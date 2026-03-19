# Implementation Review: Add Kover for Code Coverage

## Summary

The implementation adds JetBrains Kover code coverage plugin to the project with an XML report output to `.out/coverage.xml` and a `coverage.sh` shell wrapper. The change is additive only -- no existing tests or source files were modified or removed. All existing tests pass. The coverage report generates correctly (374KB XML file in JaCoCo-compatible format).

The XML-instead-of-JSON deviation is well justified and properly documented -- Kover genuinely does not support JSON output, and XML is the standard machine-readable format with full JaCoCo tooling compatibility.

Overall assessment: **Approve with one important issue to address.**

## CRITICAL Issues

None.

## IMPORTANT Issues

### 1. Kover `koverVerify` is implicitly wired into `build` via `check` lifecycle

**Files:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/build.gradle.kts`

The requirement states: "Should execute as explicit task, NOT a dependency of other gradle tasks." However, applying the Kover plugin automatically registers `koverVerify` as a dependency of the `check` task (which is a dependency of `build`).

Evidence from `./gradlew :app:build --dry-run`:
```
:app:test SKIPPED
:app:koverGenerateArtifactJvm SKIPPED
:app:koverGenerateArtifact SKIPPED
:app:koverCachedVerify SKIPPED
:app:koverVerify SKIPPED
:app:check SKIPPED
:app:build SKIPPED
```

Currently this is a no-op because no verification rules are configured. But it violates the stated requirement, and if someone later adds verification rules (minimum coverage thresholds), `build` would start failing based on coverage -- a surprising side effect.

**Suggested fix** -- disable the default kover verification task from the `check` lifecycle:

```kotlin
// Prevent Kover from wiring into the check/build lifecycle.
// Coverage must be run explicitly via coverage.sh.
tasks.matching { it.name == "koverVerify" }.configureEach {
    enabled = false
}
```

Additionally, `koverFindJar` appears in the `test` task chain. This is the instrumentation agent and is harmless (just sets up the JaCoCo agent jar location), but it does mean that applying the Kover plugin slightly modifies the `test` task behavior by adding instrumentation. This is inherent to how coverage tools work and is acceptable -- just worth being aware of.

## Suggestions

### 1. Consider adding `--no-daemon` or `--info` output guidance

The `coverage.sh` script uses `tee .tmp/coverage.txt` which is good for debugging. However, unlike `test.sh`, it uses `tee` instead of plain redirect. This is a minor inconsistency -- `test.sh` uses `| tee .tmp/test.txt` as well, so actually this IS consistent. No action needed.

### 2. Kover version pinning

Kover `0.9.1` is recent and appropriate. No concern here.

## Documentation Updates Needed

None -- the `.ai_out` PUBLIC.md already documents the XML deviation clearly, and the inline comments in `build.gradle.kts` explain the design decisions well.
