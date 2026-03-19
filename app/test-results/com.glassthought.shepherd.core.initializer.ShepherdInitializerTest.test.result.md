---
spec: "com.glassthought.shepherd.core.initializer.ShepherdInitializerTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN ContextInitializer that fails
  - WHEN run() is called
    - [PASS] THEN exception propagates from ContextInitializer
    - [PASS] THEN server is never started
- GIVEN TICKET_SHEPHERD_SERVER_PORT env var is not set
  - WHEN readServerPortFromEnv() is called
    - [PASS] THEN throws IllegalStateException with descriptive message
- GIVEN TicketShepherd.run() exits via FakeProcessExitException
  - WHEN run() completes (via exit)
    - [PASS] THEN server is stopped
- GIVEN a ShepherdInitializer with a ticket path and workflow name
  - WHEN run() is called
    - [PASS] THEN TicketShepherdCreator.create receives the ticket path
    - [PASS] THEN TicketShepherdCreator.create receives the workflow name
- GIVEN a ShepherdInitializer with fakes
  - WHEN run() is called
    - [PASS] THEN ContextInitializer.initialize is called
- GIVEN a ShepherdInitializer with server port 18080
  - WHEN run() is called
    - [PASS] THEN server is started on port 18080
- GIVEN server starter that throws
  - WHEN run() is called
    - [PASS] THEN ShepherdContext.close() is called (cleanup in reverse)
    - [PASS] THEN exception from server starter propagates
