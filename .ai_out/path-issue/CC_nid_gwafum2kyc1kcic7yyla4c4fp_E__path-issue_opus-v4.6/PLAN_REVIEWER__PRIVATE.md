# Plan Reviewer Private Context

## Review Summary

Plan is sound. Approved with minor revisions (simplification, not redesign).

## Key Findings from Code Analysis

### Command Structure Verification

The `ClaudeCodeAdapter.buildStartCommand()` produces:
```
bash -c '<glmPrefix>cd <workdir> && unset CLAUDECODE && export TICKET_SHEPHERD_HANDSHAKE_GUID=<guid> && export TICKET_SHEPHERD_SERVER_PORT=<port> && export PATH=$PATH:<dir> && claude --model <model> --tools <tools> --system-prompt-file <path> --dangerously-skip-permissions "<escaped bootstrap msg>"'
```

Key observations:
1. `escapeForBashC` only replaces `'` with `'\''` -- no other characters affected.
2. `claude --model` is always the first token of the claude command (line 98-101 of adapter).
3. The bootstrap message is shell-quoted with `shellQuote()` which wraps in double quotes and escapes `$`, backticks, `\`, `"`, `!`.
4. The bootstrap message with GUID is INSIDE the single-quoted `bash -c` wrapper.

### Replacement Strategy Correctness

Finding `claude --model` in the command string and replacing through to the closing `'` is safe because:
- `claude --model` contains no characters that `escapeForBashC` would modify.
- The `shellQuote`'d bootstrap message may contain escaped single quotes from `escapeForBashC` if the message had any, but that doesn't affect finding `claude --model`.
- The closing `'` of `bash -c '...'` is the last character of the command string.

### SharedContextDescribeSpec Coupling

The `SharedContextIntegFactory` singleton is initialized eagerly at class-load time. It calls `ContextInitializer.forIntegTest()` which:
1. Reads `MY_ENV` env var
2. Reads ZAI API key from `$MY_ENV/.secrets/Z_AI_GLM_API_TOKEN`
3. Creates `GlmConfig.standard(authToken = zaiApiKey)`
4. Creates `TmuxSessionManager` with real `TmuxCommandRunner`
5. Creates `ClaudeCodeAdapter` with sentinel port/scripts dir

This means any test extending `SharedContextDescribeSpec` requires the ZAI env var setup, even if it doesn't use GLM. This is the existing pattern for ALL tmux integ tests (`TmuxSessionManagerIntegTest`, `TmuxCommunicatorIntegTest`). Changing it is out of scope.

### CallbackScriptsDir Resolution

The `IntegTestHelpers.resolveCallbackScriptsDir()` resolves to `app/src/main/resources/scripts/` and calls `CallbackScriptsDir.validated()`. This is the correct approach for this test -- it uses the REAL scripts directory, not the sentinel from `SharedContextIntegFactory`.

The adapter from `shepherdContext.infra.claudeCode.agentTypeAdapter` uses the SENTINEL scripts dir (`/unused-integ-test-sentinel`). The test creates its OWN adapter with the real scripts dir. This is correct.

### Risk Assessment

1. **Tmux session name collision**: Mitigated by timestamp suffix.
2. **Temp file not created (test hangs)**: Mitigated by `AsgardAwaitility` timeout.
3. **Cleanup failure**: Handled by try/catch in afterEach, matching existing pattern.
4. **Command structure change**: If adapter changes command format, `claude --model` search may fail. This is actually GOOD -- it would surface the change during testing.

## Files Read During Review

- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/.ai_out/path-issue/CC_nid_gwafum2kyc1kcic7yyla4c4fp_E__path-issue_opus-v4.6/DETAILED_PLANNING__PUBLIC.md`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/.ai_out/path-issue/CC_nid_gwafum2kyc1kcic7yyla4c4fp_E__path-issue_opus-v4.6/EXPLORATION_PUBLIC.md`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/.ai_out/path-issue/CC_nid_gwafum2kyc1kcic7yyla4c4fp_E__path-issue_opus-v4.6/CLARIFICATION__PUBLIC.md`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapter.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextDescribeSpec.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSessionManager.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapterTest.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/AgentTypeAdapter.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/agent/data/TmuxStartCommand.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/IntegTestHelpers.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/CallbackScriptsDir.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/bucket/TmuxCommunicatorIntegTest.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/bucket/TmuxSessionManagerIntegTest.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/bucket/integTestSupport.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextIntegFactory.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/data/ShepherdContext.kt`
