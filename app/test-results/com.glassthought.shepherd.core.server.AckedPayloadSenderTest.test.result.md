---
spec: "com.glassthought.shepherd.core.server.AckedPayloadSenderTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN AckedPayloadSenderImpl
  - WHEN sendAndAwaitAck is called
    - [PASS] THEN pendingPayloadAck is set before send-keys is invoked
- GIVEN AckedPayloadSenderImpl where ACK never arrives
  - WHEN all attempts timeout without ACK
    - [PASS] THEN exception message contains payload ID
    - [PASS] THEN sendKeys was called maxAttempts times
    - [PASS] THEN throws PayloadAckTimeoutException
- GIVEN AckedPayloadSenderImpl where first attempt times out
  - WHEN ACK arrives on second attempt
    - [PASS] THEN sendAndAwaitAck returns normally (retried successfully)
    - [PASS] THEN sendKeys was called twice
- GIVEN AckedPayloadSenderImpl with counter starting at 1
  - WHEN sendAndAwaitAck is called and ACK arrives immediately
    - [PASS] THEN the wrapped payload uses the correct PayloadId from HandshakeGuid + counter
- GIVEN AckedPayloadSenderImpl with immediate ACK
  - WHEN ACK arrives immediately
    - [PASS] THEN sendAndAwaitAck returns normally
    - [PASS] THEN sendKeys was called exactly once
- GIVEN AckedPayloadSenderImpl with shared counter
  - WHEN sendAndAwaitAck is called twice
    - [PASS] THEN first call uses sequence 1 and second uses sequence 2
- GIVEN wrapPayload with a PayloadId and content
  - [PASS] THEN contains the payload content
  - [PASS] THEN ends with the closing XML tag
  - [PASS] THEN matches the exact spec format
  - [PASS] THEN opening tag contains MUST_ACK_BEFORE_PROCEEDING with exact ack command
  - [PASS] THEN starts with opening XML tag containing payload_id attribute
