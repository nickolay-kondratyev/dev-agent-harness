# Exploration: NonInteractiveAgentRunner Implementation

## Spec Location
- `doc/core/NonInteractiveAgentRunner.md` (ref.ap.ad4vG4G2xMPiMHRreoYVr.E)

## Key Findings

### ProcessRunner API (from asgardCore-jvm)
- **Interface**: `com.asgard.core.processRunner.ProcessRunner`
- **`runProcessV2(timeout, vararg input)`**: Returns `ProcessResult(stdout, stderr, exitCode, executionTimeMs)`
  - Throws `ProcessCommandFailedException(message, result)` on non-zero exit — `result.exitCode` and `result.stdout`/`result.stderr` available
  - Throws `ProcessCommandTimeoutException(message, partialResult)` on timeout — `partialResult.stdout`/`partialResult.stderr` available
  - **No working directory support** — ProcessBuilder directory not set
  - **No env var support** — inherits current process env only
- **`runProcess(vararg input)`**: Returns stdout as String, throws RuntimeException on non-zero. No timeout.

### Working Directory + Env Var Strategy
Since ProcessRunner doesn't support working dir / env vars, the implementation should wrap commands via `bash -c "cd /path && [export ZAI_API_KEY=...&&] command"`.

### AgentType Enum
- Location: `app/src/main/kotlin/com/glassthought/shepherd/core/data/AgentType.kt`
- Values: `CLAUDE_CODE`, `PI`

### Package Target
- Main: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/noninteractive/`
- Test: `app/src/test/kotlin/com/glassthought/shepherd/core/agent/noninteractive/`

### Test Patterns
- Extend `AsgardDescribeSpec` with `AsgardDescribeSpecConfig`
- BDD: nested `describe` for GIVEN/WHEN, `it` for THEN
- One assertion per `it` block
- Use `shouldBe`, `shouldContain`, `shouldThrow<>` from Kotest
- `outFactory` inherited from `AsgardDescribeSpec`

### Logging Patterns
- `private val out = outFactory.getOutForClass(MyImpl::class)`
- `out.info("snake_case_message", Val(value, ValType.TYPE))`
- All `Out` methods are suspend

### Existing Agent Package Structure
```
core/agent/
  adapter/       — ClaudeCodeAdapter, PiAdapter
  contextwindow/ — ContextWindowStateReader
  facade/        — AgentFacade, FakeAgentFacade
  tmux/          — TmuxSessionManager, etc.
  noninteractive/ — NEW: NonInteractiveAgentRunner
```
