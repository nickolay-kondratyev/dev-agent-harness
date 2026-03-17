---
closed_iso: 2026-03-17T22:02:11Z
id: nid_xqmr7spyvat0g24ax7o22lepm_E
title: "SIMPLIFY_CANDIDATE: Replace DirectQuickCheapLLM branch-slug compression with deterministic truncation"
status: closed
deps: []
links: []
created_iso: 2026-03-17T21:32:21Z
status_updated_iso: 2026-03-17T22:02:11Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, branch-naming, robustness, startup]
---

Current design: Branch name is "{TICKET_ID}__{slugified_title}__try-{N}". When the slugified title is too long, TicketShepherdCreator calls DirectQuickCheapLLM to compress it. See doc/core/git.md (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E) section "Branch Naming".

Problem: Calling an LLM at startup just to shorten a branch name:
1. Creates a startup failure mode (LLM API unavailable = harness cannot start)
2. Makes branch names non-deterministic (two runs on the same ticket may produce different slugs)
3. Adds latency to harness startup
4. Introduces a dependency on DirectQuickCheapLLM in TicketShepherdCreator that is disproportionate to the value (branch name readability)

Spec reference: doc/core/git.md "Branch Naming" section, doc/core/TicketShepherdCreator.md (ref.ap.cJbeC4udcM3J8UFoWXfGh.E)

Simpler approach: Deterministic truncation algorithm:
1. Slugify the title (lowercase, hyphens)
2. If length <= MAX_SLUG_LENGTH (e.g., 50 chars) → use as-is
3. If longer → take the first K words that fit within the limit, append a short stable hash (first 6 chars of SHA1 of the full slug) for uniqueness
Example: "implement-user-authentication-flow-with-oauth-and-session" → "implement-user-authentication-flow-with-a1b2c3"

Benefits:
- No LLM call at startup → no LLM-related startup failure mode
- Deterministic: same ticket always produces the same branch name
- Zero latency overhead
- Readable enough for human debugging
- Removes DirectQuickCheapLLM dependency from startup critical path

Note: This does not affect branch name uniqueness — try-N collision avoidance is already handled by the .ai_out/ directory scan.


## Notes

**2026-03-17T22:02:07Z**

Resolved by updating doc/core/git.md Branch Naming section. Replaced the DirectQuickCheapLLM compression approach with a deterministic truncation algorithm: first K whole hyphen-delimited words that fit within MAX_SLUG_LENGTH-7 (50-7=43 chars), then append a hyphen and 6-char SHA-1 hash of the full slug. This removes the LLM call from startup, makes branch names deterministic, and eliminates the startup failure mode. Committed as 0def8b6.
