---
spec: "com.glassthought.chainsaw.core.server.KtorHarnessServerTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a KtorHarnessServer that has not been started
  - [PASS] THEN port() throws IllegalStateException
- GIVEN a KtorHarnessServer with a recording handler
  - AND POST /agent/done is called
    - [PASS] THEN onDone is invoked with the correct branch
    - [PASS] THEN onDone receives the correct branch value
  - AND POST /agent/question is called
    - [PASS] THEN response body contains the handler's answer
- GIVEN a started KtorHarnessServer
  - AND POST /agent/done is called with malformed JSON
    - [PASS] THEN response status is 400
  - AND POST /agent/done is called with valid JSON
    - [PASS] THEN response status is 200
  - AND POST /agent/done response body
    - [PASS] THEN response body is {"status":"ok"}
  - AND POST /agent/failed is called with valid JSON
    - [PASS] THEN response status is 200
  - AND POST /agent/question is called with valid JSON
    - [PASS] THEN response status is 200
  - AND POST /agent/status is called with valid JSON
    - [PASS] THEN response status is 200
  - AND port file management
    - [PASS] THEN bound port is in valid TCP range (1-65535)
    - [PASS] THEN port file contains the actual bound port as a number
    - [PASS] THEN port file exists after start
  - AND start() is called a second time
    - [PASS] THEN throws IllegalStateException
  - AND the server is closed
    - [PASS] THEN port file is deleted after close
    - [PASS] THEN port() throws IllegalStateException after close
    - [PASS] THEN second close() does not throw (idempotent)
