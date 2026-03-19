---
spec: "com.glassthought.shepherd.core.git.CommitMessageBuilderTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN build
  - WHEN called with invalid inputs
    - [PASS] THEN throws IllegalArgumentException for blank partName
    - [PASS] THEN throws IllegalArgumentException for blank result
    - [PASS] THEN throws IllegalArgumentException for blank subPartName
    - [PASS] THEN throws IllegalArgumentException for currentIteration < 1 when hasReviewer=true
    - [PASS] THEN throws IllegalArgumentException for maxIterations < 1 when hasReviewer=true
  - WHEN called with reviewer (hasReviewer=true)
    - AND iteration 2/3
      - [PASS] THEN includes iteration 2/3 in the message
    - AND partName='planning', subPartName='plan_review', result='pass', iteration 1/3
      - [PASS] THEN returns '[shepherd] planning/plan_review — pass (iteration 1/3)'
    - AND partName='ui_design', subPartName='impl', result='completed', iteration 1/3
      - [PASS] THEN returns '[shepherd] ui_design/impl — completed (iteration 1/3)'
    - AND partName='ui_design', subPartName='review', result='needs_iteration', iteration 1/3
      - [PASS] THEN returns '[shepherd] ui_design/review — needs_iteration (iteration 1/3)'
  - WHEN called without reviewer (hasReviewer=false)
    - AND partName='backend_impl', subPartName='impl', result='completed'
      - [PASS] THEN returns '[shepherd] backend_impl/impl — completed'
    - AND partName='planning', subPartName='plan', result='completed'
      - [PASS] THEN returns '[shepherd] planning/plan — completed'
    - [PASS] THEN omits iteration info
