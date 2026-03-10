---
spec: "com.glassthought.chainsaw.core.ticket.TicketParserTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a ticket file missing the id field
  - WHEN parse is called
    - [PASS] THEN throws IllegalArgumentException
- GIVEN a ticket file missing the title field
  - WHEN parse is called
    - [PASS] THEN throws IllegalArgumentException
- GIVEN a ticket file with extra frontmatter fields
  - WHEN parse is called
    - [PASS] THEN additionalFields 'created_iso' value equals the ISO string
    - [PASS] THEN additionalFields 'created_iso' value is a String (not a java.util.Date)
    - [PASS] THEN additionalFields contains 'assignee'
    - [PASS] THEN additionalFields contains 'priority'
    - [PASS] THEN additionalFields contains 'type'
    - [PASS] THEN additionalFields does NOT contain 'id'
    - [PASS] THEN additionalFields does NOT contain 'status'
    - [PASS] THEN additionalFields does NOT contain 'title'
- GIVEN a ticket file with no body after closing ---
  - WHEN parse is called
    - [PASS] THEN description is empty
- GIVEN a valid ticket file
  - WHEN parse is called
    - [PASS] THEN description contains the body text
    - [PASS] THEN description does NOT contain --- delimiters
    - [PASS] THEN id is parsed correctly
    - [PASS] THEN status is parsed correctly
    - [PASS] THEN title is parsed correctly
