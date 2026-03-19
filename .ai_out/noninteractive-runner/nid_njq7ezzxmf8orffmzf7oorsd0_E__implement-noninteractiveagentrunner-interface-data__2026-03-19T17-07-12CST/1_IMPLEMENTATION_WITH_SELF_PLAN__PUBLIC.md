# Implementation: NonInteractiveAgentRunner

## Status: COMPLETE

## Files Created

### Main Source
1. **`app/src/main/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunner.kt`**
   - `NonInteractiveAgentRunner` interface with `suspend fun run(request): NonInteractiveAgentResult`
   - `NonInteractiveAgentRequest` data class (instructions, workingDirectory, agentType, model, timeout)
   - `NonInteractiveAgentResult` sealed class: `Success(output)`, `Failed(exitCode, output)`, `TimedOut(output)`

2. **`app/src/main/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunnerImpl.kt`**
   - Constructor: `ProcessRunner`, `OutFactory`, `zaiApiKey: String`
   - Shell command construction via `bash -c "cd {workDir} && ..."` (ProcessRunner lacks working dir support)
   - CLAUDE_CODE: `claude --print --model {model} -p '{instructions}'`
   - PI: `export ZAI_API_KEY='{key}' && pi --provider zai --model {model} -p '{instructions}'`
   - Shell escaping via single-quote wrapping with `'\''` idiom for embedded quotes
   - Catches `ProcessCommandFailedException` -> `Failed`, `ProcessCommandTimeoutException` -> `TimedOut`
   - Structured logging with `Out`

### Test Source
3. **`app/src/test/kotlin/com/glassthought/shepherd/core/agent/noninteractive/FakeProcessRunner.kt`**
   - `FakeProcessRunner` implementing `ProcessRunner` with configurable `FakeProcessBehavior`
   - Records `lastCommandArgs` and `lastTimeout` for verification
   - Behaviors: `Succeed`, `Fail`, `Timeout`

4. **`app/src/test/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunnerImplTest.kt`**
   - 19 tests, all passing
   - BDD style with AsgardDescribeSpec
   - Covers: CLAUDE_CODE command, PI command (with ZAI_API_KEY), success/failed/timeout mapping, combined stdout+stderr, shell escaping

## Key Decisions

1. **Shell escaping**: Used single-quote wrapping (`'value'`) with `'\''` for embedded quotes rather than double-quote escaping. This is safer for arbitrary instruction strings.

2. **`combineOutput` utility**: Combines stdout and stderr with newline separator, but returns just one if the other is blank. This avoids leading/trailing newlines in output.

3. **`buildShellCommand` is `internal`**: Exposed at `internal` visibility for direct command-construction testing without needing to run the full process flow.

4. **ValType choices**: Used `ValType.ENUM` for agent type and `ValType.STRING_USER_AGNOSTIC` for model name (no project-specific ValTypeV2 exists yet).

## Test Results
```
Tests: 19 passed, 0 failed, 0 skipped
Build: SUCCESS (detekt + compile + test)
```
