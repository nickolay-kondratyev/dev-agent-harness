# Plan Review: E2E Integration Test for Straightforward Workflow

## Executive Summary

The plan is well-researched and demonstrates a thorough understanding of the binary's startup sequence, GLM configuration, and existing integration test patterns. The core approach -- running the binary as a subprocess via ProcessBuilder with GLM env vars inherited through tmux -- is sound. However, there is one critical issue with GLM env var propagation that needs resolution, and several simplification opportunities. **APPROVED WITH MINOR REVISIONS** -- the revisions are straightforward and can be made inline during implementation.

## Critical Issues (BLOCKERS)

### 1. GLM Environment Variable Propagation is Fragile and Undocumented

- **Issue:** The plan relies on GLM env vars (`ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN`, model aliases) being inherited by the `claude` CLI through the chain: ProcessBuilder env -> binary process -> tmux session -> `bash -c` -> `claude`. This WORKS because `ClaudeCodeAdapter.buildStartCommand()` does NOT unset these vars. However, this is an **implicit contract** -- if anyone adds `unset ANTHROPIC_BASE_URL` to the command template in the future, this E2E test silently starts using the real Anthropic API.

- **Impact:** Silent cost increase if the implicit contract is broken. Also, the binary's `ContextInitializer.standard()` creates the adapter with `glmConfig = null`, so no explicit GLM exports are added to the bash command. The test relies entirely on environment inheritance.

- **Recommendation:** This is acceptable for V1 -- the plan already documents this clearly in section 2a. Add a code comment in the test explicitly noting this dependency. Consider a follow-up ticket to add a `--glm` flag to the binary CLI for testability.

**Verdict: NOT a blocker.** The approach works today and is pragmatic. The plan's analysis in section 2a is correct.

## Major Concerns

### 1. Temp Git Repo Needs More Scaffolding Than Documented

- **Concern:** The plan mentions copying `config/workflows/straightforward.json` to the temp dir (section 4c), but does NOT mention the agent role definitions. `TICKET_SHEPHERD_AGENTS_DIR` env var points to a directory with role `.md` files (`IMPLEMENTATION_WITH_SELF_PLAN.md`, `IMPLEMENTATION_REVIEWER.md`). The plan says to "read from current environment" (section 3, step 5) -- but if the agents dir is a relative path, it will not resolve from the temp dir's context. Similarly, the callback scripts dir is resolved by `ContextInitializer` from classpath resources.

- **Why:** The binary resolves agents dir from the env var, and the scripts dir from classpath. The agents dir is typically an absolute path set in the dev environment. This should work, but the plan should explicitly verify.

- **Suggestion:** The plan should explicitly state that `TICKET_SHEPHERD_AGENTS_DIR` must be an absolute path (verify from actual env), and that callback scripts are embedded in the binary's classpath (no action needed for scripts).

### 2. Missing `CLAUDE.md` Content for the Temp Repo

- **Concern:** The plan mentions creating a `CLAUDE.md` in step 6 of Phase 2 but does not specify its content. The agent will read this file to understand what to do. If it is empty or nonsensical, the GLM agent may produce garbage output.

- **Why:** The hello-world task is simple enough that even a minimal `CLAUDE.md` should work. But having NO `CLAUDE.md` might cause the agent to behave unpredictably.

- **Suggestion:** Create a minimal `CLAUDE.md` with: "You are in a test repository. Follow the ticket instructions exactly."

### 3. The `--iteration-max 1` Discrepancy

- **Concern:** The CLARIFICATION doc says `--iteration-max 1`, but the workflow JSON has `"iteration": { "max": 4 }` for the reviewer. The plan in section 4 Phase 4 uses `--iteration-max 1`. Need to confirm: does `--iteration-max` override the workflow JSON's iteration max? If so, this means only 1 iteration attempt -- the reviewer must PASS on the first try, or the test fails with `FailedToConverge`.

- **Why:** With GLM, the reviewer might not PASS on the first attempt. Setting `--iteration-max 1` is aggressive.

- **Suggestion:** Verify that the CLI `--iteration-max` is actually the default budget (overridable by workflow JSON), or a hard cap. If it is a hard cap, consider using `--iteration-max 2` to give one retry. If the workflow JSON's `"max": 4` takes precedence, then `--iteration-max 1` is fine as a fallback.

## Simplification Opportunities (PARETO)

### 1. Tmux Cleanup: Just Record and Kill Delta

- **Current approach:** The plan proposes listing tmux sessions before/after and killing the delta (section 6).
- **Simpler alternative:** Since the binary itself kills sessions on successful exit (`allSessionsKiller.killAllSessions()`), the cleanup only matters on failure. On failure, a simple `tmux kill-server` in an `afterSpec` is safe for CI/Docker. For local dev, the before/after delta approach is better.
- **Value:** Less code. The test only runs in Docker anyway. A comment noting this tradeoff is sufficient.

### 2. Binary Build Check: Option B is Correct

- The plan recommends Option B (fail hard if binary not found). This is the right call. `test_with_integ.sh` already exists and can be updated to include `installDist`. No Gradle task dependency needed.
- **Action:** Add `./gradlew :app:installDist` to `test_with_integ.sh` before running tests. The plan should state this explicitly as a required change.

### 3. Assertions: Keep it Minimal

- **Current approach:** 4 assertions (exit code, git branch, hello-world.sh exists, .ai_out/ exists).
- **Simpler:** Exit code 0 is the primary assertion -- it proves the entire workflow completed successfully (doer done, reviewer PASS, git commit, clean exit). The other assertions are nice-to-have but add maintenance burden for an inherently non-deterministic test.
- **Recommendation:** Keep exit code 0 as the primary `it` block. Add `.ai_out/ exists` as a secondary. Drop `hello-world.sh exists` and `git branch created` -- these are implementation details of the agent, not of the harness. The harness test should test the harness, not the agent's coding ability.

## Minor Suggestions

1. **Port variable naming:** The plan sets `TICKET_SHEPHERD_SERVER_PORT` as an env var on the subprocess. Verify this is read by `ContextInitializerImpl.readServerPort()` via `System.getenv()`. It should be, since ProcessBuilder env becomes the subprocess's `System.getenv()` environment. Correct.

2. **Timeout:** 10 minutes is generous. Consider 15 minutes -- GLM can be very slow, and the doer + reviewer flow involves two agent sessions.

3. **Test file location:** `app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/` is a good choice. The `e2e` sub-package clearly distinguishes from component-level integ tests.

4. **Stdout/stderr redirect:** Good decision to redirect to files. Consider also capturing these in the assertion failure message (on test failure, read the file and include in the error message).

## Strengths

1. **Thorough GLM analysis (section 2a):** The plan correctly identifies that `ContextInitializer.standard()` does NOT inject GLM, and correctly concludes that environment inheritance via ProcessBuilder -> tmux -> bash -c will work. This is the most nuanced part of the plan and it is handled well.

2. **Correct decision to NOT use SharedContextDescribeSpec:** The E2E test runs the binary externally, so it does not need internal ShepherdContext wiring. Using `AsgardDescribeSpec` directly is correct.

3. **Risk mitigation table (section 9):** Comprehensive and realistic. All major risks are identified with practical mitigations.

4. **BDD test structure (section 5):** Clean, follows project standards, one assert per `it` block.

5. **Docker requirement acknowledgment:** The plan correctly identifies that this test can only run in Docker and does not try to work around it.

## Inline Revisions (can be applied during implementation)

1. Add `./gradlew :app:installDist` to `test_with_integ.sh` before the `./gradlew :app:test -PrunIntegTests=true` line.
2. Reduce assertions to: exit code 0 (primary), `.ai_out/` exists (secondary). Drop agent-output-specific assertions.
3. Use 15-minute timeout instead of 10.
4. Create minimal `CLAUDE.md` in temp repo: "You are in a test repository. Follow the ticket instructions exactly."

## Verdict

- [ ] APPROVED
- [x] APPROVED WITH MINOR REVISIONS
- [ ] NEEDS REVISION
- [ ] REJECTED

The plan is solid, well-researched, and pragmatic. The minor revisions above can be applied inline during implementation without requiring a plan iteration cycle. **PLAN_ITERATION can be skipped** -- proceed to implementation with the inline adjustments noted above.
