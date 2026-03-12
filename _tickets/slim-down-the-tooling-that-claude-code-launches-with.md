---
closed_iso: 2026-03-12T23:16:18Z
id: nid_q4zk8eitl71hvou4svsud51dr_E
title: "Slim down the tooling that claude code launches with"
status: closed
deps: []
links: []
created_iso: 2026-03-09T18:00:42Z
status_updated_iso: 2026-03-12T23:16:18Z
type: task
priority: 3
assignee: nickolaykondratyev
---

When the harness launches a Claude Code agent (via `claude --tools "..."`) we should pass an explicit
tool allowlist so that:

1. **Less context** — fewer tool descriptions loaded → smaller context window per agent session.
2. **No confusion** — `AskUserQuestion` must be excluded; harness agents ask questions via
   `harness-cli-for-agent.sh` (HTTP POST to `/agent/question`), not via an interactive tool.

The `claude` CLI supports: `--tools <list>` (explicit set), `--allowedTools`, `--disallowedTools`.
Recommend `--tools` (explicit allowlist) — safest against future Claude Code adding new tools.

## All Claude Code System Tools (as of 2026-03-10)

| Tool | Description |
|------|-------------|
| `Bash` | Execute bash/shell commands |
| `Read` | Read file contents from the filesystem |
| `Edit` | Exact string replacement edit in a file |
| `Write` | Write/overwrite a file |
| `Glob` | Find files by glob pattern (sorted by mtime) |
| `Grep` | Search file content using ripgrep regex |
| `WebFetch` | Fetch and process web page content from a URL |
| `WebSearch` | Search the web |
| `Agent` | Launch specialized sub-agents for complex multi-step tasks |
| `NotebookEdit` | Edit Jupyter notebook cells |
| `EnterPlanMode` | Enter plan mode before implementation |
| `ExitPlanMode` | Signal plan completion and request user approval |
| `EnterWorktree` | Create an isolated git worktree for the session |
| `AskUserQuestion` | Interactively ask the user questions (blocks until answered) |
| `Skill` | Execute a user-invocable skill by name |
| `TaskCreate` | Create a task in the session task list |
| `TaskGet` | Retrieve full details of a task by ID |
| `TaskUpdate` | Update task status or details |
| `TaskList` | List all tasks |
| `TaskOutput` | Get output from a background task/shell |
| `TaskStop` | Terminate a background task |

## Open Decisions (need alignment before implementation)

1. **Mechanism**: `--tools` (explicit list, recommended) vs `--disallowedTools` (denylist)?
2. **Granularity**: Single global harness tool set vs per-role tool sets?
3. **Web tools**: Include `WebFetch` + `WebSearch`? (useful for research agents)
4. **Plan mode**: Include `EnterPlanMode` / `ExitPlanMode`? (used by PLANNER role)
5. **Task tools**: Include `TaskCreate/Get/Update/List/Output/Stop`?

## Likely Excluded (regardless of decisions above)

- `AskUserQuestion` — agents use `/agent/question` HTTP endpoint instead
- `Agent` — harness handles orchestration; nested sub-agents not in scope for V1
- `NotebookEdit` — not needed for typical coding tasks
- `EnterWorktree` — harness manages git branching
- `Skill` — not relevant for harness agents
