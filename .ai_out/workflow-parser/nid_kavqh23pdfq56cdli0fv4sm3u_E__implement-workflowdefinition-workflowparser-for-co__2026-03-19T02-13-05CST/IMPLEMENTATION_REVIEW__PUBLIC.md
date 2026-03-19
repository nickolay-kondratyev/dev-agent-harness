# Implementation Review: WorkflowDefinition + WorkflowParser

## Summary

This change introduces `WorkflowDefinition` (data class with init-block validation) and `WorkflowParser` (interface + `WorkflowParserImpl`) for loading workflow JSON files from `config/workflows/`. The implementation correctly models the two mutually exclusive workflow modes (straightforward vs. with-planning), follows existing codebase patterns (TicketParser, ShepherdObjectMapper, constructor injection), and includes comprehensive BDD tests.

**Overall assessment: APPROVE with one IMPORTANT issue.**

All tests pass. Sanity check passes. No existing functionality was removed or modified -- this is purely additive (4 new files).

## No CRITICAL Issues

No security, correctness, or data-loss issues found.

## IMPORTANT Issues

### 1. Missing validation: `executionPhasesFrom` should be rejected for straightforward workflows

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/workflow/WorkflowDefinition.kt` (lines 31-43)

Currently, a straightforward workflow can silently accept `executionPhasesFrom`:

```kotlin
WorkflowDefinition(
    name = "weird",
    parts = listOf(executionPart()),
    executionPhasesFrom = "plan_flow.json",  // silently accepted, never used
)
```

This violates the **Principle of Least Surprise**. If someone specifies `executionPhasesFrom` on a straightforward workflow, it is almost certainly a mistake. The init block should reject this:

```kotlin
if (parts != null) {
    require(executionPhasesFrom == null) {
        "Straightforward workflow must not specify 'executionPhasesFrom' — " +
            "this field is only valid for with-planning workflows."
    }
}
```

A corresponding test case should be added to `WorkflowDefinitionTest`.

## Suggestions

### 1. Consider `suspend` call placement in `WorkflowParserTest` describe blocks

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/workflow/WorkflowParserTest.kt` (lines 35-36)

The `parser.parse(...)` call is made directly inside a `describe` block body (line 36), which per CLAUDE.md standards is **not a suspend context**. It works because Kotest `DescribeSpec` describe blocks do run as coroutines, but the project standard calls out:

> `describe` block bodies are NOT suspend contexts. Suspend calls must go inside `it` or `afterEach` blocks.

This applies to lines 35, 81, and other `val definition = parser.parse(...)` calls inside `describe` blocks. The pattern works but contradicts the stated convention. If the convention is correct, these should use a `lateinit` or lazy pattern inside `it` blocks. If the convention is overly strict, consider updating the documentation.

### 2. Temp directory cleanup could use `@TempDir` or test lifecycle

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/workflow/WorkflowParserTest.kt` (lines 143-160, 164-198, 202-237)

The malformed JSON and phase validation tests create temp directories with `Files.createTempDirectory()` and clean up with `afterSpec`. This works, but multiple `describe` blocks repeat the same temp-dir setup pattern (create temp dir, create `config/workflows/`, write file, cleanup). Consider extracting a helper:

```kotlin
private fun withWorkflowFile(fileName: String, content: String, block: (Path) -> Unit) {
    val tempDir = Files.createTempDirectory("workflow-parser-test")
    try {
        val workflowDir = tempDir.resolve("config/workflows")
        Files.createDirectories(workflowDir)
        Files.writeString(workflowDir.resolve(fileName), content)
        block(tempDir)
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}
```

This would DRY up 3 test blocks that share the same boilerplate.

### 3. Log message semantics could be more specific

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/workflow/WorkflowParser.kt` (lines 78-85)

The info log emits `Val` with `ValType.STRING_USER_AGNOSTIC` for both the workflow name and its type. Consider using a more semantically specific `ValType` if one exists or creating one (e.g., `ValType.WORKFLOW_NAME`, `ValType.WORKFLOW_TYPE`). This is a minor point -- the current approach is consistent with how `TicketParser` logs.

## Documentation Updates Needed

None required. The implementation is self-contained and the spec in `doc/schema/plan-and-current-state.md` already documents the schema.
