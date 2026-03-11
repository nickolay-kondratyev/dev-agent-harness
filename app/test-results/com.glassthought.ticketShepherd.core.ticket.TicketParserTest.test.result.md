---
spec: "com.glassthought.ticketShepherd.core.ticket.TicketParserTest"
status: FAILED
failed: 17
skipped: 0
---

## Failed Tests

- GIVEN a valid ticket file > WHEN parse is called > THEN id is parsed correctly
- GIVEN a valid ticket file > WHEN parse is called > THEN title is parsed correctly
- GIVEN a valid ticket file > WHEN parse is called > THEN status is parsed correctly
- GIVEN a valid ticket file > WHEN parse is called > THEN description contains the body text
- GIVEN a valid ticket file > WHEN parse is called > THEN description does NOT contain --- delimiters
- GIVEN a ticket file missing the id field > WHEN parse is called > THEN throws IllegalArgumentException
  - Error: Expected exception java.lang.IllegalArgumentException but a NullPointerException was thrown instead.
- GIVEN a ticket file missing the title field > WHEN parse is called > THEN throws IllegalArgumentException
  - Error: Expected exception java.lang.IllegalArgumentException but a NullPointerException was thrown instead.
- GIVEN a ticket file with extra frontmatter fields > WHEN parse is called > THEN additionalFields contains 'type'
- GIVEN a ticket file with extra frontmatter fields > WHEN parse is called > THEN additionalFields contains 'priority'
- GIVEN a ticket file with extra frontmatter fields > WHEN parse is called > THEN additionalFields contains 'assignee'
- GIVEN a ticket file with extra frontmatter fields > WHEN parse is called > THEN additionalFields does NOT contain 'id'
- GIVEN a ticket file with extra frontmatter fields > WHEN parse is called > THEN additionalFields does NOT contain 'title'
- GIVEN a ticket file with extra frontmatter fields > WHEN parse is called > THEN additionalFields does NOT contain 'status'
- GIVEN a ticket file with extra frontmatter fields > WHEN parse is called > THEN additionalFields 'created_iso' value is a String (not a java.util.Date)
- GIVEN a ticket file with extra frontmatter fields > WHEN parse is called > THEN additionalFields 'created_iso' value equals the ISO string
- GIVEN a ticket file where id is an unquoted ISO date > WHEN parse is called > THEN id is preserved as the ISO string (not corrupted by snakeyaml Date resolution)
- GIVEN a ticket file with no body after closing --- > WHEN parse is called > THEN description is empty

- GIVEN a ticket file missing the id field
  - WHEN parse is called
    - [FAIL] THEN throws IllegalArgumentException
- GIVEN a ticket file missing the title field
  - WHEN parse is called
    - [FAIL] THEN throws IllegalArgumentException
- GIVEN a ticket file where id is an unquoted ISO date
  - WHEN parse is called
    - [FAIL] THEN id is preserved as the ISO string (not corrupted by snakeyaml Date resolution)
- GIVEN a ticket file with extra frontmatter fields
  - WHEN parse is called
    - [FAIL] THEN additionalFields 'created_iso' value equals the ISO string
    - [FAIL] THEN additionalFields 'created_iso' value is a String (not a java.util.Date)
    - [FAIL] THEN additionalFields contains 'assignee'
    - [FAIL] THEN additionalFields contains 'priority'
    - [FAIL] THEN additionalFields contains 'type'
    - [FAIL] THEN additionalFields does NOT contain 'id'
    - [FAIL] THEN additionalFields does NOT contain 'status'
    - [FAIL] THEN additionalFields does NOT contain 'title'
- GIVEN a ticket file with no body after closing ---
  - WHEN parse is called
    - [FAIL] THEN description is empty
- GIVEN a valid ticket file
  - WHEN parse is called
    - [FAIL] THEN description contains the body text
    - [FAIL] THEN description does NOT contain --- delimiters
    - [FAIL] THEN id is parsed correctly
    - [FAIL] THEN status is parsed correctly
    - [FAIL] THEN title is parsed correctly
