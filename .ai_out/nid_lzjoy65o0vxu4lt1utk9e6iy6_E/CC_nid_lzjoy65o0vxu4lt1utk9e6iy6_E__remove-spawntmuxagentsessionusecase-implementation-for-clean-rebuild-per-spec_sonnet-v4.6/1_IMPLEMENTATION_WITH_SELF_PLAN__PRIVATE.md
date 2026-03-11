# Implementation Private Notes

## Plan

**Goal**: Remove SpawnTmuxAgentSessionUseCase and all orphaned dependencies for a clean build.

**Steps**:
1. [x] Read all affected files — DONE
2. [x] Delete files to remove (main + test) — DONE
3. [x] Update Initializer.kt: remove imports, UseCases data class, wiring code, update ShepherdContext call — DONE
4. [x] Update ShepherdContext (inside Initializer.kt): remove `useCases: UseCases` param — DONE
5. [x] Remove now-unused `environment`, `systemPromptFilePath`, `claudeProjectsDir` params from Initializer interface and impl — DONE
6. [x] Update SharedContextIntegFactory and AppDependenciesCloseTest to match new signature — DONE
7. [x] Update KDoc in TmuxAgentSession.kt, ClaudeCodeAgentStarter.kt (cleanup dangling refs) — DONE
8. [x] Clean up dangling comment in HandshakeGuid.kt — DONE
9. [x] Verify build passes — DONE (BUILD SUCCESSFUL)
10. [x] Run tests — DONE (115 tests, 0 failures, 0 errors)
11. [x] Write public output — DONE

## Status: COMPLETE
