---
id: nid_gaypzj96k523r5rhy2db0lj2n_E
title: "bring asgard test tools and adjust claude md to make sure we use kotest with asgard describe spec"
status: open
deps: []
links: []
created_iso: 2026-03-07T17:42:49Z
status_updated_iso: 2026-03-07T17:42:49Z
type: task
priority: 3
assignee: nickolaykondratyev
---

Bring in asgard test tools dependency to be able to use `asgardTestTools/src/commonMain/kotlin/com/asgard/testTools/describe_spec/AsgardDescribeSpec.kt` as the default describe spec.

With that we should update our instructions to use Kotest with AsgardDescribeSpec. AsgardDescribeSpec already has wiring with outFactory wired in and it detects things like WARNs being logged during testing.