---
closed_iso: 2026-03-20T14:36:38Z
id: nid_64ddt50iaqtj9bijtidkutp3w_E
title: "align enums"
status: closed
deps: []
links: []
created_iso: 2026-03-20T14:31:30Z
status_updated_iso: 2026-03-20T14:36:38Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

Failed to start:
```
Startup failed: Invalid agentType=[ClaudeCode] for sub-part=[impl]. Valid values: [CLAUDE_CODE, PI]
```

Lets align enums to always use CLAUDE_CODE instead of ClaudeCode
## Notes

**2026-03-20T14:36:38Z**

Resolved: Changed all 'ClaudeCode' string literals to 'CLAUDE_CODE' across workflow configs, instruction text, KDoc comments, and test files. The AgentType enum uses CLAUDE_CODE but configs/docs were using ClaudeCode (camelCase), which the normalization logic (uppercase().replace(' ', '_')) could not convert correctly. All tests pass.
