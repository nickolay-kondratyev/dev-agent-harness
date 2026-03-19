---
spec: "com.glassthought.shepherd.core.TicketShepherdCreatorTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a TicketShepherdCreatorImpl with test dependencies
  - AND the InterruptHandler is installed
    - WHEN a SIGINT signal is simulated on the returned handler
      - [PASS] THEN the handler responds with confirmation message (proving install wired correctly)
  - WHEN create() is called
    - [PASS] THEN invokes the AllSessionsKiller factory
    - [PASS] THEN returns a non-null CurrentStatePersistence
    - [PASS] THEN returns a result with an InterruptHandlerImpl
    - [PASS] THEN returns an initialized CurrentState with empty parts
