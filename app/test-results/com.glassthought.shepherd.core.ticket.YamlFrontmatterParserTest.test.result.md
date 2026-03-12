---
spec: "com.glassthought.shepherd.core.ticket.YamlFrontmatterParserTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN frontmatter with an unquoted ISO datetime value
  - WHEN parse is called
    - [PASS] THEN created_iso value equals the ISO string
    - [PASS] THEN created_iso value is a String (not a java.util.Date)
- GIVEN markdown content where body has multiple paragraphs
  - WHEN parse is called
    - [PASS] THEN body contains first paragraph
    - [PASS] THEN body contains second paragraph
    - [PASS] THEN body contains third paragraph
- GIVEN markdown content where body itself contains --- lines
  - WHEN parse is called
    - [PASS] THEN body contains text after the inner ---
    - [PASS] THEN body contains text before the inner ---
    - [PASS] THEN yamlFields contains id from frontmatter only
- GIVEN markdown content with extra frontmatter fields
  - WHEN parse is called
    - [PASS] THEN yamlFields contains 'assignee' field
    - [PASS] THEN yamlFields contains 'priority' field
- GIVEN markdown content with opening --- but no closing ---
  - WHEN parse is called
    - [PASS] THEN throws IllegalArgumentException
- GIVEN markdown content with valid YAML frontmatter
  - WHEN parse is called
    - [PASS] THEN body contains the expected text
    - [PASS] THEN body does NOT contain the frontmatter delimiters
    - [PASS] THEN yamlFields contains the id
    - [PASS] THEN yamlFields contains the title
- GIVEN markdown content without leading --- delimiter
  - WHEN parse is called
    - [PASS] THEN throws IllegalArgumentException
