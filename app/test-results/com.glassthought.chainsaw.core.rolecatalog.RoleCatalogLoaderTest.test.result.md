---
spec: "com.glassthought.chainsaw.core.rolecatalog.RoleCatalogLoaderTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a catalog directory with a role missing description
  - WHEN load is called
    - [PASS] THEN error message contains the filename
    - [PASS] THEN throws IllegalArgumentException
- GIVEN a catalog directory with a single role
  - WHEN load is called
    - [PASS] THEN returns a list with exactly 1 role
    - [PASS] THEN the role name matches the filename without extension
- GIVEN a non-existent directory path
  - WHEN load is called
    - [PASS] THEN error message contains the directory path
    - [PASS] THEN throws IllegalArgumentException
- GIVEN a valid catalog directory with multiple roles
  - WHEN load is called
    - [PASS] THEN IMPLEMENTOR has correct description
    - [PASS] THEN IMPLEMENTOR has descriptionLong populated
    - [PASS] THEN REVIEWER has description populated
    - [PASS] THEN REVIEWER has null descriptionLong
    - [PASS] THEN contains a role named IMPLEMENTOR
    - [PASS] THEN contains a role named REVIEWER
    - [PASS] THEN each role has a filePath ending with its filename
    - [PASS] THEN returns a list with 2 roles
- GIVEN an empty catalog directory (no .md files)
  - WHEN load is called
    - [PASS] THEN error message indicates no .md files found
    - [PASS] THEN throws IllegalArgumentException
