#need-tickets

Be smart on whether to continue the same conversation or restart a new one as we are looping and addressing feedback.

IN v1 we can always restart. To avoid COMPACTION.

IN v2 we can be smarter when it comes to COMPACTION or not.

---

**UPDATE (2026-03-14):** This is now addressed by the Context Window Self-Compaction spec
(ref.ap.8nwz2AHf503xwq8fKuLcl.E). The harness monitors `context_window_slim.json` and
performs controlled self-compaction with PRIVATE.md summarization + session rotation at
done boundaries (35% remaining / 65% used). Claude Code's native auto-compaction remains
**enabled** as an emergency fallback for mid-task context exhaustion. V2 adds a hard
threshold (20% remaining) with emergency interrupt — see
[`our-own-emergency-compression.md`](our-own-emergency-compression.md).