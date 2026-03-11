---
id: nid_zt03ntko0lyz93yhhtob9sa0p_E
title: "Refactor ClaudeCodeWingman to use val guidScanner"
status: open
deps: []
links: []
created_iso: 2026-03-10T23:30:12Z
status_updated_iso: 2026-03-10T23:30:12Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

Pre-existing immutability violation in ClaudeCodeWingman.

The secondary (test-injection) constructor delegates to the primary with PLACEHOLDER_PATH, which triggers FilesystemGuidScanner(PLACEHOLDER_PATH) in the property initializer — and immediately after, the secondary constructor body overwrites it via `this.guidScanner = guidScanner`. This requires `var` where `val` is semantically correct, and it allocates a FilesystemGuidScanner instance only to discard it.

Fix: Refactor ClaudeCodeWingman to accept GuidScanner as the primary constructor parameter (with a convenience secondary constructor wrapping FilesystemGuidScanner(claudeProjectsDir)), allowing the field to be `val`.

File: app/src/main/kotlin/com/glassthought/shepherd/core/wingman/ClaudeCodeWingman.kt lines ~87-93

Identified during review of ticket nid_47z6remhu28wwoi4clb1b0pb9_E.

