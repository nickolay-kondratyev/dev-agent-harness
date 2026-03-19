---
spec: "com.glassthought.shepherd.core.supporting.git.GitCommandBuilderTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN no workingDir (null)
  - WHEN build is called with args
    - [PASS] THEN returns git command without -C flag
  - WHEN build is called with no args
    - [PASS] THEN returns array with only git
- GIVEN workingDir is specified
  - WHEN build is called with args
    - [PASS] THEN prepends -C <workingDir> before args
  - WHEN build is called with no args
    - [PASS] THEN returns git with -C <workingDir>
- GIVEN workingDir with spaces in path
  - WHEN build is called
    - [PASS] THEN preserves the path with spaces as a single element
