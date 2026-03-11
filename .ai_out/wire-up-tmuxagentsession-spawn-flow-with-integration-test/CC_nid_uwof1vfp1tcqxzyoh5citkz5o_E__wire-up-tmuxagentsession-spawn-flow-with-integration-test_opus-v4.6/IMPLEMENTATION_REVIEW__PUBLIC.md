# Implementation Review: Wire up TmuxAgentSession Spawn Flow

## Summary

The implementation delivers the end-to-end TmuxAgentSession spawn flow as specified in the ticket. It introduces clean data types (`PhaseType`, `TmuxStartCommand`, `StartAgentRequest`, `TmuxAgentSession`), an `AgentStarter` abstraction with `ClaudeCodeAgentStarter` implementation, `AgentTypeChooser`, `AgentStarterBundleFactory` pattern, and the orchestrating `SpawnTmuxAgentSessionUseCase`. All 23 unit tests and 1 integration test pass. No existing tests were removed or modified beyond an additive change to `EnvironmentTest`. The architecture follows constructor injection, SRP, OCP, and project conventions well.

**One IMPORTANT correctness bug was found**: the system prompt is not escaped for single-quote context in the bash command, meaning any prompt file containing an apostrophe (extremely common in English) will break the command or potentially allow command injection.

## Overall Verdict: PASS_WITH_ADJUSTMENTS

## IMPORTANT Issues

### 1. [MAJOR] Shell Command Construction: System Prompt Not Escaped for Single-Quote Context

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/chainsaw/core/agent/starter/impl/ClaudeCodeAgentStarter.kt` (line 61)

**Problem**: The full command is wrapped in single quotes: `bash -c 'cd ... && unset CLAUDECODE && claude ...'`. The `workingDir` is properly escaped for single-quote context via `escapeForShell()`. However, the `claudeCommand` string (which contains the system prompt in double quotes) is interpolated directly into the single-quoted wrapper **without escaping its single quotes**.

The `escapeForDoubleQuote()` method correctly escapes `\`, `"`, `$`, `` ` `` for double-quote context, but within the outer single-quoted bash `-c` string, a literal single quote in the system prompt will terminate the single-quoted string, leading to either a syntax error or command injection.

**Example**: A system prompt file containing `You're a test agent.` produces:
```
bash -c 'cd /home/user/project && unset CLAUDECODE && claude --model sonnet --system-prompt "You're a test agent." --dangerously-skip-permissions'
```
The `'` in `You're` breaks the outer single-quoting.

**Suggested fix**: The entire `claudeCommand` portion (or specifically the system prompt) needs single-quote escaping as well. The simplest approach is to apply `escapeForShell()` to the entire `claudeCommand` string before interpolating it into the single-quoted wrapper, OR restructure to escape the system prompt for both contexts. One clean approach:

```kotlin
val fullCommand = "bash -c 'cd ${escapeForShell(workingDir)} && unset CLAUDECODE && ${escapeForShell(claudeCommand)}'"
```

**Severity**: MAJOR -- this is both a correctness bug (commands break on common English text with apostrophes) and a potential injection vector. The current test prompt (`You are a test agent.`) happens to have no single quotes, which is why the integration test passes.

**Test gap**: Add a unit test case with a system prompt containing a single quote to prevent regression.

### 2. [MAJOR] `model` and `allowedTools` Values Are Not Shell-Escaped

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/chainsaw/core/agent/starter/impl/ClaudeCodeAgentStarter.kt` (lines 34-39)

**Problem**: The `model` and `allowedTools` parameters are interpolated into the single-quoted command string without any escaping. Currently these are controlled by factory constants (`"sonnet"`, `["Read", "Write"]`), so the risk is low in practice. However, the `ClaudeCodeAgentStarter` class is a public API that accepts arbitrary strings -- if any future caller passes a model or tool name containing a single quote, the command breaks.

**Severity**: Low risk currently (factory controls the values), but architecturally unsound. Applying the fix from Issue 1 (escaping the entire `claudeCommand`) would also fix this.

## Suggestions

### 1. Consider Using `ProcessBuilder` Instead of String-Based Command Construction

The current approach of building a command string and passing it to `bash -c '...'` requires careful escaping of every value at every nesting level. An alternative would be to restructure so that `TmuxStartCommand` carries structured data (working directory, command parts as a list) rather than a pre-escaped string, and let the tmux session creation layer handle the composition. This is a larger refactor and not necessary for this ticket, but worth noting for future consideration.

### 2. The `delay` in `SpawnTmuxAgentSessionUseCase` Is a Pragmatic But Fragile Approach

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/chainsaw/core/agent/SpawnTmuxAgentSessionUseCase.kt` (line 86)

Using `delay(agentStartupDelay)` (default 5 seconds) between creating the tmux session and sending the GUID is pragmatic and the default is configurable. The 45-second timeout on the resolver provides a generous safety net. This is acceptable for V1 but a more robust approach (polling for readiness) would be worth exploring if timing issues arise.

### 3. Verbose Logging in `SpawnTmuxAgentSessionUseCase`

The use case has 6 `out.info()` calls for what is essentially a single operation. Consider using `out.debug()` for intermediate steps (agent_type_chosen, start_command_built, sending_guid, guid_sent_resolving_session_id) and keeping only the bookend logs (spawning_agent_session, agent_session_spawned) at INFO level. This is a minor suggestion -- not blocking.

### 4. Integration Test Package Placement Inconsistency

The integration test is placed in `org.example` package, matching existing integration tests. This is consistent. The plan reviewer noted this is worth eventually migrating to proper package placement -- agreed, but not for this ticket.

## Per-File Assessment

| File | Assessment |
|------|-----------|
| `PhaseType.kt` | Clean enum, good KDoc. |
| `TmuxStartCommand.kt` | Clean value class with appropriate documentation. |
| `StartAgentRequest.kt` | Clean data class. |
| `TmuxAgentSession.kt` | Good decision to use plain data class per review feedback (simpler than interface + impl). |
| `AgentStarter.kt` | Clean interface with clear SRP. |
| `ClaudeCodeAgentStarter.kt` | **Has shell escaping bug** (Issue 1). Otherwise well-structured with good separation of escape concerns. |
| `AgentTypeChooser.kt` | Interface + trivial V1 impl. Good OCP adherence. |
| `AgentStarterBundle.kt` | Clean data class pairing. |
| `AgentStarterBundleFactory.kt` | Clean interface with good KDoc explaining why `StartAgentRequest` (not just `PhaseType`) is needed. |
| `ClaudeCodeAgentStarterBundleFactory.kt` | Well-structured. Test/prod config separation is clean. Named constants for model and tools. `require` for unsupported agent types. |
| `SpawnTmuxAgentSessionUseCase.kt` | Good orchestration. Logging is thorough (perhaps slightly verbose at INFO). Configurable delay is a good decision. |
| `Environment.kt` | Additive-only change -- `test()` factory method parallels `production()`. |
| `test-agent-system-prompt.txt` | Minimal, appropriate for test use. |
| `ClaudeCodeAgentStarterTest.kt` | Good coverage of flag combinations. **Missing**: test case for system prompt containing single quotes. |
| `DefaultAgentTypeChooserTest.kt` | Data-driven across all PhaseType values -- good pattern. |
| `ClaudeCodeAgentStarterBundleFactoryTest.kt` | Covers test/prod environments, null prompt, unsupported agent type. Good. |
| `SpawnTmuxAgentSessionUseCaseIntegTest.kt` | Properly gated, cleanup in afterEach, pragmatic single `it` block for expensive operation. Good. |
| `EnvironmentTest.kt` | Additive -- tests new `Environment.test()` factory method. |

## Deviations from Plan Assessment

1. **TmuxAgentSession as plain data class** -- Good simplification per review feedback. YAGNI over premature interface.
2. **ResumeTmuxAgentSessionUseCase deferred** -- Correct decision. Better to design properly in its own ticket.
3. **CLI flags corrected** (`--system-prompt` instead of `--system-prompt-file`) -- Good correction based on actual CLI behavior.
4. **Added `unset CLAUDECODE`** -- Good discovery and necessary fix for tmux-spawned agents.
5. **Configurable `agentStartupDelay`** -- Pragmatic addition with reasonable default.
6. **No unit test for SpawnTmuxAgentSessionUseCase** -- Acceptable. Thin orchestrator covered by integration test.

## Summary Verdict

**PASS_WITH_ADJUSTMENTS** -- The implementation is solid architecturally and follows project standards well. The shell escaping bug in `ClaudeCodeAgentStarter` (Issue 1) needs to be fixed before this can be considered complete. It is a straightforward fix (escape the entire `claudeCommand` for single-quote context) and should include a test case for prompts containing single quotes.

IMPLEMENTATION_ITERATION is needed to fix the shell escaping issue.
