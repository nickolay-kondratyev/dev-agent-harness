# READY TO SHIP

## Summary

Introduces `CallbackScriptsDir` -- a validated type wrapping the callback scripts directory path. Construction via `validated()` checks directory existence, presence of `callback_shepherd.signal.sh`, and executable permission. All consuming sites (`ClaudeCodeAdapter`, `ContextInitializerImpl`, test helpers) are updated consistently. Tests pass (sanity check + `./test.sh`). No regressions detected.

The change is well-scoped, follows fail-fast principles, and the validated type prevents the original exit-code-127 issue from silently occurring.

## No CRITICAL Issues

None found.

## IMPORTANT Issues

### 1. `forTest` used in production code path (`ContextInitializerImpl.resolveCallbackScriptsDir`)

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt`, line 244

```kotlin
if (callbackScriptsDirOverride != null) return CallbackScriptsDir.forTest(callbackScriptsDirOverride)
```

The `forTest` factory is documented as "Use only in unit tests where the callback scripts directory does not need to exist on disk." However, it is called in production code (`ContextInitializerImpl`), specifically when `callbackScriptsDirOverride` is non-null. This override is only used from `forIntegTest()` with a sentinel value (`/unused-integ-test-sentinel`), so functionally it is fine. But the naming is misleading -- a production class calling `.forTest()` violates the Principle of Least Surprise.

**Suggestion:** Rename to `CallbackScriptsDir.unvalidated(dirPath)` or `CallbackScriptsDir.withoutValidation(dirPath)` to make the intent clear regardless of caller context. Alternatively, keep `forTest` but add a second factory like `sentinel()` for the production sentinel use case, making intent explicit at the call site.

**Severity:** Low-IMPORTANT. Not blocking -- the behavior is correct and the sentinel path is documented. But the name `forTest` in production code will surprise future readers.

## Suggestions

### 1. Consider `data class` instead of manual `equals`/`hashCode`

`CallbackScriptsDir` manually implements `equals`, `hashCode`, and `toString` for a single `path: String` field. A `data class` with a private constructor would give this for free. The private constructor + companion factory pattern works with data classes:

```kotlin
data class CallbackScriptsDir private constructor(val path: String) {
    companion object {
        fun validated(dirPath: String): CallbackScriptsDir { ... }
        fun forTest(dirPath: String): CallbackScriptsDir = CallbackScriptsDir(dirPath)
    }
}
```

This is minor -- the manual implementation is correct.

### 2. `IntegTestHelpers.resolveCallbackScriptsDir()` -- silently making script executable

The previous version had explicit `check()` calls that would fail loudly if the script was missing or the directory did not exist. The new version pre-emptively sets executable permission before calling `CallbackScriptsDir.validated()`. The validation is still present (delegated to `validated()`), but the `setExecutable(true)` before validation is a minor readability improvement over the previous explicit check-and-set pattern. This is fine.

## Documentation Updates Needed

None required.
