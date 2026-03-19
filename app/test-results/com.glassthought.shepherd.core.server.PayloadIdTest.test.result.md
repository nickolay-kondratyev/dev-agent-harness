---
spec: "com.glassthought.shepherd.core.server.PayloadIdTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a HandshakeGuid and counter starting at 1
  - WHEN generate is called
    - [PASS] THEN produces correct format {8chars}-{seq}
    - [PASS] THEN toString returns raw value
- GIVEN a HandshakeGuid and counter starting at 1 for sequential calls
  - WHEN generate is called three times
    - [PASS] THEN first payload has sequence 1
    - [PASS] THEN second payload has sequence 2
    - [PASS] THEN third payload has sequence 3
- GIVEN a HandshakeGuid with handshake. prefix
  - WHEN generate is called
    - [PASS] THEN shortGuid is first 8 chars after prefix removal
