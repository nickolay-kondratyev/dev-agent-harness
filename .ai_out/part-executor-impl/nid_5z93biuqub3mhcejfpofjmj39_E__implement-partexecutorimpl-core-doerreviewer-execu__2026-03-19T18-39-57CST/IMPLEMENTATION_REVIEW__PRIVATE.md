# PRIVATE: Implementation Review Notes

## Review Session Context
- Reviewed all 5 implementation files + spec + exploration summary
- Full test suite passes (`:app:test` EXIT_CODE=0)
- sanity_check.sh passes (EXIT_CODE=0)

## Detailed Analysis Notes

### Spec Compliance Checklist
- [x] R6: No Clock, SessionsState, AgentUnresponsiveUseCase, ContextWindowStateReader in constructor
- [x] PUBLIC.md validation after every Done signal
- [x] Git commit after each Done signal (via afterDone -> gitCommitStrategy.onSubPartDone)
- [x] Status validation via SubPartStateTransition (transitionTo, validateCanSpawn)
- [x] Done(PASS)/Done(NEEDS_ITERATION) in doer-only -> IllegalStateException
- [x] Doer stays IN_PROGRESS until reviewer PASS (both marked COMPLETED simultaneously)
- [x] Sessions killed on part completion
- [x] Context window state read at done boundaries
- [!] Reviewer spawned eagerly instead of lazily (spec deviation - flagged as I1)

### Status tracking through flows:
- Doer-only COMPLETED: NOT_STARTED->IN_PROGRESS (spawn) -> transitionTo validates -> COMPLETED -> killSession
- Doer+Reviewer COMPLETED+PASS:
  - Doer: NOT_STARTED->IN_PROGRESS (spawn) -> stays IN_PROGRESS through iterations -> COMPLETED on reviewer PASS
  - Reviewer: NOT_STARTED->IN_PROGRESS (spawn) -> transitionTo(PASS)=Complete -> COMPLETED
  - Both set COMPLETED on line 161-162
- NEEDS_ITERATION: reviewer stays IN_PROGRESS (IterateContinue transition type, status unchanged)

### Subtle correctness points verified:
1. `transitionTo` is called for validation but return discarded -- acceptable since it throws on invalid
2. In doer+reviewer path, doer's Done(COMPLETED) intentionally does NOT call transitionTo -- doer stays IN_PROGRESS (correct per spec)
3. `currentIteration++` happens AFTER afterDone (git commit + context window read) for NEEDS_ITERATION -- correct ordering
4. Budget check: `currentIteration < maxIterations` uses < not <= which means iteration 0 is first, and if max=1, after increment currentIteration=1 which is NOT < 1, so budget is exceeded. This is correct.
5. ITERATION_INCREMENT = 2 is used for budget extension -- matches spec "iteration.max += 2"

### Edge case I considered but is NOT an issue:
- The `error()` calls for illegal signals (PASS/NEEDS_ITERATION in doer-only, COMPLETED in reviewer, SelfCompacted) correctly call `killAllSessions` before `error()` where applicable. The SelfCompacted case does NOT kill sessions since it "should not reach" the executor at all -- this is a programming invariant, not a runtime signal.

### Why I gave NEEDS_ITERATION verdict:
- I1 (eager reviewer spawn) is a real spec deviation that changes resource usage behavior
- The test coverage gap (reviewer Done(COMPLETED) not tested) should be filled
- ValType usage needs semantic specificity per project standards
