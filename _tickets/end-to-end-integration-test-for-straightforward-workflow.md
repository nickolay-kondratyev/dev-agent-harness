---
closed_iso: 2026-03-20T15:53:19Z
id: nid_7td55ygwv0g6tatrehzrz4ldz_E
title: "End-to-end integration test for straightforward workflow"
status: closed
deps: []
links: []
created_iso: 2026-03-20T14:57:16Z
status_updated_iso: 2026-03-20T15:53:19Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [integration-test, e2e, straightforward]
---

Create an end-to-end integration test that exercises the full straightforward workflow.

## What to test
- Set up a temporary git repo with a simple ticket (e.g., "write hello-world.sh")
- Run the actual binary (`./app/build/install/app/bin/app`) with `--ticket=<path> --workflow straightforward --iteration-max 1`
- Verify the full flow: handshake, instruction delivery, agent execution, done signal
- Verify the output (e.g., hello-world.sh exists and is correct)

## Implementation approach
- Use a real temporary directory with `git init`
- Need the actual binary built (see how `run.sh` does `./gradlew :app:installDist`)
- Listen to stdout/stderr of the process for logging/debugging
- May need to configure env vars: TICKET_SHEPHERD_SERVER_PORT, HOST_USERNAME, TICKET_SHEPHERD_AGENTS_DIR, MY_ENV, AI_MODEL__ZAI__FAST
- Consider using GLM (Z.AI) for the agent to avoid Anthropic API costs
- Follow existing integration test patterns: gate with `isIntegTestEnabled()`, use `SharedContextDescribeSpec` if appropriate

## Key files to reference
- `run.sh` — how the binary is invoked
- `app/src/test/kotlin/com/glassthought/shepherd/integtest/` — existing integration test patterns
- `config/workflows/straightforward.json` — the workflow definition
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializer.kt` — startup sequence
- `app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt` — CLI entry point


## Notes

**2026-03-20T15:53:18Z**

## Resolution

Implemented end-to-end black-box integration test for the straightforward workflow.

### Files created/modified
- `app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/StraightforwardWorkflowE2EIntegTest.kt` — main test
- `test_with_integ.sh` — added `./gradlew :app:installDist` prerequisite

### Test details
- Extends `AsgardDescribeSpec` (black-box, not in-process)
- Gated with `isIntegTestEnabled()` + `@OptIn(ExperimentalKotest::class)`
- Creates temp git repo under `.tmp/` with ticket, CLAUDE.md, workflow config
- Runs actual binary via `ProcessBuilder` with GLM env vars for cost-free execution
- Asserts: exit code 0, `.ai_out/` exists, feature branch created
- 15-minute timeout, diagnostic output on failure
- Delta-based tmux session cleanup
