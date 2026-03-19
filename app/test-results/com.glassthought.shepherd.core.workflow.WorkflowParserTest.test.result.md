---
spec: "com.glassthought.shepherd.core.workflow.WorkflowParserTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a malformed JSON workflow file
  - WHEN parsing 'malformed'
    - [PASS] THEN throws IllegalArgumentException with 'Failed to parse'
- GIVEN a non-existent workflow file
  - WHEN parsing 'nonexistent-workflow'
    - [PASS] THEN throws IllegalArgumentException with 'not found'
- GIVEN a straightforward workflow file with planning phase
  - WHEN parsing 'bad-phases'
    - [PASS] THEN throws IllegalArgumentException about phase='execution'
- GIVEN a with-planning workflow file with execution phase in planningParts
  - WHEN parsing 'bad-planning-phases'
    - [PASS] THEN throws IllegalArgumentException about phase='planning'
- GIVEN the actual straightforward.json workflow file
  - WHEN parsing 'straightforward'
    - [PASS] THEN first subPart role is IMPLEMENTATION_WITH_SELF_PLAN
    - [PASS] THEN has one part
    - [PASS] THEN isStraightforward is true
    - [PASS] THEN isWithPlanning is false
    - [PASS] THEN name is 'straightforward'
    - [PASS] THEN part has two subParts
    - [PASS] THEN part name is 'main'
    - [PASS] THEN part phase is EXECUTION
    - [PASS] THEN second subPart has iteration max of 4
- GIVEN the actual with-planning.json workflow file
  - WHEN parsing 'with-planning'
    - [PASS] THEN executionPhasesFrom is 'plan_flow.json'
    - [PASS] THEN first subPart role is PLANNER
    - [PASS] THEN has one planningPart
    - [PASS] THEN isStraightforward is false
    - [PASS] THEN isWithPlanning is true
    - [PASS] THEN name is 'with-planning'
    - [PASS] THEN plan_review iteration max is 3
    - [PASS] THEN planningPart has two subParts
    - [PASS] THEN planningPart phase is PLANNING
    - [PASS] THEN second subPart role is PLAN_REVIEWER
