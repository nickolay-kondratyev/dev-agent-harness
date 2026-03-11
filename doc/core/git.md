# Git Commit Strategy / ap.BvNCIzjdHS2iAP4gAQZQf.E

The harness owns all git operations. Agents never commit — they make code changes, signal done, and
the harness commits on their behalf. This gives the harness full control over commit timing, message
format, and author attribution.

For branch naming see [Git Branch / Feature Naming](../high-level.md#git-branch--feature-naming)
(ref.ap.THL21SyZzJhzInG2m4zl2.E).

---

## What Gets Committed

**The entire working tree.** Every commit is preceded by `git add -A` — all agent code changes,
`.ai_out/` metadata, `current_state.json` updates. Nothing is selectively staged.

This means `.ai_out/` is git-tracked. The full history of `PUBLIC.md` changes across iterations
is preserved in git (ref.ap.56azZbk7lAMll0D4Ot2G0.E — plan-and-current-state iteration semantics).

---

## When Commits Happen — GitCommitStrategy

Commit timing is **swappable** via a `GitCommitStrategy` interface. The harness calls the strategy
at two hook points during workflow execution:

| Hook | When it fires |
|------|---------------|
| `onSubPartDone` | After a sub-part signals `result` via `/callback-shepherd/done` (before the next sub-part starts or iteration resumes) |
| `onPartDone` | After a part completes (all sub-parts finished, TMUX sessions killed) |

Each `GitCommitStrategy` implementation decides **which hooks to commit at**. Examples:

| Strategy | `onSubPartDone` | `onPartDone` | Use case |
|---|---|---|---|
| `CommitPerSubPart` | ✅ commit | no-op (already committed) | Maximum granularity — every doer completion, every reviewer pass/needs_iteration gets its own commit |
| `CommitPerPart` | no-op | ✅ commit | Minimal commits — one per completed part |

**V1 default: `CommitPerSubPart`** — preserves full iteration history in git, which is the primary
reason for harness-owned commits.

### Hook Context

Both hooks receive sufficient context to build the commit message and author:

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

Both must be present at **harness initialization** (not deferred to first commit). Fail hard
immediately with a clear error message if either is missing.

| Env var | Purpose | Validated at |
|---|---|---|
| `HOST_USERNAME` | Identifies the human operator in commit author attribution | Initialization |
| `MODEL_VERSION_DIR` | Directory containing model version files for commit author attribution | Initialization |

This follows the same fail-hard pattern as `Z_AI_API_TOKEN` — flush configuration bugs at startup,
not mid-workflow.

---

## Git Operations Summary

The harness performs these git operations during a workflow:

| When | Operation |
|---|---|
| Workflow start (in `TicketShepherdCreator` ref.ap.cJbeC4udcM3J8UFoWXfGh.E) | Resolve try-N (dual check: `git branch --list` + `.ai_out/` directory) → `git checkout -b {branch}`. See [Try-N Resolution](../high-level.md#try-n-resolution) (ref.ap.THL21SyZzJhzInG2m4zl2.E). |
| `onSubPartDone` / `onPartDone` (per strategy) | `git add -A` → `git commit --author="{author} <{email}>" -m "{message}"` |
| Workflow failure (`FailedToExecutePlanUseCase`) | V1: no git operations on failure — harness prints red error and halts. V2 will add automated cleanup (see `doc_v2/FailedToExecutePlanUseCaseV2.md`). |
