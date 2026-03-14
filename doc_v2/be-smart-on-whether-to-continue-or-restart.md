Be smart on whether to continue the same conversation or restart a new one as we are looping and addressing feedback.

IN v1 we can always restart. To avoid COMPACTION.

IN v2 we can be smarter when it comes to COMPACTION or not.

---

**UPDATE (2026-03-14):** This is now addressed by the Context Window Self-Compaction spec
(ref.ap.8nwz2AHf503xwq8fKuLcl.E). The harness monitors `context_window_slim.json`,
disables Claude Code's auto-compaction, and performs controlled self-compaction with
PRIVATE.md summarization + session rotation at two thresholds (65% remaining at done
boundaries, 20% remaining emergency interrupt).