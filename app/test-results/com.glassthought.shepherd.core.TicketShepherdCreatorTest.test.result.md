---
spec: "com.glassthought.shepherd.core.TicketShepherdCreatorTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a TicketShepherdCreatorImpl with test dependencies
  - WHEN create() is called
    - [PASS] THEN invokes the AllSessionsKiller factory
    - [PASS] THEN returns a CurrentStatePersistenceImpl (correct wiring)
    - [PASS] THEN returns a result with an InterruptHandlerImpl
    - [PASS] THEN returns an initialized CurrentState with empty parts
  - WHEN create() result handler is invoked directly
    - [PASS] THEN the handler responds with confirmation message (proving wiring correctness)
