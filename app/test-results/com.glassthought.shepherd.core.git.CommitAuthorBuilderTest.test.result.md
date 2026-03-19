---
spec: "com.glassthought.shepherd.core.git.CommitAuthorBuilderTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN build
  - WHEN called with AgentType.CLAUDE_CODE
    - AND model='glm-5', hostUsername='johndoe'
      - [PASS] THEN returns 'CC_glm-5_WITH-johndoe'
    - AND model='opus', hostUsername='nickolaykondratyev'
      - [PASS] THEN returns 'CC_opus_WITH-nickolaykondratyev'
    - AND model='sonnet', hostUsername='nickolaykondratyev'
      - [PASS] THEN returns 'CC_sonnet_WITH-nickolaykondratyev'
  - WHEN called with AgentType.PI
    - AND model='sonnet', hostUsername='nickolaykondratyev'
      - [PASS] THEN returns 'PI_sonnet_WITH-nickolaykondratyev'
  - WHEN called with invalid inputs
    - [PASS] THEN throws IllegalArgumentException for blank hostUsername
    - [PASS] THEN throws IllegalArgumentException for blank model
