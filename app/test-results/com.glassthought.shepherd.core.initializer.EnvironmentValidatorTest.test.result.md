---
spec: "com.glassthought.shepherd.core.initializer.EnvironmentValidatorTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN EnvironmentValidator running in Docker with all env vars present
  - WHEN validate is called
    - [PASS] THEN does not throw
- GIVEN EnvironmentValidator running outside Docker
  - WHEN validate is called
    - [PASS] THEN throws IllegalStateException with Docker message
- GIVEN EnvironmentValidator with missing env vars
  - WHEN validate is called
    - [PASS] THEN throws IllegalStateException listing all missing env vars
- GIVEN EnvironmentValidator with one blank env var
  - WHEN validate is called
    - [PASS] THEN throws IllegalStateException listing the blank env var
