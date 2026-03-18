---
closed_iso: 2026-03-18T16:22:34Z
id: nid_xme0h76xv7446kvdxudw7yff3_E
title: "add detekt"
status: closed
deps: []
links: []
created_iso: 2026-03-18T16:12:42Z
status_updated_iso: 2026-03-18T16:22:34Z
type: task
priority: 3
assignee: nickolaykondratyev
---

**Add `detekt`**

1) Add detekt
2) Establish a baseline based on the current code, so the build passes.
3) Add detekt to run as part of ./test.sh (make detekt a part of test dependency in gradle)
4) update the ai_input to add a new SUCCINCT entry that we should aim to reduce any baseline exceptions rather than add any new ones for any code changes.  