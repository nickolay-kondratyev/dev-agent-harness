---
spec: "com.glassthought.shepherd.core.state.CurrentStateInitializerTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a straightforward workflow definition
  - WHEN creating initial state
    - [PASS] THEN all parts have EXECUTION phase
    - [PASS] THEN first part name is ui_design
    - [PASS] THEN has two parts
    - [PASS] THEN second part name is backend
  - WHEN inspecting doer sub-part status
    - [PASS] THEN iteration is null (doer has no iteration config)
    - [PASS] THEN sessionIds is null
    - [PASS] THEN status is NOT_STARTED
  - WHEN inspecting doer-only part (backend)
    - [PASS] THEN iteration is null
    - [PASS] THEN status is NOT_STARTED
  - WHEN inspecting reviewer sub-part status
    - [PASS] THEN iteration.current is 0
    - [PASS] THEN iteration.max is preserved from workflow definition
    - [PASS] THEN sessionIds is null
    - [PASS] THEN status is NOT_STARTED
- GIVEN a with-planning workflow definition
  - WHEN creating initial state
    - [PASS] THEN has exactly one part (planning only)
    - [PASS] THEN has two sub-parts
    - [PASS] THEN part name is planning
    - [PASS] THEN part phase is PLANNING
  - WHEN inspecting plan_reviewer sub-part
    - [PASS] THEN iteration.current is 0
    - [PASS] THEN iteration.max is 3
    - [PASS] THEN status is NOT_STARTED
  - WHEN inspecting planner sub-part
    - [PASS] THEN iteration is null (planner is a doer)
    - [PASS] THEN status is NOT_STARTED
- GIVEN workflow definition with iteration.current already set (e.g. from JSON)
  - WHEN creating initial state
    - [PASS] THEN iteration.current is reset to 0
    - [PASS] THEN iteration.max is preserved
