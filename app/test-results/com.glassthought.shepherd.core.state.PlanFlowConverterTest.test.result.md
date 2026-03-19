---
spec: "com.glassthought.shepherd.core.state.PlanFlowConverterTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a plan_flow.json containing a planning-phase part
  - WHEN convertAndAppend is called
    - [PASS] THEN exception message includes the offending part name
    - [PASS] THEN throws PlanConversionException mentioning phase execution
- GIVEN a plan_flow.json with empty parts array
  - WHEN convertAndAppend is called
    - [PASS] THEN throws PlanConversionException mentioning at least one part
- GIVEN a plan_flow.json with mixed execution and planning phases
  - WHEN convertAndAppend is called
    - [PASS] THEN throws PlanConversionException
- GIVEN a plan_flow.json with runtime fields already present
  - WHEN convertAndAppend is called
    - [PASS] THEN does not throw
    - [PASS] THEN status is re-initialized to NOT_STARTED
- GIVEN a plan_flow.json with two execution parts
  - WHEN convertAndAppend is called
    - [PASS] THEN all sub-parts have status NOT_STARTED
    - [PASS] THEN first part name is ui_design
    - [PASS] THEN returns two execution parts
    - [PASS] THEN second part name is backend
- GIVEN a valid plan_flow.json with execution part
  - WHEN convertAndAppend is called
    - [PASS] THEN execution sub-part private directory is created
    - [PASS] THEN feedback pending directory is created for the part
- GIVEN a valid plan_flow.json with one execution part
  - AND a CurrentState with an existing planning part
    - WHEN convertAndAppend is called
      - [PASS] THEN currentState first part is the original planning part
      - [PASS] THEN currentState has two parts total (planning + execution appended)
      - [PASS] THEN currentState second part is the appended execution part
      - [PASS] THEN current_state.json is flushed to disk
      - [PASS] THEN impl subPart status is NOT_STARTED
      - [PASS] THEN plan_flow.json is deleted
      - [PASS] THEN returned part has two subParts
      - [PASS] THEN returned part name is ui_design
      - [PASS] THEN returned part phase is EXECUTION
      - [PASS] THEN returns one execution part
      - [PASS] THEN review subPart iteration.current is 0
      - [PASS] THEN review subPart iteration.max is 3
      - [PASS] THEN review subPart status is NOT_STARTED
- GIVEN malformed plan_flow.json
  - WHEN convertAndAppend is called
    - [PASS] THEN throws PlanConversionException
