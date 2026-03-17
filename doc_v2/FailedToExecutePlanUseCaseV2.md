# FailedToExecutePlanUseCase — V2 Smart Cleanup / ap.tmCqyceyxReMeJGgvubVu.E

> **V2 feature** — not implemented in V1. In V1, `FailedToExecutePlanUseCase` prints a red error
> to the console and halts — waiting for human intervention. This document describes the V2
> automated cleanup design.

## Motivation

When plan execution hits blocking issues (agent calls `/callback-shepherd/signal/fail-workflow`), V1
requires the human to manually clean up. V2 automates this with a CLEANUP_AGENT that rolls back
the codebase and re-opens the ticket for retry.

## V2 Flow — CLEANUP_AGENT

When plan execution fails:

1. Spawn a **`CLEANUP_AGENT`** using `PartExecutorImpl` (with `reviewerConfig = null` — single sub-part, no reviewer).
   The `CLEANUP_AGENT` role is expected in the role catalog (`$TICKET_SHEPHERD_AGENTS_DIR`).
   Uses the standard TMUX spawn flow and callback protocol.
2. Cleanup agent analyzes the approach taken and why it failed
3. Writes failure summary + learnings into the ticket (so next retry is better informed)
4. Commits all current work (`git add -A && git commit`)
5. Determines the common ancestor: `git merge-base HEAD origin/$(default.branch)`
   — default branch resolved via `GitBranchManager.getDefaultBranch()`
   (shells out to `git symbolic-ref refs/remotes/origin/HEAD`, strips prefix)
6. Checks out the merge-base commit to restore the codebase to pre-branching state
7. Re-opens the ticket by setting `status: open` in the ticket's YAML frontmatter directly
   (does NOT use `tk` CLI)
8. Calls `callback_shepherd.signal.sh done completed` when finished

### Terminal Failure — Cleanup Agent Calls `fail-workflow`

If the cleanup agent itself encounters an unrecoverable error and calls
`callback_shepherd.signal.sh fail-workflow`, the harness **prints a red error message to the console
and halts** — same as V1 behavior. No recursive cleanup. This is a terminal state.

## Default Branch Resolution

The cleanup agent needs to know the remote's default branch name for the merge-base computation.
This is resolved at runtime, not configured.

**Method**: `GitBranchManager.getDefaultBranch()` — shells out to:

```bash
git symbolic-ref refs/remotes/origin/HEAD
# → refs/remotes/origin/main — strip prefix → "main"
```

**Fail hard** if the ref is not set (e.g., after a bare `git init` without a proper clone).
Error message should be actionable: _"Run `git remote set-head origin --auto` to set the
default branch ref."_

**Integration test**: `getDefaultBranch()` returns `"main"` for this repository.

## Git Operations (V2 Only)

| When | Operation |
|---|---|
| `FailedToExecutePlanUseCase` cleanup | `CLEANUP_AGENT` (via `PartExecutorImpl` with `reviewerConfig = null`): `git add -A && git commit` all work → `git merge-base HEAD origin/$(default.branch)` → `git checkout <merge-base>` → set ticket `status: open` in frontmatter directly. Default branch resolved via `GitBranchManager.getDefaultBranch()`. |

## Try-N Auto-Increment (V2)

After the CLEANUP_AGENT successfully resets the codebase and re-opens the ticket, the next
`shepherd run` will naturally pick a new try-N (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) since the
previous branch and `.ai_out/` directory still exist.

In V1, try-N increments only on manual retries — the human creates a new run after failure.
