---
desc: "Why Gradle `dependsOn` Cannot Self-Heal"
ap: ap.D8WYDcHnzOjzU6v0206H2.E
---

Gradle resolves maven coordinates (e.g. `com.asgard:asgardCore:1.0.0`) at **configuration time**,
before any task executes. A `dependsOn ensureAsgardInMavenLocal` wiring in `compileKotlin` cannot
heal a missing dependency — the build fails at configuration before the healing task can run.
`_prepare_pre_build.sh` solves this by running before Gradle starts.