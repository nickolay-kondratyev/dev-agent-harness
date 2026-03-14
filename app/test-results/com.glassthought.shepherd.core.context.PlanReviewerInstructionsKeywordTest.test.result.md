---
spec: "com.glassthought.shepherd.core.context.PlanReviewerInstructionsKeywordTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a plan reviewer instruction request on iteration 1
  - WHEN instructions are assembled
    - [PASS] THEN contains 'needs_iteration' done result
    - [PASS] THEN contains 'pass' done result
    - [PASS] THEN contains 'validate-plan' query instruction
    - [PASS] THEN contains PLAN.md content
    - [PASS] THEN contains PLAN_REVIEWER role name
    - [PASS] THEN contains agent types and models for validation
    - [PASS] THEN contains callback query script name (plan reviewer validates plan)
    - [PASS] THEN contains callback signal script name
    - [PASS] THEN contains payload ACK tag
    - [PASS] THEN contains plan.json content
    - [PASS] THEN contains planner's PUBLIC.md content
    - [PASS] THEN contains ticket content
