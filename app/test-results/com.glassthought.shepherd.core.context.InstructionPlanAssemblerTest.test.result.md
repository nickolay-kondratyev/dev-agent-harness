---
spec: "com.glassthought.shepherd.core.context.InstructionPlanAssemblerTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a plan containing sections that return null
  - WHEN assembled
    - [PASS] THEN does not produce stray separators for skipped sections
    - [PASS] THEN skips null sections (no Prior Session Context)
- GIVEN a plan with OutputPathSection
  - WHEN assembled
    - [PASS] THEN includes the concrete path value
    - [PASS] THEN includes the output path section
- GIVEN a plan with PartContext for a PlannerRequest (returns null)
  - WHEN assembled
    - [PASS] THEN PartContext is skipped for planner
    - [PASS] THEN Role and Ticket are still present
- GIVEN a plan with multiple sections
  - WHEN assembled
    - [PASS] THEN renders sections in order
    - [PASS] THEN sections are separated by horizontal rule
    - [PASS] THEN writes to instructions.md inside outputDir
- GIVEN an empty plan
  - WHEN assembled
    - [PASS] THEN produces empty file
