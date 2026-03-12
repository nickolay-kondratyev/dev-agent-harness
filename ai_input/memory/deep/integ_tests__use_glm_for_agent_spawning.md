---
desc: "Integration tests that spawn real Claude Code agents MUST use GLM (Z.AI) instead of Claude. Documents the env-var injection mechanism."
---

## Integration Tests: Use GLM for Agent Spawning

### Preference

Integration tests that spawn **real Claude Code agents** (via tmux sessions) MUST use **GLM (Z.AI)**
instead of the real Claude API. Reasons: cost savings, no Anthropic quota consumed, GLM subscription
already available via `Z_AI_GLM_API_TOKEN` (which the harness already requires for its own direct
LLM calls).

### Mechanism: How Claude Code Is Redirected to GLM

Claude Code supports an Anthropic-compatible API. Setting the following env vars before launching
`claude` makes it transparently use GLM instead:

```bash
# Redirect claude to Z.AI's Anthropic-compatible endpoint
export ANTHROPIC_BASE_URL="https://api.z.ai/api/anthropic"
export ANTHROPIC_AUTH_TOKEN="${Z_AI_GLM_API_TOKEN}"   # token already required by the harness
export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1

# Map Claude model aliases to GLM models
export ANTHROPIC_DEFAULT_OPUS_MODEL="glm-5"           # or ${BEST_Z_AI_MODEL} in shell
export ANTHROPIC_DEFAULT_SONNET_MODEL="glm-5"         # test mode uses --model sonnet
export ANTHROPIC_DEFAULT_HAIKU_MODEL="glm-4-flash"    # or ${BEST_Z_FAST_MODEL} in shell
```

This mirrors `claude_code.z_ai.switch_to_z_ai_glm()` from the production dev shell — the same
env-var pattern that successfully redirects Claude Code to GLM in the engineer's shell environment.

### Implementation Hook

When implementing GLM injection for test spawned agents:

**Where**: `ClaudeCodeAgentStarter.buildStartCommand()` (ref.ap file:
`app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/starter/impl/ClaudeCodeAgentStarter.kt`)

The current command template is:
```bash
bash -c 'cd <workingDir> && unset CLAUDECODE && claude --model sonnet ...'
```

For test environments, prepend the GLM env-var exports:
```bash
bash -c 'export ANTHROPIC_BASE_URL=... && export ANTHROPIC_AUTH_TOKEN=... && cd <workingDir> && unset CLAUDECODE && claude ...'
```

**Trigger**: `ClaudeCodeAgentStarterBundleFactory` already receives `environment: Environment`.
Use `environment.isTest` to conditionally inject the GLM env vars. The API token is already
present in the process env as `Z_AI_GLM_API_TOKEN` (required by `SharedContextIntegFactory`
via `Constants.Z_AI_API.API_TOKEN_ENV_VAR`).

### Token Availability

`Z_AI_GLM_API_TOKEN` MUST already be set in the environment for integration tests to run (it is
required by `ContextInitializer` (ref.ap.9zump9YISPSIcdnxEXZZX.E) for the harness's own direct LLM calls). No additional secrets setup is
needed — this same token is reused for agent spawning.

### NOT Yet Implemented

As of 2026-03-11, GLM injection for tmux-spawned agents is **not yet implemented**. The deep memory
above documents the intended approach. See `TODO(ap.ifrXkqXjkvAajrA4QCy7V.E)` in `ContextInitializer.kt`
which is a placeholder for swapping external services in test mode.
