# EXPLORATION — Agent CLI Script

## Key Findings

### Project Structure
- No `scripts/` or `bin/` directory exists yet — needs creation
- Existing bash scripts at root: `run.sh`, `test.sh`, `sanity_check.sh`, `CLAUDE.generate.sh`
- All use `set -euo pipefail` or bash strict mode

### Server Endpoints (Documented, Not Yet Implemented)
The HTTP server (Ktor CIO) is planned but NOT yet built. Endpoints:
- `POST /agent/done` — task complete
- `POST /agent/question` — Q&A (curl blocks until answered)
- `POST /agent/failed` — unrecoverable error
- `POST /agent/status` — health ping reply

### Port File Management
- Server will write port to: `$HOME/.chainsaw_agent_harness/server/port.txt`
- Server binds to port 0 (OS-assigned), file-based discovery
- On shutdown, file is deleted
- CLI must error if file missing

### Testing Patterns
- **Kotlin**: Kotest BDD-style with `AsgardDescribeSpec`, GIVEN/WHEN/THEN via describe/it
- **Integration tests**: Gated via `.config(isIntegTestEnabled())`
- **No bash test framework yet** — shell-based tests for this script

### Existing Bash Script Patterns
- `run.sh`: `#!/usr/bin/env bash` + `set -euo pipefail` + simple build/run
- `test.sh`: Sets `THORG_ROOT` env and runs gradlew test

### What's NOT Yet Implemented
- Ktor HTTP server, port file writing, workflow JSON, picocli CLI
- This script is designed to work AHEAD of the server implementation

### Design Reference
- Harness→Agent: TMUX `send-keys` (ref.ap.7sZveqPcid5z1ntmLs27UqN6.E)
- Agent→Harness: HTTP POST via harness-cli-for-agent.sh
- Structured content: Via temp files in `$HOME/.chainsaw_agent_harness/tmp/agent_comm/`
