---
id: nid_ardsgbv0n6z48ya1y3079ez2u_E
title: "SIMPLIFY_CANDIDATE: Move UserQuestionHandler out of executor's coroutine scope — fire-and-forget Q&A delivery"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T22:01:57Z
status_updated_iso: 2026-03-17T22:25:34Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, user-question, coroutines]
---

FEEDBACK:
--------------------------------------------------------------------------------
Currently user questions suspend the executor coroutine:
1. Agent calls /callback-shepherd/signal/user-question
2. Server delegates to UserQuestionHandler (blocks waiting for human input)
3. Answer delivered asynchronously via TMUX send-keys
4. Executor remains suspended on signalDeferred.await() while human thinks (potentially minutes)

The executor holds live state while human latency is unbounded. Health monitoring loop is blocked during this time. If the human never answers, the question hangs indefinitely.

Proposed simplification:
- Move UserQuestionHandler entirely OUTSIDE the executor coroutine scope
- Agent signals question via HTTP (fire-and-forget; agent continues work)
- Server spawns a separate lightweight coroutine to handle Q&A (blocking on user input)
- When answer is ready, server sends it via TMUX send-keys (same channel as health pings)
- Executor NEVER suspends on a user question — it only awaits done signals

Robustness gains:
- Executor state simplified — no user question suspension state to manage
- Human response latency no longer blocks executor health monitoring loop
- noActivityTimeout can still fire naturally if agent stops working
- Natural parallelism: executor waits for work while human handles Q&A separately
- No indefinite hang risk from unanswered questions

Relevant specs:
- doc/core/agent-to-server-communication-protocol.md (user-question endpoint, answer delivery)
- doc/core/UserQuestionHandler.md (interface, lifecycle)

Relevant code:
- app/src/main/kotlin/com/glassthought/shepherd/core/supporting/ (UserQuestionHandler)
- Server endpoint handler for /user-question
- PartExecutor interaction with UserQuestionHandler
- SessionEntry state

--------------------------------------------------------------------------------

NEXT_STEPS: let's clarify. when we go into user waiting questions we know that we can be in this state for a while (could be HOURS). During this time there is no reason to ping the agent UNTIL user has answered the questions since we would be just using up the context of the agent for no reason. Hence, let's make sure to adjust the above approach to take this into account. 
