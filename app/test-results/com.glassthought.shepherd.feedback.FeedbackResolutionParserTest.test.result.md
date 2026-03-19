---
spec: "com.glassthought.shepherd.feedback.FeedbackResolutionParserTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN feedback file with '## Resolution: ADDRESSED'
  - WHEN parse is called
    - [PASS] THEN resolution is ADDRESSED
    - [PASS] THEN result is Found
- GIVEN feedback file with '## Resolution: INVALID_VALUE'
  - WHEN parse is called
    - [PASS] THEN rawValue is 'INVALID_VALUE'
    - [PASS] THEN result is InvalidMarker
- GIVEN feedback file with '## Resolution: REJECTED'
  - WHEN parse is called
    - [PASS] THEN resolution is REJECTED
    - [PASS] THEN result is Found
- GIVEN feedback file with '## Resolution: SKIPPED'
  - WHEN parse is called
    - [PASS] THEN resolution is SKIPPED
    - [PASS] THEN result is Found
- GIVEN feedback file with '## Resolution:' but empty keyword
  - WHEN parse is called
    - [PASS] THEN result is MissingMarker
- GIVEN feedback file with lowercase '## Resolution: addressed' (case insensitivity)
  - WHEN parse is called
    - [PASS] THEN result is Found with ADDRESSED
- GIVEN feedback file with resolution marker embedded in longer content
  - WHEN parse is called
    - [PASS] THEN resolution is ADDRESSED
    - [PASS] THEN result is Found
- GIVEN feedback file without any '## Resolution:' line
  - WHEN parse is called
    - [PASS] THEN result is MissingMarker
