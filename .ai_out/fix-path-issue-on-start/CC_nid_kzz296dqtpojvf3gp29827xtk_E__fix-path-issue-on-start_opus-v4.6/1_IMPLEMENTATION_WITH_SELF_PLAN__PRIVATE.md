# Implementation Private Notes

## Status: COMPLETE

## Plan (executed)
1. [x] Create `CallbackScriptsDir` class with `validated`/`forTest` factories
2. [x] Update `ClaudeCodeAdapter` to use `CallbackScriptsDir` instead of `String`
3. [x] Update `ContextInitializerImpl.resolveCallbackScriptsDir()` to return `CallbackScriptsDir`
4. [x] Update all test files to use `CallbackScriptsDir.forTest()` or `CallbackScriptsDir.validated()`
5. [x] Add `CallbackScriptsDirTest` with validation tests
6. [x] Run `./test.sh` - BUILD SUCCESSFUL
7. [x] Commit changes

## Notes
- Fixed detekt MaxLineLength violations in 3 test files caused by FQN references; switched to imports.
- `ServerPortInjectingAdapter` still does string replacement on the command output; it now accepts `CallbackScriptsDir` and uses `.path` for the replacement string.
