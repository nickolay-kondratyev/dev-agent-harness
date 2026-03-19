# AgentFacadeImpl Review -- Private Context

## Review Process

1. Read all context files (exploration, implementation public)
2. Read all files under review (AgentFacadeImpl, SessionsState, test, detekt-config)
3. Read related interfaces and types (AgentFacade, SessionEntry, SpawnTmuxAgentSessionUseCase, AgentTypeAdapter, SpawnAgentConfig, SpawnedAgentHandle, AckedPayloadSender, AgentPayload, HarnessTimeoutConfig, AsgardBaseException)
4. Ran `./test.sh` and `./sanity_check.sh` -- both pass
5. Analyzed diff against main

## Key Findings Priority

### Correctness
- **C1 (using stale entry for sendKeys)**: Technically works today because the TmuxAgentSession reference is shared, but is semantically wrong and will silently break if SessionEntry is ever made immutable/copied properly.
- **C2 (missing ACK protocol)**: The interface KDoc explicitly promises ACK wrapping with 3x retry. The implementation sends raw file path. This is the most significant gap. The "V1 stub" framing in the implementation doc understates the gap -- the interface contract is violated.

### Architecture
- **I1 (exception hierarchy)**: Straightforward fix, just needs to extend AsgardBaseException with structured vals.
- **I5 (hardcoded workingDir)**: Design gap -- should be injectable.

### Test Quality
- **I4 (delay in tests)**: The `spawn` helper with `removeAllForPart` polling loop is particularly concerning because it has destructive side effects on SessionsState. The test for "registers entry in SessionsState" only passes due to timing luck (the polling job removes entries for startup handshake, but the real entry is registered after the job is cancelled).
- The test coverage is otherwise quite good -- 20 tests covering happy path, timeout cleanup, kill with/without entry, readContextWindowState delegation, and sendPayloadAndAwaitSignal with/without entry.

### detekt threshold bump
- The bump from 8 to 9 for `constructorThreshold` is reasonable given AgentFacadeImpl has 8 constructor parameters (all legitimate DI dependencies). This is a global setting change though -- worth noting that it now allows 9-param constructors everywhere.

## What's Good
- Clean DIP usage (TmuxSessionCreator, SingleSessionKiller interfaces)
- Correct placeholder pattern for startup handshake
- Proper cleanup on timeout (remove from SessionsState + kill tmux session)
- Well-structured helper methods with clear responsibilities
- BDD test structure with one assert per test
- Good edge case coverage (kill non-existing, spawn timeout cleanup)
