---
spec: "com.glassthought.shepherd.core.interrupt.InterruptHandlerTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN expired first press followed by confirmed double press
  - WHEN third signal arrives within 2 seconds of second
    - [PASS] THEN triggers cleanup and exit (second signal became new first)
- GIVEN first Ctrl+C was pressed
  - AND second Ctrl+C arrives after 2+ seconds
    - WHEN the second signal is received
      - [PASS] THEN does NOT exit the process
      - [PASS] THEN does NOT kill sessions
      - [PASS] THEN prints confirmation message again (treated as fresh first press)
  - AND second Ctrl+C arrives at exactly 1999ms (just under 2 seconds)
    - WHEN the second signal is received
      - [PASS] THEN triggers cleanup and exits
  - AND second Ctrl+C arrives at exactly 2 seconds
    - WHEN the second signal is received
      - [PASS] THEN treats it as fresh first press (window expired)
  - AND second Ctrl+C arrives within 2 seconds
    - WHEN the second signal is received
      - [PASS] THEN exits with non-zero code
      - [PASS] THEN flushes state to disk
      - [PASS] THEN kills all sessions
- GIVEN no prior Ctrl+C press
  - WHEN first signal is received
    - [PASS] THEN does NOT exit the process
    - [PASS] THEN does NOT flush state to disk
    - [PASS] THEN does NOT kill sessions
    - [PASS] THEN prints confirmation message in red
- GIVEN state has no parts (empty state)
  - WHEN cleanup is triggered via double Ctrl+C
    - [PASS] THEN exits without errors
    - [PASS] THEN still flushes state to disk
- GIVEN state has sub-parts with mixed statuses
  - WHEN cleanup is triggered via double Ctrl+C
    - [PASS] THEN marks only IN_PROGRESS sub-parts as FAILED
