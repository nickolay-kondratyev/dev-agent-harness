# Implementation Review - Private Notes

## Review Context

Reviewing the E2E integration test for the straightforward workflow. The change adds:
1. `StraightforwardWorkflowE2EIntegTest.kt` - a black-box subprocess test
2. Modification to `test_with_integ.sh` to add `installDist`

## Key Findings

### Critical Analysis

1. **`--iteration-max 1` vs workflow JSON `max: 4`**: The CLI param `iterationMax` is parsed but NOT consumed downstream (see `ShepherdInitializer.kt` lines 68-70). The workflow JSON's `"iteration": { "max": 4 }` will be used. So `--iteration-max 1` is effectively a no-op. This means the test will run up to 4 reviewer iterations, which is acceptable but the test documentation is misleading.

2. **GLM env inheritance**: Verified that `bash -c` inside tmux inherits the parent env. `ClaudeCodeAdapter.buildStartCommand()` does `unset CLAUDECODE` but does NOT unset ANTHROPIC_BASE_URL/ANTHROPIC_AUTH_TOKEN. Chain is sound.

3. **Docker requirement**: `EnvironmentValidator.standard().validate()` checks `/.dockerenv`. The test can only run in Docker. This is consistent with being gated by `isIntegTestEnabled()`.

4. **Environment clearing**: `processBuilder.environment().clear()` then `putAll(subprocessEnv)` - this is explicit and good, but could miss env vars the binary needs. Checked: PATH, HOME, JAVA_HOME, TERM, XDG dirs are covered.

### Pattern Consistency Check

- Uses `AsgardDescribeSpec` (not `SharedContextDescribeSpec`) - correct since it's a black-box subprocess test
- Uses `@OptIn(ExperimentalKotest::class)` - correct
- Uses `.config(isIntegTestEnabled())` on describe block - correct
- Uses `AsgardDescribeSpecConfig.FOR_INTEG_TEST` - correct
- BDD style with GIVEN/WHEN/THEN - correct
- One assert per `it` block - correct

### Potential Issues

1. `listTmuxSessions()` swallows ALL exceptions silently - could mask real issues
2. Temp dir created in system temp, not `.tmp/` as per CLAUDE.md convention
3. Free-floating function `listTmuxSessions()` violates "no free-floating functions" rule
4. `runGitInTemp` discards all output including errors - if git init fails, debugging is hard
