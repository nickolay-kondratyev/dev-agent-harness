---
spec: "com.glassthought.shepherd.core.workflow.WorkflowDefinitionTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a straightforward WorkflowDefinition
  - [PASS] THEN isStraightforward is true
  - [PASS] THEN isWithPlanning is false
  - [PASS] THEN parts is not null
- GIVEN a straightforward WorkflowDefinition with allPartsForStructure()
  - [PASS] THEN allPartsForStructure() returns execution parts
  - [PASS] THEN allPartsForStructure() returns the expected number of parts
- GIVEN a straightforward workflow with executionPhasesFrom specified
  - [PASS] THEN construction fails with IllegalArgumentException
- GIVEN a with-planning WorkflowDefinition
  - [PASS] THEN executionPhasesFrom is set
  - [PASS] THEN isStraightforward is false
  - [PASS] THEN isWithPlanning is true
- GIVEN a with-planning WorkflowDefinition with allPartsForStructure()
  - [PASS] THEN allPartsForStructure() returns planning parts
  - [PASS] THEN allPartsForStructure() returns the expected number of parts
- GIVEN both parts and planningParts are provided
  - [PASS] THEN construction fails with IllegalArgumentException
- GIVEN neither parts nor planningParts are provided
  - [PASS] THEN construction fails with IllegalArgumentException
- GIVEN planningParts without executionPhasesFrom
  - [PASS] THEN construction fails with IllegalArgumentException
