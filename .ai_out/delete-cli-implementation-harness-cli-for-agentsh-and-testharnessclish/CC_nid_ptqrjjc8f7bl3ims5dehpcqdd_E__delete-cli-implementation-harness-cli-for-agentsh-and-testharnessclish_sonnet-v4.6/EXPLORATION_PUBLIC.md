# Exploration Results

## Task
Delete CLI scripts and clean up references per ticket nid_ptqrjjc8f7bl3ims5dehpcqdd_E.

## Files to Delete
1. `scripts/harness-cli-for-agent.sh` — contains anchor `ap.8PB8nMd93D3jipEWhME5n.E`
2. `scripts/test_harness_cli.sh` — test script for the above

## Anchor Point References to Update (ref.ap.8PB8nMd93D3jipEWhME5n.E)

### In ticket scope:
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/AgentRequests.kt` line 10
  - KDoc: `* JSON payloads match the contract defined in harness-cli-for-agent.sh`
  - `* (ref.ap.8PB8nMd93D3jipEWhME5n.E).`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt` line 22
  - KDoc: `* Agents call endpoints via harness-cli-for-agent.sh (ref.ap.8PB8nMd93D3jipEWhME5n.E).`
- `doc/high-level.md` line 97
  - `<!-- ref.ap.8PB8nMd93D3jipEWhME5n.E -- implementation in scripts/harness-cli-for-agent.sh -->`

### Extra (NOT in ticket scope but found):
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/PortFileManager.kt` lines 23, 47
  - Line 23: `* (matches ref.ap.8PB8nMd93D3jipEWhME5n.E harness-cli-for-agent.sh)`
  - Line 47: `// MUST match PORT_FILE in scripts/harness-cli-for-agent.sh (ref.ap.8PB8nMd93D3jipEWhME5n.E)`
  - NOTE: ticket says to search for all refs and update/remove; these should be cleaned up too

## Build Verification
- `./gradlew :app:compileKotlin :app:compileTestKotlin` must pass
- `./gradlew :app:test` must pass
- Use `bash _prepare_pre_build.sh && ./gradlew :app:compileKotlin :app:compileTestKotlin` to ensure asgard libs are published first
