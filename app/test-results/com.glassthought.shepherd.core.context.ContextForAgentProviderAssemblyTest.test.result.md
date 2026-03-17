---
spec: "com.glassthought.shepherd.core.context.ContextForAgentProviderAssemblyTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a DOER request with null partName
  - [PASS] THEN assembleInstructions throws IllegalArgumentException
- GIVEN a PLANNER request with null planJsonOutputPath
  - [PASS] THEN assembleInstructions throws IllegalArgumentException
- GIVEN a PLAN_REVIEWER request with null planJsonContent
  - [PASS] THEN assembleInstructions throws IllegalArgumentException
- GIVEN a REVIEWER request with null partName
  - [PASS] THEN assembleInstructions throws IllegalArgumentException
- GIVEN a doer request on iteration 1
  - WHEN instructions are assembled
    - [PASS] THEN contains WHY-NOT keyword
    - [PASS] THEN does NOT include pushback guidance (first iteration)
- GIVEN a doer request on iteration 2 with reviewer feedback
  - WHEN instructions are assembled
    - [PASS] THEN includes pushback guidance
    - [PASS] THEN includes reviewer's PUBLIC.md content
- GIVEN a doer request with a plan (with-planning workflow)
  - WHEN instructions are assembled
    - [PASS] THEN includes PLAN.md content
- GIVEN a doer request with prior PUBLIC.md files
  - WHEN instructions are assembled
    - [PASS] THEN includes Prior Agent Outputs header
    - [PASS] THEN includes prior PUBLIC.md content
- GIVEN a doer request without a plan (no-planning workflow)
  - WHEN instructions are assembled
    - [PASS] THEN does NOT include plan section header
- GIVEN a reviewer request on iteration 1
  - WHEN instructions are assembled
    - [PASS] THEN does NOT include addressed/rejected feedback headers (first iteration)
    - [PASS] THEN includes doer's output for review
    - [PASS] THEN includes feedback writing instructions
    - [PASS] THEN includes structured feedback format guidance
- GIVEN a reviewer request on iteration 2 with feedback state
  - WHEN instructions are assembled
    - [PASS] THEN includes addressed feedback header
    - [PASS] THEN includes rejected feedback header
- GIVEN instructions are assembled for any agent
  - WHEN the file is written
    - [PASS] THEN sections are separated by horizontal rules
    - [PASS] THEN the file is named instructions.md
