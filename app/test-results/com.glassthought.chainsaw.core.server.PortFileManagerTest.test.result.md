---
spec: "com.glassthought.chainsaw.core.server.PortFileManagerTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN PortFileManager with a temp directory
  - WHEN deletePort is called after writePort
    - [PASS] THEN port file does not exist
  - WHEN deletePort is called without prior writePort
    - [PASS] THEN does not throw
  - WHEN writePort is called
    - [PASS] THEN port file content is the port number as string
    - [PASS] THEN port file exists
