# Plan Reviewer -- Private Notes

## Review Session: 2026-03-20

### Key Investigation Points

1. **GLM env var propagation chain**: Verified by reading `ClaudeCodeAdapter.buildStartCommand()` (line 131). When `glmConfig` is null, the `glmPrefix` is empty string -- no GLM exports in the bash command. But the `bash -c` command runs inside tmux which inherits the binary's process environment. Since `ANTHROPIC_BASE_URL` etc. are NOT unset anywhere in the command, they propagate through. The plan's approach is correct.

2. **ContextInitializer.standard() vs forIntegTest()**: Line 73 vs 83. Standard mode has `glmEnabled = false`. The binary uses `standard()`. The E2E test cannot change this -- it runs the binary as-is. The env var inheritance approach is the only viable path without modifying production code.

3. **EnvironmentValidator.standard()**: Checks `/.dockerenv` and required env vars. This means the E2E test is Docker-only. No way around it without modifying production code.

4. **test_with_integ.sh** exists and runs `./gradlew :app:test -PrunIntegTests=true`. It does NOT run `installDist`. This needs to be added for the E2E test to work.

5. **iteration-max behavior**: Need to verify if CLI `--iteration-max` overrides or is overridden by workflow JSON `"iteration": {"max": 4}`. This is a minor concern -- the test will either work or fail clearly.

### Decision: APPROVED WITH MINOR REVISIONS

The plan is thorough and the core approach is sound. The inline revisions are minor enough that they don't warrant a full plan iteration cycle. The implementer can apply them directly.

### Files Examined
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapter.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/EnvironmentValidator.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextIntegFactory.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextDescribeSpec.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/config/workflows/straightforward.json`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/test_with_integ.sh`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/run.sh`
