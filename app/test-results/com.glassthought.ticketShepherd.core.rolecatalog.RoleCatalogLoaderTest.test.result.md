---
spec: "com.glassthought.ticketShepherd.core.rolecatalog.RoleCatalogLoaderTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a non-existent directory path
  - WHEN load is called
    - [PASS] THEN error message contains the directory path
    - [PASS] THEN throws IllegalArgumentException
