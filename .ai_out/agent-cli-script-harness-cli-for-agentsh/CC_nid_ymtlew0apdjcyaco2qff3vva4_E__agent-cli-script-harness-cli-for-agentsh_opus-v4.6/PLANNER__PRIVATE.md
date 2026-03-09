# PLANNER Private Context -- harness-cli-for-agent.sh

## Planning Session Notes

### Complexity Assessment
This is simple glue code. The entire script should be under 100 lines. The test script may be slightly longer due to assertion helpers but should still be under 200 lines.

### Key Design Decision: jq for JSON
The single most impactful decision is using `jq -n --arg` for JSON construction instead of manual string interpolation. This eliminates an entire class of injection/escaping bugs with zero added complexity. The dev environment already has `jq` available.

### Key Design Decision: DRY_RUN test hook
Rather than mocking curl via PATH manipulation or starting a real HTTP server for testing, the `HARNESS_CLI_DRY_RUN` env var approach is the simplest way to verify curl command construction. It is explicit, opt-in, and clearly named -- not a silent fallback.

### Bash Style Conventions Observed
- `run.sh` uses `#!/usr/bin/env bash` + `# __enable_bash_strict_mode__` + `main()` pattern
- `test.sh` uses `#!/usr/bin/env bash` + `set -euo pipefail` (explicit strict mode)
- Both patterns are in use. For this script, use `set -euo pipefail` explicitly (matches `test.sh` and is more portable -- `__enable_bash_strict_mode__` appears to be a project-specific hook).

### Files Referenced
- Ticket: `_tickets/agent-cli-script-harness-cli-for-agentsh.md`
- Design doc: `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`
- Style refs: `run.sh`, `test.sh`, `sanity_check.sh`, `CLAUDE.generate.sh`

### Risks
- None significant. This is straightforward.
- The only minor risk is `jq` not being available, but it is standard in any dev environment this project targets.
