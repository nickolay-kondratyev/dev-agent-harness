---
spec: "com.glassthought.shepherd.usecase.ticketstatus.TicketStatusUpdaterImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a TicketStatusUpdaterImpl with ticketId 'abc-123'
  - WHEN markDone is called
    - [PASS] THEN it invokes 'ticket close abc-123' via ProcessRunner
- GIVEN a TicketStatusUpdaterImpl with ticketId 'nid_xyz_task'
  - WHEN markDone is called
    - [PASS] THEN it invokes 'ticket close nid_xyz_task' via ProcessRunner
