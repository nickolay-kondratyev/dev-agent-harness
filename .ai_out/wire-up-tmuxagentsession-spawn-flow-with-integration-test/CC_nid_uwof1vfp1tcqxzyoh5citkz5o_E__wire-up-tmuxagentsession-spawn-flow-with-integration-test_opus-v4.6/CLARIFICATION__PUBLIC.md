# CLARIFICATION: Wire up TmuxAgentSession Spawn Flow

## Status: ALIGNED — No blocking ambiguities

The ticket provides a detailed design from ticket nid_j54dq6ra33hix1e8aavanb8bz_E with clear contracts.

## Scope Decisions

1. **Primary scope**: SpawnTmuxAgentSessionUseCase + all supporting abstractions + integration test
2. **Secondary scope**: ResumeTmuxAgentSessionUseCase — mentioned in design, will implement but not integration-test
3. **Integration test**: Tests full spawn flow only (create tmux session → start Claude Code → GUID handshake → resolve session ID → verify TmuxAgentSession)
4. **Test config**: Environment.isTest = true → slim config (--tools Read,Write, --model sonnet, minimal system-prompt-file)

## Key Alignment Points
- All new abstractions follow constructor injection, no DI framework
- BDD integration test with isIntegTestEnabled() gating
- Cleanup tmux sessions in afterEach
- Package placement follows existing structure under `com.glassthought.chainsaw.core`
