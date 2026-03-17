---
id: nid_x3083dbww7sokx19bfltj81of_E
title: "SIMPLIFY_CANDIDATE: EnvironmentValidator should collect all failures before failing — not fail-fast on first error"
status: open
deps: []
links: []
created_iso: 2026-03-17T21:32:56Z
status_updated_iso: 2026-03-17T21:32:56Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, startup, ux, robustness]
---

Current design: EnvironmentValidator.validate() performs sequential checks:
1. Docker container check (/.dockerenv)
2. tmux binary check
3. Required env vars check (Constants.REQUIRED_ENV_VARS.ALL)

See doc/high-level.md "Startup — Initializer" (ref.ap.HRlQHC1bgrTRyRknP3WNX.E, ref.ap.A8WqG9oplNTpsW7YqoIyX.E).

Problem: The current fail-on-first-error pattern means that if a new environment is missing Docker, tmux, and 3 env vars, the user has to run shepherd run 5+ times to discover all missing prerequisites. Each fix-and-retry cycle can take minutes in a remote dev environment.

Simpler approach: Collect ALL validation failures in one pass, then fail with a single consolidated error listing every issue:

  ERROR: Environment validation failed:
    - Not running in a Docker container (/.dockerenv not found). Shepherd requires Docker for safety.
    - tmux not found on $PATH. Install: apt install tmux
    - Missing required env vars: Z_AI_API_TOKEN, TICKET_SHEPHERD_SERVER_PORT

Benefits:
- User fixes all issues in one go instead of N sequential fix-and-retry cycles
- Simpler control flow: no early returns, just collect into a list and fail once at the end
- The validation logic becomes a map/forEach + report pattern instead of nested if/throw chains
- More robust: adding a new validation check cannot accidentally short-circuit other important checks

Implementation: Collect List<String> of error messages. If non-empty, throw with formatted multi-line error. Each check becomes a pure function returning a String? (null = ok, non-null = error message).

