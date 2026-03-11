---
id: nid_ptqrjjc8f7bl3ims5dehpcqdd_E
title: "Delete CLI implementation: harness-cli-for-agent.sh and test_harness_cli.sh"
status: in_progress
deps: []
links: []
created_iso: 2026-03-11T16:05:30Z
status_updated_iso: 2026-03-11T16:07:35Z
type: chore
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [cleanup, cli]
---

## Context

The CLI script implementation jumped ahead of the design. We are iterating on the spec
(HandshakeGuid in all callbacks, dropping branch from requests, CHAINSAW_HANDSHAKE_GUID env var).
The existing implementation does not include these design changes and should be deleted
for a clean break. A fresh implementation will follow once the spec is finalized.

## Requirements

### R1 — Delete CLI script
Delete `scripts/harness-cli-for-agent.sh`

### R2 — Delete CLI tests
Delete `scripts/test_harness_cli.sh`

### R3 — Remove anchor point references to deleted script
Search for `ref.ap.8PB8nMd93D3jipEWhME5n.E` and update/remove references:
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/AgentRequests.kt` (KDoc reference)
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt` (KDoc reference)
- `doc/high-level.md` (section references)

The anchor point itself (`ap.8PB8nMd93D3jipEWhME5n.E`) is deleted with the file.
References should note the script was removed and will be rebuilt per updated spec.

### R4 — Verify build
`./gradlew :app:compileKotlin :app:compileTestKotlin` compiles clean.
`./gradlew :app:test` passes.

## Out of Scope
- Reimplementing the CLI (separate ticket after spec is finalized)
- Updating AgentRequests.kt to use handshakeGuid (design iteration still in progress)
- Updating doc/high-level.md CLI spec section (being rewritten as part of design iteration)

