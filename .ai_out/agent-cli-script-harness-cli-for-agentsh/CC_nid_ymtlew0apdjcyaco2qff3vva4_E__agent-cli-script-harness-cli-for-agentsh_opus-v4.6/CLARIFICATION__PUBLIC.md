# CLARIFICATION — Agent CLI Script (harness-cli-for-agent.sh)

## Requirements Summary
- Create `scripts/harness-cli-for-agent.sh` bash script
- Reads server port from `$HOME/.chainsaw_agent_harness/server/port.txt`
- Subcommands: `done`, `question "<text>"`, `failed "<reason>"`, `status`
- Each wraps a `curl` POST to `/agent/{subcommand}` with JSON body
- All requests include current git branch as identifier
- `--help` produces usage output (will be embedded in agent instructions)
- Fail with clear error if port file missing

## Resolved Decisions
1. **Script location**: `scripts/harness-cli-for-agent.sh` (project convention)
2. **Body format**: JSON (`Content-Type: application/json`)
3. **Body structure**:
   - `done`: `{"branch": "<git-branch>"}`
   - `question`: `{"branch": "<git-branch>", "question": "<text>"}`
   - `failed`: `{"branch": "<git-branch>", "reason": "<reason>"}`
   - `status`: `{"branch": "<git-branch>"}`
4. **Testing**: Shell-based tests (no Kotlin/Gradle changes needed)
5. **`question` blocks**: curl blocks naturally until server responds (no special handling)

## No Blocking Ambiguities
All requirements are clear from the ticket and reference design doc.
