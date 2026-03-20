# Exploration: Git History for PATH / CallbackScriptsDir Fixes

## Summary

There are **two related tickets** and **one completed fix branch** addressing the PATH issue with `callback_shepherd.signal.sh`:

1. **Ticket `nid_kzz296dqtpojvf3gp29827xtk_E`** ("fix PATH issue on start") -- **CLOSED**. This was the fix branch.
2. **Ticket `nid_gwafum2kyc1kcic7yyla4c4fp_E`** ("PATH issue") -- **IN PROGRESS** (current branch). This is a separate report of the same symptom, created after the fix was already merged.

The fix introduced a `CallbackScriptsDir` validated type that replaces raw `String` parameters, ensuring the callback scripts directory is valid at construction time (fail-fast) rather than failing with a cryptic exit code 127 at agent spawn time.

---

## Git Log (recent 20 commits)

```
29633f8f Mark ticket as in progress: [nid_gwafum2kyc1kcic7yyla4c4fp_E__path-issue]
bd1c9e79 Added file: path-issue.md
1f78ae73 Merge branch 'CC_nid_kzz296dqtpojvf3gp29827xtk_E__fix-path-issue-on-start_opus-v4.6' into main
9604461e Close ticket, add change log for CallbackScriptsDir PATH validation fix
25c2c762 Rename CallbackScriptsDir.forTest() to .unvalidated() for clarity
4e2b10bc Add CallbackScriptsDir validated type for fail-fast PATH validation
ade56e3d Mark ticket as in progress: [nid_kzz296dqtpojvf3gp29827xtk_E__fix-path-issue-on-start]
c79e5698 Added file: fix-path-issue-on-start.md
4ec7a13e Added file: create-hello-world-in-shell-script.md
8b08ce77 Merge branch 'merge-2026-03-20T19-48-17CST'
218b48f1 Merge branch '...' into main
09017c0c Merge branch 'merge-2026-03-20T19-35-50CST'
a1f0349a Merge branch '...' into main
5eac332c [AI] 1st attempt succeeded to fix [./test_post_ticket_consume.sh]
3ab07bb7 Close ticket nid_m2si3js0u72ugb4yfamk0u74y_E + add change log
de23b2f9 Add unit tests: PartExecutorImpl currentIteration starts at 1 for reviewer path
9114101a [AI] 1st attempt succeeded to fix [./test_post_ticket_consume.sh]
b0b73f69 Merge branch 'nid_j1ovm6ohu22o5y8swg2d2hjyz_E__...'
98eb2854 [AI] 1st attempt succeeded to fix [./test_post_ticket_consume.sh]
e14676d7 Merge branch 'merge-2026-03-20T18-56-39CST'
```

---

## Commit: `4e2b10bc` -- Initial Implementation

**Message:** Add CallbackScriptsDir validated type for fail-fast PATH validation

This is the core implementation commit. It introduced:

### New File: `CallbackScriptsDir.kt`
**Path:** `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/CallbackScriptsDir.kt`

```kotlin
class CallbackScriptsDir private constructor(
    val path: String,
) {
    companion object {
        private const val REQUIRED_SCRIPT = "callback_shepherd.signal.sh"

        fun validated(dirPath: String): CallbackScriptsDir {
            val dir = File(dirPath)
            check(dir.isDirectory) {
                "Callback scripts directory does not exist or is not a directory: [$dirPath]"
            }
            val script = File(dir, REQUIRED_SCRIPT)
            check(script.exists()) {
                "$REQUIRED_SCRIPT not found in callback scripts directory [$dirPath]"
            }
            check(script.canExecute()) {
                "$REQUIRED_SCRIPT in [$dirPath] is not executable"
            }
            return CallbackScriptsDir(dirPath)
        }

        fun forTest(dirPath: String): CallbackScriptsDir = CallbackScriptsDir(dirPath)
    }
    // equals, hashCode, toString
}
```

### Modified: `ClaudeCodeAdapter.kt`
Changed `callbackScriptsDir` parameter from `String` to `CallbackScriptsDir`:

```diff
-    private val callbackScriptsDir: String,
+    private val callbackScriptsDir: CallbackScriptsDir,
```

And the PATH export line:

```diff
-            "export PATH=\$PATH:$callbackScriptsDir && " +
+            "export PATH=\$PATH:${callbackScriptsDir.path} && " +
```

### Modified: `ContextInitializerImpl.kt` (`resolveCallbackScriptsDir()`)
Return type changed from `String` to `CallbackScriptsDir`:

```diff
-  private fun resolveCallbackScriptsDir(): String {
-    if (callbackScriptsDirOverride != null) return callbackScriptsDirOverride
+  private fun resolveCallbackScriptsDir(): CallbackScriptsDir {
+    if (callbackScriptsDirOverride != null) return CallbackScriptsDir.forTest(callbackScriptsDirOverride)
     // ... extract script from classpath to temp dir ...
-    return tempDir.toAbsolutePath().toString()
+    val dirPath = tempDir.toAbsolutePath().toString()
+    return CallbackScriptsDir.validated(dirPath)
   }
```

### New Test: `CallbackScriptsDirTest.kt`
**Path:** `app/src/test/kotlin/com/glassthought/shepherd/core/agent/adapter/CallbackScriptsDirTest.kt`

10 test cases covering:
- Valid directory with executable script
- Nonexistent directory
- Missing script in directory
- Non-executable script
- File path instead of directory
- `forTest` factory skipping validation
- Equality and hashCode (same path, different paths)

### Updated Test Files (5 files)
All changed `String` references to `CallbackScriptsDir.forTest(...)`:
- `ClaudeCodeAdapterTest.kt`
- `TicketShepherdCreatorTest.kt` (2 locations)
- `ShepherdInitializerTest.kt`
- `IntegTestHelpers.kt` (switched to `CallbackScriptsDir.validated()`)
- `ServerPortInjectingAdapter.kt`

---

## Commit: `25c2c762` -- Rename forTest() to unvalidated()

**Message:** Rename CallbackScriptsDir.forTest() to .unvalidated() for clarity

This was a follow-up commit that addressed a review finding: the `forTest()` factory was called in production code (`ContextInitializerImpl`) for sentinel/override paths. Naming it `forTest` violated the Principle of Least Surprise when seen in production code.

### Key Rename

```diff
-        fun forTest(dirPath: String): CallbackScriptsDir = CallbackScriptsDir(dirPath)
+        fun unvalidated(dirPath: String): CallbackScriptsDir = CallbackScriptsDir(dirPath)
```

### KDoc Updated

```diff
- * Use [validated] for production wiring. Use [forTest] in unit tests where the directory
- * does not need to exist on disk.
+ * Use [validated] for production wiring. Use [unvalidated] for sentinel values and unit tests
+ * where the directory does not need to exist on disk.
```

### Production Code Fix

```diff
// ContextInitializerImpl.kt
-    if (callbackScriptsDirOverride != null) return CallbackScriptsDir.forTest(callbackScriptsDirOverride)
+    if (callbackScriptsDirOverride != null) return CallbackScriptsDir.unvalidated(callbackScriptsDirOverride)
```

### All Test Call Sites Updated

All `CallbackScriptsDir.forTest(...)` calls renamed to `CallbackScriptsDir.unvalidated(...)` across:
- `CallbackScriptsDirTest.kt`
- `ClaudeCodeAdapterTest.kt`
- `TicketShepherdCreatorTest.kt` (both locations)
- `ShepherdInitializerTest.kt`

This commit also added the review documents (IMPLEMENTATION_REVIEW__PUBLIC.md, IMPLEMENTATION_REVIEW__PRIVATE.md, etc.) under `.ai_out/fix-path-issue-on-start/`.

---

## Commit: `9604461e` -- Close Ticket + Change Log

**Message:** Close ticket, add change log for CallbackScriptsDir PATH validation fix

This commit:
1. Closed ticket `nid_kzz296dqtpojvf3gp29827xtk_E` (status: closed, closed_iso: 2026-03-20T20:18:53Z)
2. Added a Resolution section to the ticket with a summary of changes
3. Created change log entry `_change_log/2026-03-20_20-19-16Z.md` with:
   - Type: `bug_fix`, Impact: `3`
   - Title: "Add CallbackScriptsDir validated type for fail-fast PATH validation"
   - Desc: "Replaces raw String with validated CallbackScriptsDir type. Validates directory exists, script present, and executable at construction time. Prevents exit code 127 when agents try to call callback_shepherd.signal.sh."

---

## Commit: `1f78ae73` -- Merge into main

**Message:** Merge branch 'CC_nid_kzz296dqtpojvf3gp29827xtk_E__fix-path-issue-on-start_opus-v4.6' into main

A merge commit combining the fix branch into main. There was a merge conflict in `ClaudeCodeAdapter.kt` at the `create()` factory method, where parameter names had diverged between branches (`resolutionConfig`/`dispatcherProvider` vs `guidResolutionConfig`). The merge resolution kept the newer `guidResolutionConfig` parameter shape.

```diff
 fun create(
     claudeProjectsDir: Path,
     outFactory: OutFactory,
     serverPort: Int,
-    callbackScriptsDir: String,
+    callbackScriptsDir: CallbackScriptsDir,
     glmConfig: GlmConfig? = null,
-    resolutionConfig: GuidResolutionConfig = GuidResolutionConfig(),
-    dispatcherProvider: DispatcherProvider = DispatcherProvider.standard(),
+    guidResolutionConfig: GuidResolutionConfig = GuidResolutionConfig(),
 ): ClaudeCodeAdapter
```

---

## Tickets Found

### Closed: `nid_kzz296dqtpojvf3gp29827xtk_E` -- "fix PATH issue on start"
- **Status:** closed (2026-03-20T20:18:53Z)
- **Root cause:** Agents spawned in TMUX got exit code 127 when calling `callback_shepherd.signal.sh` because there was no validation that the callback scripts directory existed or was correctly set up.
- **Resolution:** Added `CallbackScriptsDir` validated type with `validated()` and `unvalidated()` factories. Updated `ClaudeCodeAdapter` and `ContextInitializerImpl` to use the new type. Added 10 unit tests.

### In Progress: `nid_gwafum2kyc1kcic7yyla4c4fp_E` -- "PATH issue" (current branch)
- **Status:** in_progress
- **Description:** Reports the same symptom -- agents cannot find `callback_shepherd.signal.sh` on PATH. The agent's PATH dump shows it has no entry for the callback scripts directory.
- **Note:** This ticket was created AFTER the fix was already merged to main. The current branch is based on a commit that includes the merge of the fix. This suggests either the fix did not fully resolve the issue in all scenarios, or this ticket is a duplicate that can be closed.

---

## Analysis: Is the Problem Fully Fixed?

The fix (`CallbackScriptsDir`) addresses **validation** -- it ensures the directory exists, the script is present, and the script is executable at construction time. This is a fail-fast improvement.

However, the current ticket (`nid_gwafum2kyc1kcic7yyla4c4fp_E`) reports that the PATH was **not being set as expected** in the agent's environment. The agent's PATH dump shows no callback scripts directory entry at all. This could indicate:

1. **The agent was not spawned through `ClaudeCodeAdapter`** -- The fix only works when agents go through the normal spawn flow. The closed ticket's agent reply confirmed: "this agent wasn't launched through that flow."
2. **A different code path** where the PATH export is bypassed or lost.
3. **The ticket is stale** -- The symptom was observed before the fix was merged, and the ticket was created after the fix was already in main.

The current ticket suggests writing a focused integration test that verifies the PATH is set correctly within the TMUX session (without starting a full agent). This would be a valuable addition regardless, as it would catch PATH regressions.
