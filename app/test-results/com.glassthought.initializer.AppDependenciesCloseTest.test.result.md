---
spec: "com.glassthought.initializer.AppDependenciesCloseTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN AppDependencies
  - WHEN close() is called
    - [PASS] THEN OkHttpClient dispatcher executor service is shut down
