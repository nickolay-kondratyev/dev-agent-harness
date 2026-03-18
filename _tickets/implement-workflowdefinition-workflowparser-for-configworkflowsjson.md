---
id: nid_kavqh23pdfq56cdli0fv4sm3u_E
title: "Implement WorkflowDefinition + WorkflowParser for config/workflows/*.json"
status: open
deps: [nid_m3cm8xizw5qhu1cu3454rca79_E]
links: []
created_iso: 2026-03-18T18:02:56Z
status_updated_iso: 2026-03-18T18:02:56Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [plan-current-state, workflow, parser]
---

Implement the workflow JSON parser from the plan-and-current-state spec (ref.ap.56azZbk7lAMll0D4Ot2G0.E, lines 454-514).

## What to implement

### 1. WorkflowDefinition data class (spec lines 504-514)
A workflow is either **straightforward** or **with-planning** — mutually exclusive.

```kotlin
data class WorkflowDefinition(
    val name: String,
    val parts: List<Part>? = null,              // straightforward: full execution plan
    val planningParts: List<Part>? = null,       // with-planning: planning loop definition
    val executionPhasesFrom: String? = null,     // with-planning: "plan_flow.json"
) {
    val isWithPlanning: Boolean get() = planningParts \!= null
    val isStraightforward: Boolean get() = parts \!= null

    init {
        require((parts \!= null) xor (planningParts \!= null)) {
            "Workflow must have exactly one of parts (straightforward) or planningParts (with-planning)"
        }
        if (planningParts \!= null) {
            requireNotNull(executionPhasesFrom) { "with-planning workflow requires executionPhasesFrom" }
        }
    }
}
```

### 2. WorkflowParser (interface + impl)
```kotlin
interface WorkflowParser {
    suspend fun parse(workflowName: String): WorkflowDefinition
}
```
- Loads `config/workflows/<workflowName>.json` from the working directory
- Uses Jackson ObjectMapper (from shared factory created in data model ticket)
- Fail-fast if file not found or JSON is malformed
- Validate all parts have required fields
- For straightforward: validate all parts have `phase: "execution"`
- For with-planning: validate planningParts have `phase: "planning"`

### 3. Tests
- Parse actual `config/workflows/straightforward.json` — verify structure matches spec
- Parse actual `config/workflows/with-planning.json` — verify structure matches spec
- Test fail-fast on missing workflow file
- Test fail-fast on malformed JSON
- Test mutual exclusivity validation (cannot have both parts and planningParts)
- Test with-planning without executionPhasesFrom fails
- Test straightforward parts all have phase=execution

## Package
`com.glassthought.shepherd.core.workflow`

## Files to read
- `doc/schema/plan-and-current-state.md` — spec lines 454-514
- `config/workflows/straightforward.json` — actual config file
- `config/workflows/with-planning.json` — actual config file
- `app/build.gradle.kts` — Jackson dependencies

