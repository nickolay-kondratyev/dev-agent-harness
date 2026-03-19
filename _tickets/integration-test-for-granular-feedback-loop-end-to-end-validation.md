---
id: nid_jgp6ocwb2csmxvgntb8f8su9c_E
title: "Integration test for granular feedback loop — end-to-end validation"
status: in_progress
deps: [nid_fq8wn0eb9yrvzcpzdurlmsg7i_E, nid_yzmwosyazxksnr1hafmw87x1m_E, nid_gp9rduvxoqf14m95z9bttnaxq_E]
links: []
created_iso: 2026-03-18T22:33:56Z
status_updated_iso: 2026-03-19T20:26:23Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [granular-feedback-loop, integration-test]
---

Gate 6 from granular feedback loop spec (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).

End-to-end integration test with real agent (or GLM fake) confirming the full feedback loop.

## Scope
Integration test covering:
- Reviewer writes individual feedback files with severity prefixes
- Doer receives one item at a time, addresses/rejects each with resolution marker
- Rejection negotiation works between real agents
- Harness moves files correctly after each item
- Self-compaction triggers between items when context is low
- Iteration counter behaves correctly
- Part completion guard works

## Notes
- Use GLM (Z.AI) for agent spawning per deep memory `integ_tests__use_glm_for_agent_spawning.md`
- Gate entire describe block with `.config(isIntegTestEnabled())`
- This is a SANITY CHECK — unit tests in the dependency tickets provide primary coverage

## Spec Reference
`doc/plan/granular-feedback-loop.md` Gate 6

