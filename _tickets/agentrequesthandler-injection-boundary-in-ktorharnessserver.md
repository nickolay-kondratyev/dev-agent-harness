---
closed_iso: 2026-03-10T20:55:32Z
id: nid_z3rhdp8coydv3aigd2o01h2t7_E
title: "AgentRequestHandler injection boundary in KtorHarnessServer"
status: closed
deps: []
links: []
created_iso: 2026-03-10T16:33:03Z
status_updated_iso: 2026-03-10T20:55:32Z
type: task
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [server, architecture, ktor]
---

Introduce an AgentRequestHandler interface to KtorHarnessServer so the server owns HTTP protocol concerns only, and the phase runner wires in behavior (especially the blocking /agent/question behavior).

Context: app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt

V1 all four endpoints (/agent/done, /agent/question, /agent/failed, /agent/status) are stubs that log and return {"status":"ok"} immediately. When real behavior is added, it will need to enter KtorHarnessServer — inject it via AgentRequestHandler to keep SRP clean.

The /agent/question endpoint in particular must eventually suspend until a human answers (answer delivered via TMUX send-keys) and return the answer in the response body. The interface shape will depend on the real behavior, so do NOT implement prematurely.

Suggested interface (finalize shape when implementing):
  interface AgentRequestHandler {
    suspend fun onDone(request: AgentDoneRequest)
    suspend fun onQuestion(request: AgentQuestionRequest): String  // blocking, returns answer
    suspend fun onFailed(request: AgentFailedRequest)
    suspend fun onStatus(request: AgentStatusRequest)
  }

A NoOpAgentRequestHandler should serve as placeholder.

Ref: CONSOLIDATED_REVIEW.md optional #7 in .ai_out/harness-http-server-review/


## Notes

**2026-03-10T20:55:38Z**

Completed. Created AgentRequestHandler.kt (interface + NoOpAgentRequestHandler), injected into KtorHarnessServer, /question returns handler answer in {"answer":"..."}. 17 tests pass. Commit: 0990b2f
