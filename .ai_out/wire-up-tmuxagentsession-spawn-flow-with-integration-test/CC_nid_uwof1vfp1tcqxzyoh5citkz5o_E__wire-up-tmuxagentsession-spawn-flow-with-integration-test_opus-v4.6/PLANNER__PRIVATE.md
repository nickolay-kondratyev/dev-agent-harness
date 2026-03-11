# PLANNER Private Context

## Research Findings

### Claude Code CLI v2.1.63 -- Key Flags for This Ticket

Source: [Claude Code CLI Reference](https://code.claude.com/docs/en/cli-reference)

**Flags we will use**:
- `--model sonnet` -- cheaper model for tests
- `--tools "Read,Write"` -- RESTRICTS available tools (different from `--allowedTools` which pre-approves)
- `--system-prompt-file <path>` -- REPLACES entire system prompt (test mode)
- `--append-system-prompt-file <path>` -- APPENDS to default prompt (production mode, preserves built-in behavior)
- `--dangerously-skip-permissions` -- required for non-interactive tmux usage
- `--resume <session-id>` -- for ResumeTmuxAgentSessionUseCase

**Flags we will NOT use**:
- `-p, --print` -- non-interactive mode, NOT what we want (we need interactive sessions)
- `--allowedTools` -- pre-approves tools without prompting, but we use `--dangerously-skip-permissions` instead
- `--continue`, `-c` -- continues most recent conversation, not what we need
- `--session-id <uuid>` -- forces a specific session ID; we let Claude generate its own

**Important distinction**: `--tools` vs `--allowedTools`
- `--tools "Read,Write"` RESTRICTS which tools are available (Claude can only use Read and Write)
- `--allowedTools "Read" "Write"` pre-approves tools so they don't prompt for permission, but all tools remain available
- For test mode, we want `--tools` to restrict and minimize context window

### Codebase Patterns Observed

1. **TestEnvironment is internal** to `com.glassthought.chainsaw.core.initializer.data` but accessible from tests because they're in the same module (`app`). Adding `Environment.test()` factory method provides a cleaner public API.

2. **AsgardAwaitility** is used in TmuxCommunicatorIntegTest for polling (not in the code we're writing, but useful pattern to know).

3. **No existing UseCase pattern** in the codebase -- this will be the first. The naming `XxxUseCase` is established in the CLAUDE.md project description.

4. **Value classes** are used sparingly (`HandshakeGuid`). `TmuxStartCommand` follows this pattern.

5. **Interface + impl colocated** pattern: Interface and default impl in same file (see `AgentTypeChooser`).

## Design Decisions Made During Planning

1. **No mocking framework**: All tests use hand-written fakes, following existing test patterns.

2. **Single integration test `it` block**: Despite "one assert per test" standard, spawning 3 Claude sessions is too expensive. One `it` block with 3 assertions is the pragmatic choice.

3. **`workingDir` as String, not Path**: Following existing patterns in the codebase (e.g., `InteractiveProcessRunner` uses String commands). Path would be more type-safe but adds friction with the bash command construction.

4. **Factory receives system prompt file path**: Rather than classpath resolution, the factory takes an explicit file path. This avoids classpath extraction complexity and keeps things explicit.

5. **Phase type does NOT affect CLI flags in V1**: All phases use the same Claude Code configuration. Environment (test/production) is the only differentiator. PhaseType is there for future evolution.

6. **No changes to AppDependencies/Initializer**: The spawn use case will be composed at the call site by future tickets, not wired into the global dependency graph yet.

## Risk Assessment

- **GUID handshake timing**: Medium risk. If Claude takes too long to start, the GUID might not be processed within the 45s timeout. Mitigation: the timeout is generous.
- **Test flakiness**: Medium risk. Integration test depends on Claude CLI availability, authentication, tmux, and network. Mitigation: gated with `isIntegTestEnabled()`.
- **Shell escaping**: Low risk. The `bash -c '...'` wrapping needs careful escaping of single quotes in paths. Mitigation: avoid paths with special characters; use `ProcessBuilder` args properly in `TmuxCommandRunner`.
