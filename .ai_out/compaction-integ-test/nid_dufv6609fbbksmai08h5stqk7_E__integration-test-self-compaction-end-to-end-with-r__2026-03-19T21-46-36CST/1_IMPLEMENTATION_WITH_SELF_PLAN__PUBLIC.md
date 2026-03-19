# Self-Compaction Integration Tests - Implementation Summary

## What was done

Created end-to-end integration tests for the self-compaction flow at:
`app/src/test/kotlin/com/glassthought/shepherd/integtest/compaction/SelfCompactionIntegTest.kt`

### Test Scenarios

1. **context_window_slim.json readability** - Spawns a real agent, writes a synthetic `context_window_slim.json` to the expected path, then verifies `ClaudeCodeContextWindowStateReader` can parse it correctly (remaining_percentage = 35).

2. **Self-compaction trigger** - Spawns agent, sends compaction instruction via `SelfCompactionInstructionBuilder`, expects `AgentSignal.SelfCompacted` signal back. Validates PRIVATE.md exists and is non-empty via `PrivateMdValidator`.

3. **Session rotation** - Full lifecycle: spawn agent -> send compaction instruction -> kill old session -> spawn new agent with PRIVATE.md content in system prompt -> send done instruction -> verify `AgentSignal.Done(COMPLETED)`.

### Architecture

- Extends `SharedContextDescribeSpec` for shared `ShepherdContext`
- Gated with `isIntegTestEnabled()` (requires `-PrunIntegTests=true`)
- Uses GLM for agent spawning (via `ServerPortInjectingAdapter` pattern)
- `FacadeDeps` data class groups facade construction dependencies to satisfy detekt's LongParameterList rule
- BDD style with GIVEN/WHEN/THEN, one assert per `it` block

### Decisions

- **Synthetic context_window_slim.json**: The external hook that writes this file may not be present in CI. We write the file ourselves after spawning the agent to validate the reader works correctly with a real session ID.
- **FacadeDeps data class**: Introduced to avoid detekt LongParameterList violation while keeping the test readable.
- **Duplicated ServerPortInjectingAdapter**: The original in `AgentFacadeImplIntegTest` is private. Duplicated rather than extracting to shared code (test helper, not production code).

## Build verification

- `./test.sh` passes (compilation + detekt + all unit tests)
- Integration tests are gated and will only run with `-PrunIntegTests=true`
