---
id: nid_kwebixws6qqn808x4904f2gtr_E
title: "Implement SelfCompactionInstructionBuilder — compaction instruction message template"
status: in_progress
deps: []
links: []
created_iso: 2026-03-19T00:41:58Z
status_updated_iso: 2026-03-19T17:39:37Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, compaction]
---

## Context

Spec: `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E), R8.
Instruction template: ap.kY4yu9B3HGvN66RoDi0Fb.E

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/compaction/SelfCompactionInstructionBuilder.kt`

A builder that produces the compaction instruction message sent to the agent.

### Template

```markdown
Your context window is running low. Summarize this chat into
`<absolute_path_to_private_md>` so work can continue in a new chat.

Preserve all context needed for a new chat to understand:
- What we're doing and why\n- At which point we are in the work\n- All challenges we've had and how we've solved them\n- Key decisions made and why\n- Any patterns or discoveries about the codebase\n\nMake the summary as **concise** as possible but context rich.\n\nAfter writing the file, signal completion:\n`callback_shepherd.signal.sh self-compacted`\n```\n\n### Interface\n\n```kotlin\nclass SelfCompactionInstructionBuilder {\n    fun build(privateMdAbsolutePath: Path): String\n}\n```\n\n### Key points\n- `<absolute_path_to_private_md>` is replaced with the actual absolute path from AiOutputStructure\n- Uses `ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT` and `ProtocolVocabulary.Signal.SELF_COMPACTED`\n- The instruction is NOT in the standard callback help block (agents don't call it spontaneously)\n\n## Tests\n\n1. Unit test: template renders correct PRIVATE.md path\n2. Unit test: template contains `callback_shepherd.signal.sh self-compacted`\n3. Unit test: template contains summarization guidelines\n\n## Acceptance Criteria\n\n- Builder compiles and produces correct template\n- All unit tests pass\n- `./test.sh` passes

