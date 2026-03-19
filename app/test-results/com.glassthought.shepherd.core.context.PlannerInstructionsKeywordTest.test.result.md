---
spec: "com.glassthought.shepherd.core.context.PlannerInstructionsKeywordTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a planner instruction request on iteration 1
  - WHEN instructions are assembled
    - [PASS] THEN contains 'completed' done result
    - [PASS] THEN contains 'validate-plan' query instruction
    - [PASS] THEN contains PLAN.md output path
    - [PASS] THEN contains PLANNER role name
    - [PASS] THEN contains agent types and models section
    - [PASS] THEN contains callback query script name (planner validates plan)
    - [PASS] THEN contains callback signal script name
    - [PASS] THEN contains payload ACK tag
    - [PASS] THEN contains plan format instructions
    - [PASS] THEN contains plan_flow.json output path
    - [PASS] THEN contains role catalog with IMPLEMENTOR
    - [PASS] THEN contains role catalog with REVIEWER
    - [PASS] THEN contains ticket content
