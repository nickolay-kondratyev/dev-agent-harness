# Implementation Iteration: Review Feedback for WorkflowDefinition + WorkflowParser

## Changes Made

### 1. IMPORTANT: Added validation rejecting `executionPhasesFrom` for straightforward workflows

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/workflow/WorkflowDefinition.kt`

Added `require(executionPhasesFrom == null)` check in the init block when `parts != null` (straightforward workflow). This prevents a POLS violation where `executionPhasesFrom` could be silently accepted on a straightforward workflow without ever being used.

**Test added** in `WorkflowDefinitionTest.kt`: "GIVEN a straightforward workflow with executionPhasesFrom specified" verifies the `IllegalArgumentException` is thrown with the expected message.

### 2. Moved `parser.parse()` calls from `describe` blocks into `it` blocks

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/workflow/WorkflowParserTest.kt`

Per project standard ("describe block bodies are NOT suspend contexts"), moved all `parser.parse()` calls from `describe` block bodies into `it` blocks. Each `it` block now calls `parser.parse()` independently. This is slightly more verbose but correctly respects the suspend context convention.

### 3. DRYed up temp-dir boilerplate with `createTempWorkflowDir` helper

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/workflow/WorkflowParserTest.kt`

Extracted a `createTempWorkflowDir(fileName, content): Path` helper that creates the temp directory structure. Each `it` block uses `try/finally` for cleanup. This eliminates the repeated `Files.createTempDirectory` / `Files.createDirectories` / `Files.writeString` boilerplate and the `afterSpec` cleanup pattern from 3 test blocks.

### 4. Reviewer suggestion #3 (log ValType specificity) -- Not addressed

The review noted this was minor and consistent with existing `TicketParser` logging. Per 80/20, no change needed.

## All Tests Pass

`./gradlew :app:test` -- all green.
