---
id: nid_reck1b04pzsj8v3dmhk0f2a1t_E
title: "Add deep memory on how to setup integration tests to use GLM instead of claude"
status: open
deps: []
links: []
created_iso: 2026-03-11T20:50:54Z
status_updated_iso: 2026-03-11T20:50:54Z
type: task
priority: 3
assignee: nickolaykondratyev
---


Add deep memory on how to be able to wire up our integration tests when we are in test environment to use GLM instead of claude.

This is how we wire up Claude Code to use GLM in production shell:


```bash
claude_code.z_ai.switch_to_z_ai_glm(){
  export ANTHROPIC_BASE_URL="https://api.z.ai/api/anthropic"
  export API_TIMEOUT_MS=3000000

  # shellcheck disable=SC2155
  export ZAI_API_KEY="$(ei2 cat "${MY_ENV:?}"/.secrets/Z_AI_GLM_API_TOKEN)"
  export ANTHROPIC_AUTH_TOKEN="${ZAI_API_KEY:?}"
  export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1

  export ANTHROPIC_DEFAULT_OPUS_MODEL="${BEST_Z_AI_MODEL:?}"
  export ANTHROPIC_DEFAULT_SONNET_MODEL="${BEST_Z_AI_MODEL:?}"
  export ANTHROPIC_DEFAULT_HAIKU_MODEL="${BEST_Z_FAST_MODEL:?}"
}
export -f claude_code.z_ai.switch_to_z_ai_glm
```

We should be able to something like it when we stand up tmux sessions in our integration tests.

This task is to create DEEP memory that 
1) we prefer to use GLM for integration in tests of running actual agents.
2) setup a reference to above setup on how we have set up claude code to run with GLM successfully in shell.


no-review