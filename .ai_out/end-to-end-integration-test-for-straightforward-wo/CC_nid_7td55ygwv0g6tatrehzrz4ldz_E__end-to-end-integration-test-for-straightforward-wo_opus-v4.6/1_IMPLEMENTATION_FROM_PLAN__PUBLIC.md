# Implementation: E2E Integration Test for Straightforward Workflow

## Summary

Created an end-to-end black-box integration test that runs the actual `app` binary as a subprocess via `ProcessBuilder`, exercises the full straightforward workflow (doer + reviewer), and verifies harness behavior.

## Files Changed

### Created
- `app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/StraightforwardWorkflowE2EIntegTest.kt`
  - Black-box subprocess test extending `AsgardDescribeSpec`
  - Gated with `isIntegTestEnabled()` and `@OptIn(ExperimentalKotest::class)`
  - Creates a temporary git repo with ticket, CLAUDE.md, and workflow config
  - Sets GLM env vars on ProcessBuilder for cost-free agent execution via Z.AI
  - 15-minute timeout for doer + reviewer GLM sessions
  - Assertions: exit code 0, `.ai_out/` exists, feature branch created
  - Delta-based tmux session cleanup in `afterSpec`
  - Diagnostic log capture (stdout/stderr) on failure

### Modified
- `test_with_integ.sh`
  - Added `./gradlew :app:installDist` before test execution so the binary is built

## Key Decisions

1. **Extends `AsgardDescribeSpec` (NOT `SharedContextDescribeSpec`)**: This test runs the binary externally as a subprocess. It does not need internal `ShepherdContext` wiring.

2. **GLM via environment inheritance**: Set `ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN`, and model alias env vars on the subprocess. These propagate through: ProcessBuilder -> binary process -> tmux -> `claude` CLI. This avoids modifying production code.

3. **Assertions focus on harness behavior**: Exit code 0, `.ai_out/` structure, and feature branch creation. No assertions on agent coding quality (e.g., hello-world.sh content) per reviewer guidance.

4. **15-minute timeout**: Accommodates two GLM agent sessions (doer + reviewer).

5. **Fail hard if binary not built**: `require(Files.exists(binaryPath))` at test start with actionable error message.

## Reviewer Adjustments Incorporated

- [x] Added `./gradlew :app:installDist` to `test_with_integ.sh`
- [x] Assertions focus on harness behavior, not agent output quality
- [x] 15-minute timeout
- [x] Minimal CLAUDE.md in temp repo

## Verification

- `./gradlew :app:test` passes (unit tests, integ tests skipped)
- Test class compiles and is discoverable by Kotest runner
