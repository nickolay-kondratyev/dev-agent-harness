---
id: nid_7taro0wu8r440in4hoosv3rhj_E
title: "Rebuild agent spawn flow against new sub-parts schema"
status: open
deps: []
links: []
created_iso: 2026-03-11T14:27:13Z
status_updated_iso: 2026-03-11T14:27:13Z
type: task
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [schema, agent-spawn]
---

PhaseType enum (app/src/main/kotlin/com/glassthought/chainsaw/core/data/PhaseType.kt) is a hardcoded set of role categories (IMPLEMENTOR, REVIEWER, PLANNER, PLAN_REVIEWER). In the new schema, roles are open-ended strings from the role catalog ($CHAINSAW_AGENTS_DIR/*.md).

## Changes needed

1. Replace PhaseType enum with role string (from sub-part) in StartAgentRequest (app/src/main/kotlin/com/glassthought/chainsaw/core/agent/data/StartAgentRequest.kt)
2. Update SpawnTmuxAgentSessionUseCase and callers to pass role string instead of PhaseType
3. Update session naming to use role string for debuggability
4. Delete PhaseType.kt
5. Update affected tests:
   - app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt
   - app/src/test/kotlin/com/glassthought/chainsaw/core/agent/impl/ClaudeCodeAgentStarterBundleFactoryTest.kt
   - app/src/test/kotlin/com/glassthought/chainsaw/core/agent/DefaultAgentTypeChooserTest.kt

## Context

The new unified schema uses parts/subParts where each sub-part has a role string. See doc/schema/plan-and-current-state.md (ref.ap.56azZbk7lAMll0D4Ot2G0.E) for the schema.

