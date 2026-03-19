---
spec: "com.glassthought.shepherd.core.context.InstructionSectionOrderingTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a doer request (iteration 2 with reviewer feedback and plan)
  - WHEN instructions are assembled
    - [PASS] THEN doer sections appear in spec-defined order
- GIVEN a plan reviewer request (iteration 1)
  - WHEN instructions are assembled
    - [PASS] THEN plan reviewer sections appear in spec-defined order
- GIVEN a planner request (iteration 1)
  - WHEN instructions are assembled
    - [PASS] THEN planner sections appear in spec-defined order
- GIVEN a reviewer request (iteration 2 with feedback state)
  - WHEN instructions are assembled
    - [PASS] THEN reviewer sections appear in spec-defined order
