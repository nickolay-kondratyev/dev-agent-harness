# Planner Private Notes

## Key Decisions Made

1. **NOT extending SharedContextDescribeSpec** -- the binary manages its own context. Test is a black-box subprocess test.
2. **GLM via env inheritance** -- setting ANTHROPIC_BASE_URL etc. on ProcessBuilder so tmux-spawned claude CLI inherits them.
3. **Docker requirement accepted** -- EnvironmentValidator checks /.dockerenv. Test documents this.
4. **Workflow file must be copied to temp dir** -- binary resolves it relative to CWD.
5. **TICKET_SHEPHERD_AGENTS_DIR from current env** -- reuse real agent role definitions.

## Files Read During Planning

- `app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt` (entry point, picocli CLI)
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializer.kt` (startup sequence)
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` (context init, GLM config)
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/EnvironmentValidator.kt` (Docker + env var checks)
- `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherd.kt` (workflow driver, System.exit)
- `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt` (ticket validation, git setup)
- `app/src/main/kotlin/com/glassthought/shepherd/core/Constants.kt` (required env vars, ports)
- `app/src/main/kotlin/com/glassthought/shepherd/core/infra/ProcessExiter.kt` (System.exit abstraction)
- `config/workflows/straightforward.json` (workflow definition)
- All existing integ tests in `app/src/test/kotlin/com/glassthought/shepherd/integtest/`
- `ai_input/memory/deep/integ_tests__use_glm_for_agent_spawning.md` (GLM mechanism)
- `run.sh` (binary invocation pattern)

## Complexity Assessment

Medium complexity. The main challenge is environment setup (env vars, git repo, Docker), not the test logic itself. The ProcessBuilder invocation is straightforward. The verification is simple (exit code + file existence).

## What I considered but rejected

1. **In-process test with mocked ProcessExiter**: Would test more of the internals but defeats the purpose of E2E testing the actual binary.
2. **Using SharedContextDescribeSpec**: Not needed since the binary manages its own context.
3. **Running installDist as Gradle task dependency**: Would slow all test runs including unit tests.
