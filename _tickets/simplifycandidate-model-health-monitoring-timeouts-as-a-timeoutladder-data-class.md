---
closed_iso: 2026-03-17T22:09:36Z
id: nid_7wb6rh7rsz2el1n72x3un3fhg_E
title: "SIMPLIFY_CANDIDATE: Model health monitoring timeouts as a TimeoutLadder data class"
status: closed
deps: []
links: []
created_iso: 2026-03-17T21:32:39Z
status_updated_iso: 2026-03-17T22:09:36Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, health-monitoring, robustness, config]
---

Current design: Three health monitoring timeout constants (noStartupAckTimeout = 3 min, noActivityTimeout = 30 min, pingTimeout = 3 min) are defined independently. There is also a ticket to centralize ALL timeout constants into a HarnessTimeoutConfig data class.

Related existing ticket: "SIMPLIFY_CANDIDATE: Centralize all timeout/threshold constants into HarnessTimeoutConfig data class" — which addresses centralization. This ticket is about the additional value of making the CONCEPTUAL RELATIONSHIP between these three timeouts explicit.

Problem: The three health monitoring timeouts form a conceptual ladder — startup → steady-state → ping-response. They are applied sequentially in the health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E in doc/core/PartExecutor.md). Storing them as 3 independent constants in a flat config class hides this relationship.

Spec reference: doc/core/PartExecutor.md Health-Aware Await Loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E), doc/use-case/HealthMonitoring.md (ref.ap.RJWVLgUGjO5zAwupNLhA0.E)

Simpler/clearer approach:

data class HealthTimeoutLadder(
    val startup: Duration = 3.minutes,          // catch spawn failures before first callback
    val normalActivity: Duration = 30.minutes,  // steady-state liveness window
    val pingResponse: Duration = 3.minutes      // proof-of-life window after ping
)

The health-aware loop receives a single HealthTimeoutLadder instead of 3 separate Duration params. Tuning timeouts for a deployment means changing one cohesive object.

Benefits:
- Makes the startup→activity→ping progression self-documenting
- Operators tuning timeouts see the full sequence in one place (e.g., "startup should be < normalActivity" is obvious)
- The health-aware await loop signature becomes cleaner
- Easy to create test configurations (HealthTimeoutLadder(startup=1.second, normalActivity=5.seconds, pingResponse=1.second))

This is distinct from (and complementary to) the broader HarnessTimeoutConfig centralization ticket, which handles context window thresholds and other constants.


## Notes

**2026-03-17T22:09:32Z**

Resolution: Updated spec (doc/ tree only, no code changes) to introduce HealthTimeoutLadder data class grouping the three health monitoring timeouts. Changed 6 files:
- doc/use-case/HealthMonitoring.md: Added HealthTimeoutLadder section with Kotlin data class definition (startup=3min, normalActivity=30min, pingResponse=3min), updated Configuration table, DetectionContext table, What Runs Where table, Flow section, and edge case section to use healthTimeouts.* fields.
- doc/core/PartExecutor.md: Updated pseudocode to use healthTimeouts.normalActivity and healthTimeouts.pingResponse; Dependencies section now describes HealthTimeoutLadder nesting within HarnessTimeoutConfig.
- doc/core/agent-to-server-communication-protocol.md: Updated Startup Timeout section and inline references.
- doc/high-level.md: Updated summary descriptions.
- doc/use-case/SpawnTmuxAgentSessionUseCase.md: Updated startup handshake description.
- doc/core/AgentInteraction.md: Updated test pseudocode comment.
No old field names (noStartupAckTimeout, noActivityTimeout, pingTimeout, startupAckTimeout) remain in doc/.
