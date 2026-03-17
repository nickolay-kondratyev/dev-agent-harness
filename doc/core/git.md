# Git — Branch Naming & Commit Strategy / ap.BvNCIzjdHS2iAP4gAQZQf.E

The harness owns all git operations. Agents never commit — they make code changes, signal done,
and the harness commits on their behalf. This gives the harness full control over branch naming,
commit timing, message format, and author attribution.

---

## Working Tree Validation — Startup Guard
<!-- ap.QL051Wl21jmmYqTQTLglf.E -->

Before any git operations (branch creation, commits), the **working tree must be clean**.
This is validated at startup by `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E)
before branch creation.

**Check**: `git status --porcelain` must produce empty output.

**If dirty**: Fail hard with a clear error message listing the dirty files and instructing
the user to commit or stash their changes before running `shepherd run`. Example:

```
ERROR: Working tree is not clean. Shepherd requires a clean working tree to avoid
mixing pre-existing uncommitted work with agent output.

Dirty files:
  M  src/Main.kt
  ?? newfile.txt

Please commit or stash your changes before running 'shepherd run'.
```

**Why**: `git checkout -b` with uncommitted changes carries those changes to the new branch.
The first `git add -A && git commit` by the harness would silently include pre-existing
human WIP in the first agent's commit — mixing human work with agent output. Failing hard
at startup prevents this.

---

## Branch Naming
<!-- ap.THL21SyZzJhzInG2m4zl2.E -->

Branch is derived from the ticket. Format: `{TICKET_ID}__{slugified_title}__try-{N}`

- `TICKET_ID`: the `id` field from the ticket's YAML frontmatter
- `slugified_title`: the ticket `title` slugified (lowercase, hyphens); compressed via `DirectQuickCheapLLM` (ref.ap.hnbdrLkRtNSDFArDFd9I2.E) if too long
- `try-{N}`: starts at 1, incremented on each manual retry (V1: human creates a new run after failure)
- Delimiter between components: `__` (double underscore)

### Branch Creation

- **Ticket must be `in_progress`** — validated by `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) before any git operations. Caller is responsible for marking the ticket and pushing to remote before invoking `shepherd run`.
- Branch created from **current HEAD** at time of `shepherd run`.
- **Every `shepherd run` = new try** (V1). No resume-on-restart (V2 — ref.ap.LX1GCIjv6LgmM7AJFas20.E).
- **Owner**: `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) — validates ticket status, resolves try-N, creates the branch, then sets up `.ai_out/`.

### Try-N Resolution

Try-N is determined by scanning `.ai_out/` directories — the **single source of truth**.
N is the first value where the corresponding `.ai_out/` directory does not exist:

1. Build candidate branch name via `BranchNameBuilder.build(ticket, candidateN)`
2. Check: does `.ai_out/{candidate}/` directory exist?
3. If exists → increment candidateN, repeat
4. If not → use candidateN

**Rationale**: `.ai_out/` directories are always created as part of initialization
(ref.ap.BXQlLDTec7cVVOrzXWfR7.E). If a try ran far enough to matter, it created its
`.ai_out/` directory. If it didn't even get that far, there's nothing to conflict with.
No git operations needed — reduces failure surface and eliminates the dual-scan complexity.

---

## What Gets Committed

**The entire working tree.** Every commit is preceded by `git add -A` — all agent code changes,
`.ai_out/` metadata, `current_state.json` updates. Nothing is selectively staged.

This means `.ai_out/` is git-tracked. The full history of `PUBLIC.md` changes across iterations
is preserved in git (ref.ap.56azZbk7lAMll0D4Ot2G0.E — plan-and-current-state iteration semantics).

---

## When Commits Happen — GitCommitStrategy

Commit timing is controlled via a `GitCommitStrategy` interface with a single hook:

| Hook | When it fires |
|------|---------------|
| `onSubPartDone` | After a sub-part signals `result` via `/callback-shepherd/signal/done` (before the next sub-part starts or iteration resumes) |

**V1 strategy: `CommitPerSubPart`** — commits after every sub-part done signal. This preserves
full iteration history in git (every doer completion, every reviewer pass/needs_iteration gets
its own commit), which is the primary reason for harness-owned commits. The granularity also
enables auditing agent communication via PUBLIC.md across iterations.

**Empty commit handling:** If `git diff --cached` is empty after `git add -A` (no changes
since last commit), the commit is **skipped**. This avoids noise from `needs_iteration`
commits where the reviewer flagged issues but the doer hasn't changed code yet.

### Hook Context

The hook receives sufficient context to build the commit message and author:

- Part name, sub-part name
- Sub-part role (doer/reviewer)
- Result value (`completed`, `pass`, `needs_iteration`)
- Current iteration number and max
- Agent type and model (from the session record in `current_state.json`)

---

## Commit Message Convention

The branch already carries the ticket ID and feature name (`{TICKET_ID}__{slugified_title}__try-{N}`),
so the commit message focuses on **what just happened** within the workflow:

```
[shepherd] {part_name}/{sub_part_name} — {result} (iteration {N}/{max})
```

Examples:
```
[shepherd] planning/plan — completed
[shepherd] planning/plan_review — pass (iteration 1/3)
[shepherd] ui_design/impl — completed (iteration 1/3)
[shepherd] ui_design/review — needs_iteration (iteration 1/3)
[shepherd] ui_design/impl — completed (iteration 2/3)
[shepherd] ui_design/review — pass (iteration 2/3)
[shepherd] backend_impl/impl — completed
```

Rules:
- `[shepherd]` prefix — identifies harness-generated commits in git log
- Iteration info included only when the part has a reviewer (iteration semantics apply)
- Single sub-part parts (no reviewer) omit the iteration suffix
- **Planning phase** uses synthetic part name `planning` (e.g., `[shepherd] planning/plan — completed`)

---

## Commit Author

Each commit's **author name** encodes the coding agent, model, model version, and host user:

```
${CODING_AGENT}_${CODING_MODEL}-v${VERSION_OF_MODEL}_WITH-${HOST_USERNAME}
```

Examples:
```
CC_sonnet-v4.6_WITH-nickolaykondratyev
CC_opus-v4.1_WITH-nickolaykondratyev
```

| Component | Source |
|---|---|
| `CODING_AGENT` | Short code derived from `agentType` in the session record. Mapping: `ClaudeCode` → `CC`, `PI` → `PI`. |
| `CODING_MODEL` | `model` field from the session record (e.g., `sonnet`, `opus`, `glm-5`) |
| `VERSION_OF_MODEL` | Resolved from `${MODEL_VERSION_DIR}/${model}` file (see [Model Version Resolution](#model-version-resolution)) |
| `HOST_USERNAME` | `${HOST_USERNAME}` environment variable |

**Commit email stays as-is** — uses whatever is configured in the git config for the repo.
Only the author name is overridden per commit (via `git commit --author`).

---

## Model Version Resolution

Model versions are resolved from files on disk, not hardcoded.

**Directory**: `${MODEL_VERSION_DIR}` environment variable points to a directory containing one file
per model. Each file is named after the model and contains the version string (no newline padding).

```
${MODEL_VERSION_DIR}/
├── sonnet          # contains: 4.6
├── opus            # contains: 4.1
├── glm-5           # contains: 1.0
└── glm-4.7-flash   # contains: 1.0
```

**Lookup**: The `model` field from the session record (e.g., `"sonnet"`) is used as the filename.
If the file does not exist → fail hard with a clear error naming the missing file.

---

## Required Environment Variables

All must be present at **harness initialization** (not deferred to first use). Fail hard
immediately with a clear error message if any is missing.

| Env var | Purpose | Validated at |
|---|---|---|
| `HOST_USERNAME` | Identifies the human operator in commit author attribution | Initialization |
| `MODEL_VERSION_DIR` | Directory containing model version files for commit author attribution | Initialization |
| `TICKET_SHEPHERD_AGENTS_DIR` | Directory containing agent role definition `.md` files (ref.ap.Q7kR9vXm3pNwLfYtJ8dZs.E). Must point to `_config/agents/_generated/`. | Initialization |
| `MY_ENV` | Root directory for environment-specific configuration. System prompt files resolved relative to this path. Must contain `config/claude/ai_input/system_prompt/for_planning.md` and `config/claude/ai_input/system_prompt/default.md`. See [System Prompt File Resolution](../use-case/SpawnTmuxAgentSessionUseCase.md#system-prompt-file-resolution). | Initialization |

This follows the same fail-hard pattern as `Z_AI_API_TOKEN` — flush configuration bugs at startup,
not mid-workflow.

---

## Git Operation Failure Handling — AutoRecoveryByAgentUseCase
<!-- ap.AQ8cRaCyiwZWdK5TZiKgJ.E -->

Git operations (especially `git commit`) can fail mid-workflow due to infrastructure issues
(disk full, `.gitignore` conflict, index lock, etc.). Since commits happen after each sub-part
signal, a failure here would otherwise be an unhandled exception that halts the workflow.

**Instead of failing outright, the harness runs a recovery agent** via
`AutoRecoveryByAgentUseCase` (ref.ap.q54vAxzZnmWHuumhIQQWt.E — see
[`doc/use-case/AutoRecoveryByAgentUseCase.md`](../use-case/AutoRecoveryByAgentUseCase.md)),
which delegates to `NonInteractiveAgentRunner` (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) — a PI
agent in `--print` mode with `$AI_MODEL__ZAI__FAST`.

### GitOperationFailureUseCase

A git-specific use case that packages context and delegates to `AutoRecoveryByAgentUseCase`:

1. **Captures failure context**:
   - The git command that failed (e.g., `git add -A && git commit ...`)
   - The error output (stderr)
   - Current `git status` output
   - Current `git diff` output (if relevant)
   - Working directory path
2. **Captures workflow context**:
   - Where we are in the workflow (part name, sub-part name, iteration number)
   - What we were trying to achieve (e.g., "commit agent changes after sub-part completion")
   - The commit message that was intended
3. **Delegates to `AutoRecoveryByAgentUseCase`** with the packaged context
4. **On recovery success** → retries the original git operation **once**
5. **On retry failure or recovery failure** → delegates to `FailedToExecutePlanUseCase`
   (prints red error, halts — waits for human intervention)

**Ownership**: The failure handling (catch → recovery → retry → fallback to
`FailedToExecutePlanUseCase`) is encapsulated within the `GitCommitStrategy` implementation.
The executor calls `onSubPartDone` and either gets a successful commit or a
`FailedToExecutePlanUseCase` escalation — the executor does not orchestrate the recovery itself.

### Failure Points Covered

| Git Operation | Where It Happens | Recovery Via |
|---|---|---|
| `git add -A` | `GitCommitStrategy.onSubPartDone` | `GitOperationFailureUseCase` → `AutoRecoveryByAgentUseCase` |
| `git commit` | `GitCommitStrategy.onSubPartDone` | `GitOperationFailureUseCase` → `AutoRecoveryByAgentUseCase` |
| `git checkout -b` | `TicketShepherdCreator` (startup) | **Not covered** — startup failures fail hard (no recovery agent). The working tree is validated clean at this point, so `checkout -b` failures indicate a more fundamental issue. |

---

## Git Operations Summary

The harness performs these git operations during a workflow:

| When | Operation |
|---|---|
| Workflow start (in `TicketShepherdCreator` ref.ap.cJbeC4udcM3J8UFoWXfGh.E) | Validate clean working tree (ref.ap.QL051Wl21jmmYqTQTLglf.E) → resolve try-N (`.ai_out/` directory scan) → `git checkout -b {branch}`. See [Try-N Resolution](#try-n-resolution) above. |
| `onSubPartDone` | `git add -A` → `git commit --author="{author} <{email}>" -m "{message}"`. On failure → `GitOperationFailureUseCase` → `AutoRecoveryByAgentUseCase` (ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E). |
| Workflow failure (`FailedToExecutePlanUseCase`) | `TicketFailureLearningUseCase` (ref.ap.cI3odkAZACqDst82HtxKa.E): agent (ClaudeCode `--print`, sonnet) reads `.ai_out/` artifacts and produces failure summary on stdout. **Harness** appends `### TRY-{N}` section to ticket, commits on try branch (`git add {ticketPath} && git commit`), and attempts best-effort propagation to originating branch. Non-fatal — all failures logged and skipped. |
