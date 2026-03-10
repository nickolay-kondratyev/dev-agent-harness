---
id: nid_ab58clog693kjma16mde2pyrb_E
title: "Migrate chainsaw Out/Val usages to ValTypeV2"
status: open
deps: []
links: []
created_iso: 2026-03-10T17:16:51Z
status_updated_iso: 2026-03-10T17:16:51Z
type: task
priority: 3
assignee: nickolaykondratyev
tags: [asgardCore]
---

The Val class now has ValTypeV2 as its primary type (ValType enum is backward-compat via constructor overload).

The CLAUDE.md standards say to use specific ValTypes semantically. Now that we are consuming asgardCore from maven local (not composite build), we should migrate away from the old ValType enum to project-specific ValTypeV2 instances.

Currently 9 files use ValType in app/src:
- app/src/main/kotlin/com/glassthought/chainsaw/core/directLLMApi/glm/GLMHighestTierApi.kt
- app/src/main/kotlin/com/glassthought/chainsaw/core/processRunner/InteractiveProcessRunner.kt
- app/src/main/kotlin/com/glassthought/chainsaw/core/tmux/TmuxCommunicator.kt
- app/src/main/kotlin/com/glassthought/chainsaw/core/tmux/TmuxSessionManager.kt
- (and others)

Goal: Create chainsaw-specific ValTypeV2 constants (e.g. in a ChainsawValType object) for types like TMUX_SESSION_NAME, TMUX_COMMAND, LLM_MODEL_NAME etc., and update Val usages to use them.

Existing code compiles and works with ValType - this is a code quality improvement only.

