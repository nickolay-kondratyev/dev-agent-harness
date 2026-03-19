---
id: nid_ptsnp5tmbfhpmz6ighcnmrfeo_E
title: "Wire NonInteractiveAgentRunner into ShepherdContext via ContextInitializer"
status: in_progress
deps: [nid_njq7ezzxmf8orffmzf7oorsd0_E]
links: []
created_iso: 2026-03-18T23:56:13Z
status_updated_iso: 2026-03-19T17:56:06Z
type: feature
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [agent, noninteractive, wiring]
---

Wire the NonInteractiveAgentRunner into the existing dependency graph per spec at doc/core/NonInteractiveAgentRunner.md (ref.ap.ad4vG4G2xMPiMHRreoYVr.E).

## Scope

### Wiring Changes
1. Add `NonInteractiveAgentRunner` as a **top-level property** on `ShepherdContext` (app/src/main/kotlin/com/glassthought/shepherd/core/initializer/data/ShepherdContext.kt, ref.ap.TkpljsXvwC6JaAVnIq02He98.E). It is NOT an infra component — it's a use-case-level dependency that consumers access directly.
2. Create and wire `NonInteractiveAgentRunnerImpl` in `ContextInitializerImpl` (app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt, ref.ap.9zump9YISPSIcdnxEXZZX.E)
3. Read ZAI_API_KEY from `${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN` during `initializeImpl()` — **fail hard** with `IllegalStateException` if the file does not exist or is empty (consistent with other startup guards in EnvironmentValidator)

### Environment Variable Validation
- Add `AI_MODEL__ZAI__FAST` to `Constants.REQUIRED_ENV_VARS.ALL` — use a **named constant** (e.g., `const val AI_MODEL_ZAI_FAST = "AI_MODEL__ZAI__FAST"`) following the existing pattern in Constants.kt, not an inline string
- This env var provides the model name for PI agent utility tasks (e.g., glm-4.7-flash)

### Testing
- Update existing ContextInitializer tests to verify NonInteractiveAgentRunner is wired
- Verify ShepherdContext exposes the runner

### Key Files
- app/src/main/kotlin/com/glassthought/shepherd/core/initializer/data/ShepherdContext.kt
- app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt
- app/src/main/kotlin/com/glassthought/shepherd/core/initializer/EnvironmentValidator.kt (for env var validation)
- app/src/main/kotlin/com/glassthought/shepherd/core/Constants.kt (for REQUIRED_ENV_VARS)

