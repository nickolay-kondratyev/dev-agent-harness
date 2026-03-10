---
id: nid_0h5gb1m47hyo0ljxb7v432q2k_E
title: "Remove thorg-submodule"
status: open
deps: []
links: []
created_iso: 2026-03-10T01:07:52Z
status_updated_iso: 2026-03-10T01:07:52Z
type: task
priority: 3
assignee: nickolaykondratyev
---

1) Have Asgard core and other Asgard dependencies build to m2 local.
2) Adjust docker to share m2 directory between docker instances.
3) Switch this repo to pull asgard core from maven including local and clean up any references to THORG_ROOT in doc. Adjust Out usage to refer to ValTypeV2