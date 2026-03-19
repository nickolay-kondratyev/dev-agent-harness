---
id: nid_a93jjhotbiv75jqm9ts3ne3e0_E
title: "Implement CommitAuthorBuilder + CommitMessageBuilder — git commit metadata utilities"
status: in_progress
deps: [nid_m3cm8xizw5qhu1cu3454rca79_E]
links: []
created_iso: 2026-03-18T23:44:37Z
status_updated_iso: 2026-03-19T15:26:39Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [git, commit]
---

Implement two small utility classes for building git commit metadata.

## Spec Reference
- doc/core/git.md lines 135-192

## CommitMessageBuilder
Builds commit messages in the format:
```
[shepherd] {part_name}/{sub_part_name} — {result} (iteration {N}/{max})
```

### Rules
- `[shepherd]` prefix — identifies harness-generated commits
- Iteration info included only when the part has a reviewer (determined by `hasReviewer: Boolean` parameter — caller resolves this from SubPart config)
- Single sub-part parts (no reviewer) omit the iteration suffix
- Planning phase uses synthetic part name `planning`

### Examples
```
[shepherd] planning/plan — completed
[shepherd] planning/plan_review — pass (iteration 1/3)
[shepherd] ui_design/impl — completed (iteration 1/3)
[shepherd] ui_design/review — needs_iteration (iteration 1/3)
[shepherd] backend_impl/impl — completed
```

## CommitAuthorBuilder
Builds commit author name in the format:
```
${CODING_AGENT}_${CODING_MODEL}_WITH-${HOST_USERNAME}
```

### Mapping
| Component | Source |
|---|---|
| CODING_AGENT | Short code from agentType: ClaudeCode → CC, PI → PI |
| CODING_MODEL | model field from session record (sonnet, opus, glm-5) |
| HOST_USERNAME | HOST_USERNAME env var |

### Examples
```
CC_sonnet_WITH-nickolaykondratyev
CC_opus_WITH-nickolaykondratyev
```

### Notes
- Commit email stays as-is (uses git config)
- Only author name is overridden per commit via `git commit --author`
- No model version in author (YAGNI — deferred to V2)

## Location
- `com.glassthought.shepherd.core.supporting.git` package
- Follow existing patterns (object or data class, see BranchNameBuilder)

## Testing
- Unit tests for CommitMessageBuilder: with/without reviewer, planning phase, various results
- Unit tests for CommitAuthorBuilder: different agent types, models, host usernames
- Data-driven tests with multiple input/output combos

## Existing Code Context
- BranchNameBuilder at app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/BranchNameBuilder.kt — follow similar pattern
- Constants at app/src/main/kotlin/com/glassthought/shepherd/core/Constants.kt — HOST_USERNAME is in REQUIRED_ENV_VARS
- Part/SubPart data classes: ticket nid_m3cm8xizw5qhu1cu3454rca79_E

