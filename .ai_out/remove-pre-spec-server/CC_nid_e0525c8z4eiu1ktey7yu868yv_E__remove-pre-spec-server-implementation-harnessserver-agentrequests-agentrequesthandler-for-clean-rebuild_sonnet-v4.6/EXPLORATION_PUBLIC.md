# Exploration: Remove Pre-Spec Server Implementation

## Files to Delete

### Main Source Files (4)
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/server/HarnessServer.kt` — `HarnessServer` interface + `KtorHarnessServer` impl. Exposes `/agent/done`, `/agent/question`, `/agent/failed`, `/agent/status` (wrong paths vs spec).
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/server/AgentRequests.kt` — Request data classes using `branch` field (wrong identity model vs spec `handshakeGuid`).
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/server/AgentRequestHandler.kt` — Handler interface + `NoOpAgentRequestHandler`. `onQuestion()` returns String (blocking — wrong per spec).
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/server/PortFileManager.kt` — Implements `PortPublisher`. Loosely coupled. Spec-compliant concept but tightly coupled to the removed server.

### Test Files (2)
- `app/src/test/kotlin/com/glassthought/ticketShepherd/core/server/KtorHarnessServerTest.kt`
- `app/src/test/kotlin/com/glassthought/ticketShepherd/core/server/PortFileManagerTest.kt`

## External References

**NONE.** The server code is completely isolated:
- `Initializer.kt` — no server references
- `AppMain.kt` — no server references
- No production code outside `server/` imports these classes

## Safety Assessment

| File | Safe to Delete | Reason |
|------|----------------|--------|
| HarnessServer.kt | ✓ | Zero external references |
| AgentRequests.kt | ✓ | Only used within server package |
| AgentRequestHandler.kt | ✓ | Only used within server package |
| PortFileManager.kt | ✓ | Loosely coupled, no external usage |
| KtorHarnessServerTest.kt | ✓ | Tests for deleted code |
| PortFileManagerTest.kt | ✓ | Tests for deleted code |

## Post-Deletion Impact
- Zero changes needed to Initializer.kt, AppMain.kt, or any other file
- Build will succeed after deletion
- All remaining tests will continue to pass
