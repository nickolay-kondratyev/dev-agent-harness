---
id: nid_u07t1r4y0ymqgdxk47rdggjj3_E
title: "SIMPLIFY_CANDIDATE: Extract shared YAML frontmatter field extractor to eliminate duplication between TicketParser and RoleCatalogLoader"
status: open
deps: []
links: []
created_iso: 2026-03-17T21:39:16Z
status_updated_iso: 2026-03-17T21:39:16Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, dry, yaml, parsing]
---

TicketParser (app/src/main/kotlin/com/glassthought/shepherd/core/supporting/ticket/TicketParser.kt) and RoleCatalogLoader (app/src/main/kotlin/com/glassthought/shepherd/core/agent/rolecatalog/RoleCatalogLoader.kt) both follow the identical pattern:

1. Read file → parse frontmatter via YamlFrontmatterParser
2. Extract required fields: `result.yamlFields["key"] ?: throw IllegalArgumentException("Missing field: key")`
3. Extract optional fields: `result.yamlFields["key"]`
4. Construct domain object

The field extraction and validation boilerplate is duplicated in both files (~20+ lines each).

**Simplification:** Extract a `FrontmatterFields` value class or helper companion that provides:

```kotlin
class FrontmatterFields(private val fields: Map<String, String>) {
    fun require(key: String): String = fields[key] ?: throw IllegalArgumentException("Missing required field: $key")
    fun optional(key: String): String? = fields[key]
}
```

This reduces each parser to its essential logic (field names + domain object construction) and eliminates ~20 lines of near-identical boilerplate from two locations.

**Robustness improvement:** A future third frontmatter-based parser (e.g., for plan.json YAML headers) would automatically use consistent error messages and validation behavior instead of triplicating the pattern again.

