---
id: nid_ehbkox6da4gfkgul8jpc3quln_E
title: "Integration test — AgentFacadeImpl end-to-end signal flow"
status: open
deps: [nid_p1w49sk0s2isnvcjbmhgapho7_E, nid_qdd1w86a415xllfpvcsf8djab_E, nid_gpfjqkfrmfbvhm1gan31rlvs9_E]
links: []
created_iso: 2026-03-19T00:30:37Z
status_updated_iso: 2026-03-19T00:30:37Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [integration-test, agent-facade, testing]
---

## Context

Spec: `doc/core/AgentFacade.md` (ref.ap.9h0KS4EOK5yumssRCJdbq.E), Gate 4.

Sanity check that the real plumbing connects: real TMUX session → real agent → real HTTP callback → real deferred completion → executor resumes.

## What to Implement

Location: `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt`

### Test Scenarios

1. **Happy path — spawn + send + done**: Use `AgentFacadeImpl` to spawn a real agent (via GLM/Z.AI — ref deep memory `integ_tests__use_glm_for_agent_spawning.md`), send a simple instruction payload, receive ACK + done signal. Verify:
   - SpawnedAgentHandle contains valid HandshakeGuid and resolved session ID
   - sendPayloadAndAwaitSignal returns AgentSignal.Done(COMPLETED)
   - SessionEntry.lastActivityTimestamp updated

2. **Kill session**: After spawn, call killSession. Verify TMUX session no longer exists.

3. **Read context window state**: After spawn, call readContextWindowState. Verify returns a ContextWindowState (may be null if context_window_slim.json not yet written — acceptable for integration test).

### Test Configuration

- Extend `SharedContextDescribeSpec` (ref.ap.20lFzpGIVAbuIXO5tUTBg.E)
- Gate with `.config(isIntegTestEnabled())` 
- Use GLM (Z.AI) as agent, NOT Claude (see deep memory)
- Keep test count minimal — edge cases are covered by unit tests with FakeAgentFacade

## Acceptance Criteria

- Integration test passes with `./gradlew :app:test -PrunIntegTests=true`
- Real agent spawn → HTTP callback → deferred completion chain verified
- Test is gated behind `isIntegTestEnabled()` so regular `./test.sh` skips it
- No flakiness from real infrastructure (reasonable timeouts, clear failure messages)

