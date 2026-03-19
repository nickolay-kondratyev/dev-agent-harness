---
id: nid_92vpmdxcn3j8f98gzgu9eln43_E
title: "Implement FeedbackResolutionParser — parse Resolution markers from feedback files"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T22:29:51Z
status_updated_iso: 2026-03-19T17:00:30Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [granular-feedback-loop, parsing]
---

Implement a utility class `FeedbackResolutionParser` that reads `## Resolution:` markers from feedback files.

## Context
Part of the Granular Feedback Loop spec (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) at `doc/plan/granular-feedback-loop.md`.

After a doer processes a feedback item and signals `done`, the harness reads the `## Resolution:` marker from the feedback file to determine disposition.

## Requirements (from spec R6)
- Parse `## Resolution: ADDRESSED`, `## Resolution: REJECTED`, `## Resolution: SKIPPED` from feedback file content
- Case-insensitive keyword match after `## Resolution:`
- `SKIPPED` is valid only for `optional__` prefixed files
- Missing marker → return a distinct "missing" result (caller decides crash behavior)
- Any unrecognized keyword → return a distinct "invalid" result
- Does NOT validate the reasoning content (KISS — same principle as PUBLIC.md format)

## Interface Shape
```kotlin
enum class FeedbackResolution { ADDRESSED, REJECTED, SKIPPED }

sealed class ParseResult {
    data class Found(val resolution: FeedbackResolution) : ParseResult()
    object MissingMarker : ParseResult()
    data class InvalidMarker(val rawValue: String) : ParseResult()
}

class FeedbackResolutionParser {
    fun parse(fileContent: String): ParseResult
}
```

## Testing
- Unit test: file with `## Resolution: ADDRESSED` → `Found(ADDRESSED)`
- Unit test: file with `## Resolution: REJECTED` → `Found(REJECTED)`
- Unit test: file with `## Resolution: SKIPPED` → `Found(SKIPPED)`
- Unit test: file without any `## Resolution:` line → `MissingMarker`
- Unit test: file with `## Resolution: INVALID_VALUE` → `InvalidMarker("INVALID_VALUE")`
- Unit test: case insensitivity — `## Resolution: addressed` → `Found(ADDRESSED)`
- Unit test: marker embedded in longer file (content before and after)

## Package
`com.glassthought.shepherd.feedback`

## Acceptance Criteria
- All unit tests pass
- Parser handles all resolution marker variants correctly
- No external dependencies beyond standard Kotlin

