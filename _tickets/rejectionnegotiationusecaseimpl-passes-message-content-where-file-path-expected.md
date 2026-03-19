---
id: nid_srtovyxkmpyp3xupve7x1akiy_E
title: "RejectionNegotiationUseCaseImpl passes message content where file path expected"
status: in_progress
deps: []
links: []
created_iso: 2026-03-19T19:29:52Z
status_updated_iso: 2026-03-19T19:40:32Z
type: bug
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [integration, path-mismatch]
---

RejectionNegotiationUseCaseImpl.buildReviewerJudgmentMessage() and buildDoerComplianceMessage() produce multi-line message content strings.
These are passed to reInstructAndAwait.execute(handle, message) which internally calls Path.of(message) in ReInstructAndAwaitImpl.
This causes InvalidPathException on systems where native encoding is ASCII (em-dash character fails).

The ReInstructAndAwait interface documents message as "Absolute path to the instruction file" but RejectionNegotiationUseCaseImpl passes content, not a path.

Fix options:
1. Add InstructionFileWriter dependency to RejectionNegotiationUseCaseImpl that writes content to a temp file and returns the path.
2. Change ReInstructAndAwait to accept message content rather than file paths (would require broader changes).

Files:
- app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt (lines 100-101, 146-147)
- app/src/main/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwait.kt (line 89)

Discovered by wired integration test: RejectionNegotiationWithFakeAgentFacadeTest

