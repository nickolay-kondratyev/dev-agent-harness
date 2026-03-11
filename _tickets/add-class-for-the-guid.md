---
closed_iso: 2026-03-10T20:38:16Z
id: nid_mkxn4qa9tqlvvm9zxq4es8cg2_E
title: "Add class for the guid"
status: closed
deps: []
links: []
created_iso: 2026-03-10T20:03:24Z
status_updated_iso: 2026-03-10T20:38:16Z
type: task
priority: 3
assignee: nickolaykondratyev
---

## Resolution

Created `HandshakeGuid` value class for strong typing of the GUID parameter.

### Changes Made

1. **New file**: `app/src/main/kotlin/com/glassthought/shepherd/core/wingman/HandshakeGuid.kt`
   - Created `@JvmInline value class HandshakeGuid(val value: String)` with custom `toString()`

2. **Updated**: `Wingman.kt`
   - Changed `resolveSessionId(guid: String)` to `resolveSessionId(guid: HandshakeGuid)`

3. **Updated**: `ClaudeCodeWingman.kt`
   - Updated `GuidScanner.scan(guid: String)` to `GuidScanner.scan(guid: HandshakeGuid)`
   - Updated `FilesystemGuidScanner.scan()` implementation
   - Updated `resolveSessionId()` implementation
   - Updated `pollUntilFound()` private method

4. **Updated**: `ClaudeCodeWingmanTest.kt`
   - All test usages updated to use `HandshakeGuid("...")` instead of plain strings
   - String interpolations updated to use `guid.value`
   - `CountingFakeGuidScanner.scan()` updated to use `HandshakeGuid`

### Commit

`6446e95` - "Add HandshakeGuid value class for strong typing of GUID parameter"
