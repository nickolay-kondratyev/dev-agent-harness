---
closed_iso: 2026-03-19T18:02:15Z
id: nid_m1j23weqywhhom2jh3vj10rf7_E
title: "Implement TicketFailureLearningUseCase — cross-try failure learning"
status: closed
deps: [nid_njq7ezzxmf8orffmzf7oorsd0_E]
links: []
created_iso: 2026-03-19T00:48:19Z
status_updated_iso: 2026-03-19T18:02:15Z
type: feature
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [use-case, failure-learning]
---

## Context

Spec: `doc/use-case/TicketFailureLearningUseCase.md` (ref.ap.cI3odkAZACqDst82HtxKa.E).

When a `shepherd run` fails (try-N), the next try starts fresh with zero knowledge of what was attempted. This use case runs a non-interactive ClaudeCode agent that reads `.ai_out/` artifacts and produces a structured failure summary. The harness then appends a `## Previous Failed Attempts` section to the ticket file and handles all git operations.

## What to Implement

### 1. Interface + Data Classes
- `TicketFailureLearningUseCase` interface with `suspend fun recordFailureLearning(request: FailureLearningRequest): Unit`
- `FailureLearningRequest` data class: ticketPath, tryNumber, branchName, originatingBranch, failureContext, aiOutDir
- `PartResultFailureContext` data class: workflowType, failureType, failedAt, iteration, partsCompleted

### 2. Agent Instruction Assembly
- Build a focused instruction string from `FailureLearningRequest` fields
- Include structured failure facts (try number, branch, failure type, where it failed, iteration)
- Include path to `.ai_out/` directory so agent can read artifacts directly
- Include expected output format: `**Approach**:`, `**Root Cause**:`, `**Recommendations**:`
- No git instructions — agent only produces text on stdout

### 3. Agent Invocation
- Use `NonInteractiveAgentRunner.run()` (ref.ap.ad4vG4G2xMPiMHRreoYVr.E)
- Agent type: ClaudeCode, Model: sonnet, Timeout: 20 minutes
- `NonInteractiveAgentRunner` is a constructor dependency

### 4. Agent Output Processing
- On `Success`: capture stdout text as the agent summary
- On `Failed` or `TimedOut`: log WARN, use fallback text instead

### 5. TRY-N Section Building
- Combine structured facts from `FailureLearningRequest` with agent summary (or fallback)
- Format per spec:
```markdown
### TRY-{N}

- **Branch**: `{branchName}`
- **Workflow**: {workflowType}
- **Failure type**: {failureType}
- **Failed at**: {failedAt} (iteration {iteration})
- **Parts completed**: {partsCompleted, comma-separated}

#### Summary

{agent output or fallback message}
```

### 6. Ticket File Mutation
- If `## Previous Failed Attempts` heading does not exist in the ticket file, add it
- Append the `### TRY-{N}` subsection under that heading

### 7. Git Operations
- **Commit on try branch**: `git add {ticketPath} && git commit -m "[shepherd] ticket-failure-learning — TRY-{N}"`
- **Best-effort propagation to originating branch**:
  - `git checkout {originatingBranch}`
  - `git checkout {tryBranch} -- {ticketPath}`
  - `git commit -m "[shepherd] ticket-failure-learning — TRY-{N} (propagated)"`
  - `git checkout {tryBranch}`
- If any git operation fails, log WARN and skip — non-fatal

### 8. Non-Fatal Error Handling
- The entire use case is wrapped so it **never** throws/fails the workflow
- All errors (agent failure, git failure, propagation failure) are logged as WARN and swallowed
- Learning is best-effort

### 9. Unit Tests
- Use `FakeNonInteractiveAgentRunner` (to be created or already available from NonInteractiveAgentRunner ticket)
- Test happy path: agent succeeds, TRY-N section appended correctly
- Test agent failure: fallback summary used, ticket still updated
- Test agent timeout: same fallback behavior
- Test ticket already has `## Previous Failed Attempts`: new TRY-N appended under existing heading
- Test git commit failure: WARN logged, no exception thrown
- Test propagation failure: WARN logged, learning preserved on try branch
- Test no `.ai_out/` directory: agent still invoked (handles gracefully)

## Edge Cases (from spec)

| Scenario | Expected Behavior |
|----------|-------------------|
| Agent fails entirely | Log WARN, record structured facts only |
| Agent times out | Log WARN, kill process, record structured facts only |
| No `.ai_out/` directory | Agent handles gracefully |
| No `PUBLIC.md` files | Agent proceeds with less context |
| Ticket already has Previous Failed Attempts | Append new TRY-N subsection |
| Git commit fails on try branch | Log WARN, skip propagation, continue |
| Propagation fails | Log WARN, skip. Learning preserved on try branch |

## Dependencies
- `NonInteractiveAgentRunner` (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) — ticket nid_njq7ezzxmf8orffmzf7oorsd0_E

## Caller
- Called by `FailedToExecutePlanUseCase` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) — ticket nid_foubbnsh3vmk1fk34zm75zkg0_E

