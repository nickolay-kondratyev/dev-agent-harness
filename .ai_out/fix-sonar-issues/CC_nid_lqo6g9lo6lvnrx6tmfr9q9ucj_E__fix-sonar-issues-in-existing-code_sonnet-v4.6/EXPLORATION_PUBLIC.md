# Sonar Issues Exploration Report

## Summary

This document provides a detailed analysis of all 21 sonar issues found in the codebase, organized by rule type. The issues fall into these categories:

- **S6310**: Hardcoded Dispatchers (5 issues)
- **S1192**: String Duplication (2 issues)
- **S6517**: Single-Method Interfaces (7 issues)
- **S6532**: Replace if with check (3 issues)
- **S6514**: Interface Delegation (1 issue)
- **S1172**: Unused Parameter (1 issue)
- **S1135**: TODO Comment (1 issue)

---

## Sonar Issue Categories

### 1. S6310: Hardcoded Dispatchers (5 Issues)

**Rule**: "Avoid hardcoded dispatchers"
**Severity**: MAJOR
**Effort**: 10 min each

These issues occur where `Dispatchers.IO` is hardcoded directly in `withContext()` calls. The rule suggests injecting a `DispatcherProvider` to make the dispatcher configurable (important for testing and testability).

#### Issue 1: TmuxCommandRunner.kt (Line 24)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/util/TmuxCommandRunner.kt`

**Current Code**:
```kotlin
suspend fun run(vararg args: String): ProcessResult {
    return withContext(Dispatchers.IO) {  // <- S6310 hardcoded
        val process = ProcessBuilder("tmux", *args).start()
        // ... rest of implementation
    }
}
```

**Issue**: Line 24, columns 27-41 (`Dispatchers.IO`)

**Context**: This class runs tmux CLI commands and should dispatch on IO. Currently hardcoded.

---

#### Issue 2: RoleCatalogLoader.kt (Line 58)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/rolecatalog/RoleCatalogLoader.kt`

**Current Code**:
```kotlin
override suspend fun load(dir: Path): List<RoleDefinition> {
    // ...
    val roles = withContext(Dispatchers.IO) {  // <- S6310 hardcoded
        require(Files.exists(dir) && Files.isDirectory(dir)) {
            "Role catalog directory does not exist or is not a directory: $dir"
        }
        // ...
    }
}
```

**Issue**: Line 58, columns 32-46 (`Dispatchers.IO`)

**Context**: Reads `.md` files from disk within a suspend function. Uses IO dispatcher hardcoded.

---

#### Issue 3: ClaudeCodeAgentSessionIdResolver.kt (Line 41)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/impl/ClaudeCodeAgentSessionIdResolver.kt`

**Current Code** (FilesystemGuidScanner):
```kotlin
private class FilesystemGuidScanner(private val claudeProjectsDir: Path) : GuidScanner {
    override suspend fun scan(guid: HandshakeGuid): List<Path> = withContext(Dispatchers.IO) {  // <- S6310
        Files.walk(claudeProjectsDir)
            .use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .filter { it.extension == "jsonl" }
                    .filter { it.readText().contains(guid.value) }
                    .toList()
            }
    }
}
```

**Issue**: Line 41, columns 77-91 (`Dispatchers.IO`)

**Context**: Private inner class scanning the filesystem for GUID matches.

---

#### Issue 4: ContextForAgentProviderImpl.kt (Line 346)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`

**Current Code**:
```kotlin
private suspend fun writeInstructionsFile(outputDir: Path, sections: List<String>): Path =
    withContext(Dispatchers.IO) {  // <- S6310 hardcoded (line 346)
        Files.createDirectories(outputDir)
        val instructionsPath = outputDir.resolve("instructions.md")
        instructionsPath.toFile().writeText(sections.joinToString("\n\n---\n\n"))
        // ...
    }
```

**Issue**: Line 346, columns 20-34 (`Dispatchers.IO`)

**Context**: Writes instruction files to disk.

---

#### Issue 5: TicketParser.kt (Line 52)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/supporting/ticket/TicketParser.kt`

**Current Code**:
```kotlin
override suspend fun parse(path: Path): TicketData {
    out.debug("reading_ticket") {
        listOf(Val(path.toString(), ValType.FILE_PATH_STRING))
    }

    val content = withContext(Dispatchers.IO) { path.readText() }  // <- S6310 (line 52)
    // ...
}
```

**Issue**: Line 52, columns 34-48 (`Dispatchers.IO`)

**Context**: Reads ticket markdown file from disk.

---

### 2. S1192: String Duplication (2 Issues)

**Rule**: "Define a constant instead of duplicating this literal"
**Severity**: CRITICAL
**Effort**: 6 min each

These are literal strings that are duplicated multiple times across the codebase and should be extracted to constants.

#### Issue 1: TmuxCommunicator.kt (Line 87)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxCommunicator.kt`

**Current Code**:
```kotlin
override suspend fun sendKeys(paneTarget: String, text: String) {
    out.info(...)

    commandRunner.run("send-keys", "-t", paneTarget, "-l", text)  // <- "send-keys" #1
        .orThrow("send literal keys to tmux pane [$paneTarget]")

    commandRunner.run("send-keys", "-t", paneTarget, "Enter")     // <- "send-keys" #2
        .orThrow("send Enter to tmux pane [$paneTarget]")
}

override suspend fun sendRawKeys(paneTarget: String, keys: String) {
    out.info(...)

    commandRunner.run("send-keys", "-t", paneTarget, keys)        // <- "send-keys" #3
        .orThrow("send raw keys to tmux pane [$paneTarget]")
}
```

**Issue**: Line 87, columns 26-37 - literal `"send-keys"` duplicated 3 times across the class
- Line 87 (first occurrence)
- Line 91 (second duplication)
- Line 102 (third duplication)

**Context**: The string `"send-keys"` is the tmux command being invoked in all three places.

---

#### Issue 2: ContextForAgentProviderImpl.kt (Line 275)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`

**Current Code**:
```kotlin
private fun collectFeedbackFiles(feedbackDir: Path, status: String): String {
    val severities = listOf(...)
    return severities
        .map { severity -> feedbackDir.resolve(status).resolve(severity) }
        .flatMap { dir -> collectMarkdownFilesInDir(dir) }
        .joinToString("\n\n---\n\n")  // <- "\n\n---\n\n" #1 (line 283)
}

private fun collectFeedbackFilesInDir(dir: Path): String =
    collectMarkdownFilesInDir(dir).joinToString("\n\n---\n\n")  // <- "\n\n---\n\n" #2 (line 290)

private suspend fun writeInstructionsFile(outputDir: Path, sections: List<String>): Path =
    withContext(Dispatchers.IO) {
        Files.createDirectories(outputDir)
        val instructionsPath = outputDir.resolve("instructions.md")
        instructionsPath.toFile().writeText(sections.joinToString("\n\n---\n\n"))  // <- "\n\n---\n\n" #3 (line 349)
        // ...
    }
```

**Issue**: Line 275, columns 26-39 - literal `"\n\n---\n\n"` duplicated 3 times
- Line 283 (first occurrence in `collectFeedbackFiles`)
- Line 290 (second occurrence in `collectFeedbackFilesInDir`)
- Line 349 (third occurrence in `writeInstructionsFile`)

**Context**: This is a section separator used to join instruction sections together.

---

### 3. S6517: Single-Method Interfaces (7 Issues)

**Rule**: "Make this interface functional or replace it with a function type"
**Severity**: MAJOR
**Effort**: 5 min each

These interfaces have only one public method and could be replaced with Kotlin function types for simplicity. However, the decision to keep them as interfaces is architectural and depends on the design philosophy.

#### Issue 1: ContextForAgentProvider.kt (Line 25)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt`

**Current Code**:
```kotlin
interface ContextForAgentProvider {
    suspend fun assembleInstructions(request: AgentInstructionRequest): Path

    companion object {
        fun standard(outFactory: OutFactory): ContextForAgentProvider =
            ContextForAgentProviderImpl(outFactory)
    }
}
```

**Issue**: Line 25 - Single-method interface

**Notes**:
- Has a `companion object` with a factory method
- Could be replaced with a function type: `typealias ContextForAgentProvider = suspend (AgentInstructionRequest) -> Path`
- But the factory method pattern and explicit naming provide value

---

#### Issue 2: EnvironmentValidator.kt (Line 20)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/EnvironmentValidator.kt`

**Current Code**:
```kotlin
interface EnvironmentValidator {
  fun validate()

  companion object {
    fun standard(): EnvironmentValidator = EnvironmentValidatorImpl()
  }
}
```

**Issue**: Line 20 - Single-method interface

**Notes**:
- Has a factory method in companion object
- Single method: `validate()`
- Could be a function type but factory pattern provides clarity

---

#### Issue 3: ContextInitializer.kt (Line 56)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt`

**Current Code**:
```kotlin
interface ContextInitializer {
  suspend fun initialize(
    outFactory: OutFactory,
  ): ShepherdContext

  companion object {
    fun standard(): ContextInitializer = ContextInitializerImpl()
  }
}
```

**Issue**: Line 56 - Single-method interface

**Notes**:
- Has factory method
- Single suspend method
- Could be replaced with function type

---

#### Issue 4: AgentStarter.kt (Line 11)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/starter/AgentStarter.kt`

**Current Code**:
```kotlin
interface AgentStarter {
    fun buildStartCommand(): TmuxStartCommand
}
```

**Issue**: Line 11 - Single-method interface

**Notes**:
- Single method, no factory pattern
- Could be: `typealias AgentStarter = () -> TmuxStartCommand`
- No companion object here

---

#### Issue 5: AgentSessionIdResolver.kt (Line 26)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/AgentSessionIdResolver.kt`

**Current Code**:
```kotlin
interface AgentSessionIdResolver {
    suspend fun resolveSessionId(guid: HandshakeGuid, model: String): ResumableAgentSessionId
}
```

**Issue**: Line 26 - Single-method interface

**Notes**:
- Single suspend method
- Takes 2 parameters
- No factory method

---

#### Issue 6: RoleCatalogLoader.kt (Line 27)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/rolecatalog/RoleCatalogLoader.kt`

**Current Code**:
```kotlin
interface RoleCatalogLoader {
    suspend fun load(dir: Path): List<RoleDefinition>

    companion object {
        fun standard(outFactory: OutFactory): RoleCatalogLoader = RoleCatalogLoaderImpl(outFactory)
    }
}
```

**Issue**: Line 27 - Single-method interface

**Notes**:
- Has factory method with `outFactory` parameter
- Single suspend method

---

#### Issue 7: TicketParser.kt (Line 19)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/supporting/ticket/TicketParser.kt`

**Current Code**:
```kotlin
interface TicketParser {
    suspend fun parse(path: Path): TicketData

    companion object {
        fun standard(outFactory: OutFactory): TicketParser = TicketParserImpl(outFactory)
    }
}
```

**Issue**: Line 19 - Single-method interface

**Notes**:
- Has factory method pattern
- Single suspend method

---

### 4. S6532: Replace if with check() (3 Issues)

**Rule**: "Replace this if expression with check()"
**Severity**: MAJOR
**Effort**: 5 min each

These are simple if-not conditions that can be replaced with Kotlin's `check()` function for brevity and clarity.

#### Issue 1: ProcessResult.kt (Line 22)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/util/ProcessResult.kt`

**Current Code**:
```kotlin
internal fun ProcessResult.orThrow(operation: String) {
    if (exitCode != 0)
        throw IllegalStateException("Failed to $operation. Exit code: [$exitCode]. Stderr: [$stdErr]")
}
```

**Issue**: Line 22 - if statement checking `exitCode != 0`

**Suggested Replacement**:
```kotlin
internal fun ProcessResult.orThrow(operation: String) {
    check(exitCode == 0) { "Failed to $operation. Exit code: [$exitCode]. Stderr: [$stdErr]" }
}
```

**Context**: Extension function on ProcessResult for error checking.

---

#### Issue 2: EnvironmentValidator.kt (Line 49)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/EnvironmentValidator.kt`

**Current Code**:
```kotlin
private fun validateDockerEnvironment() {
    if (!Files.exists(dockerEnvFilePath)) {
      throw IllegalStateException(
        "TICKET_SHEPHERD must run inside a Docker container. " +
          "Docker sentinel file not found at [$dockerEnvFilePath]. " +
          "Agents are spawned with --dangerously-skip-permissions which is only safe inside a container."
      )
    }
}
```

**Issue**: Line 49 - if statement checking `!Files.exists(dockerEnvFilePath)`

**Suggested Replacement**:
```kotlin
private fun validateDockerEnvironment() {
    check(Files.exists(dockerEnvFilePath)) {
        "TICKET_SHEPHERD must run inside a Docker container. " +
          "Docker sentinel file not found at [$dockerEnvFilePath]. " +
          "Agents are spawned with --dangerously-skip-permissions which is only safe inside a container."
    }
}
```

---

#### Issue 3: EnvironmentValidator.kt (Line 63)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/EnvironmentValidator.kt`

**Current Code**:
```kotlin
private fun validateRequiredEnvVars() {
    val missing = Constants.REQUIRED_ENV_VARS.ALL.filter { envVarName ->
      envVarReader(envVarName).isNullOrBlank()
    }

    if (missing.isNotEmpty()) {
      throw IllegalStateException(
        "Required environment variables are missing or blank: $missing"
      )
    }
}
```

**Issue**: Line 63 - if statement checking `missing.isNotEmpty()`

**Suggested Replacement**:
```kotlin
private fun validateRequiredEnvVars() {
    val missing = Constants.REQUIRED_ENV_VARS.ALL.filter { envVarName ->
      envVarReader(envVarName).isNullOrBlank()
    }

    check(missing.isEmpty()) {
      "Required environment variables are missing or blank: $missing"
    }
}
```

---

### 5. S6514: Interface Delegation (1 Issue)

**Rule**: "Replace with interface delegation using \"by\" in the class header"
**Severity**: MAJOR
**Effort**: 5 min

#### Issue: ShepherdContext.kt (Line 26)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/data/ShepherdContext.kt`

**Current Code**:
```kotlin
class ShepherdContext(
  val infra: Infra,
  val timeoutConfig: HarnessTimeoutConfig = HarnessTimeoutConfig.defaults(),
) : AsgardCloseable {

  override suspend fun close() {

    // Infra should be the last to be closed as it contains the out factory which is used
    // for logging, and we may want to log while we are shutting down.
    infra.close()
  }
}
```

**Issue**: Line 26 - Manual delegation of AsgardCloseable interface

**Suggested Replacement**:
```kotlin
class ShepherdContext(
  val infra: Infra,
  val timeoutConfig: HarnessTimeoutConfig = HarnessTimeoutConfig.defaults(),
) : AsgardCloseable by infra {
  // Comment explaining why infra is closed last can be kept as a class-level comment
}
```

**Context**: The `close()` method just delegates to `infra.close()`, so Kotlin's delegation syntax is appropriate.

---

### 6. S1172: Unused Parameter (1 Issue)

**Rule**: "Remove this unused function parameter"
**Severity**: MAJOR
**Effort**: 5 min

#### Issue: AppMain.kt (Line 19)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt`

**Current Code**:
```kotlin
fun main(args: Array<String>) {
  // Step 0: Validate environment before any infrastructure is created.
  EnvironmentValidator.standard().validate()

  // [runBlocking] is acceptable at main() entry points per Kotlin development standards.
  runBlocking {
    // TODO: Implement Initializer that orchestrates:
    //   1. ContextInitializer → ShepherdContext
    //   2. ShepherdServer startup (Ktor CIO)
    //   3. TicketShepherdCreator → TicketShepherd
    //   4. TicketShepherd.run()
    //   5. Cleanup (ShepherdContext.close())
    TODO("CLI not yet implemented — see high-level.md ap.HRlQHC1bgrTRyRknP3WNX.E")
  }
}
```

**Issue**: Line 19 - Parameter `args` is not used in the function body

**Suggested Fix**: Remove the unused parameter:
```kotlin
fun main() {
  // ... rest of the code
}
```

**Note**: Since `main()` is the JVM entry point, it must have a specific signature. The parameter cannot actually be removed if this is the entry point. This is a common false positive for CLI entry points that don't process arguments yet (as noted by the TODO comment indicating the CLI is not yet implemented).

---

### 7. S1135: TODO Comment (1 Issue)

**Rule**: "Complete the task associated to this TODO comment"
**Severity**: INFO
**Effort**: 0 min

#### Issue: AppMain.kt (Line 26)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt`

**Current Code**:
```kotlin
runBlocking {
    // TODO: Implement Initializer that orchestrates:
    //   1. ContextInitializer → ShepherdContext
    //   2. ShepherdServer startup (Ktor CIO)
    //   3. TicketShepherdCreator → TicketShepherd
    //   4. TicketShepherd.run()
    //   5. Cleanup (ShepherdContext.close())
    TODO("CLI not yet implemented — see high-level.md ap.HRlQHC1bgrTRyRknP3WNX.E")
}
```

**Issue**: Line 26 - TODO comment/function indicates incomplete functionality

**Context**: The CLI application is still under development. This is expected for work-in-progress code and is marked as INFO severity (not a critical issue).

---

## DispatcherProvider Pattern

The codebase currently does not have a `DispatcherProvider` abstraction. To fix the S6310 issues, we need to:

1. **Create a DispatcherProvider interface** (recommended location: `core/infra/DispatcherProvider.kt`)
   ```kotlin
   interface DispatcherProvider {
       fun io(): CoroutineDispatcher
   }
   ```

2. **Create a default implementation** with `Dispatchers.IO`

3. **Inject into all classes** that currently use hardcoded `Dispatchers.IO`

4. **Update DI** (in `ContextInitializer` and other initialization points) to wire the provider

---

## Constants Pattern in Use

The project already uses a centralized `Constants` object:

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/Constants.kt`

```kotlin
object Constants {
  object CLAUDE_CODE {
    fun defaultProjectsDir(): java.nio.file.Path = ...
  }

  object AGENT_COMM {
    const val HANDSHAKE_GUID_ENV_VAR = "TICKET_SHEPHERD_HANDSHAKE_GUID"
  }

  object REQUIRED_ENV_VARS {
    const val HOST_USERNAME = "HOST_USERNAME"
    const val TICKET_SHEPHERD_AGENTS_DIR = "TICKET_SHEPHERD_AGENTS_DIR"
    const val MY_ENV = "MY_ENV"
    val ALL: List<String> = listOf(...)
  }
}
```

This pattern should be extended to include the duplicate string literals:
- Add a new nested object for tmux commands/separators
- Define constants for `"send-keys"` and `"\n\n---\n\n"`

---

## Interface Implementation Patterns

Based on code review, the project uses:

1. **Single Implementation Pattern**: Interface with companion object factory
   - Example: `EnvironmentValidator` → `EnvironmentValidatorImpl`
   - Factory: `companion object { fun standard(): EnvironmentValidator = EnvironmentValidatorImpl() }`
   - Used for all S6517 interfaces

2. **Private Inner Implementation**: For internal dependencies
   - Example: `GuidScanner` interface with private `FilesystemGuidScanner` implementation
   - Injected as a field in `ClaudeCodeAgentSessionIdResolver`

3. **Direct Delegation**: Some interfaces just wrap other implementations
   - Example: `ShepherdContext.AsgardCloseable` delegates to `infra.close()`
   - Candidate for S6514 fix (use `by` delegation)

---

## Summary Table

| Rule | Count | Severity | Effort | Files Affected |
|------|-------|----------|--------|-----------------|
| S6310 | 5 | MAJOR | 10 min × 5 = 50 min | TmuxCommandRunner, RoleCatalogLoader, ClaudeCodeAgentSessionIdResolver, ContextForAgentProviderImpl, TicketParser |
| S1192 | 2 | CRITICAL | 6 min × 2 = 12 min | TmuxCommunicator, ContextForAgentProviderImpl |
| S6517 | 7 | MAJOR | 5 min × 7 = 35 min | ContextForAgentProvider, EnvironmentValidator, ContextInitializer, AgentStarter, AgentSessionIdResolver, RoleCatalogLoader, TicketParser |
| S6532 | 3 | MAJOR | 5 min × 3 = 15 min | ProcessResult, EnvironmentValidator (2 occurrences) |
| S6514 | 1 | MAJOR | 5 min | ShepherdContext |
| S1172 | 1 | MAJOR | 5 min | AppMain |
| S1135 | 1 | INFO | 0 min | AppMain |
| **TOTAL** | **21** | **Various** | **~132 min** | **11 files** |

---

## Priority Recommendation

1. **High Priority (CRITICAL)**: S1192 string duplication - 2 issues, quick wins
2. **High Priority (Architectural)**: S6310 hardcoded dispatchers - 5 issues, requires DispatcherProvider abstraction
3. **Medium Priority**: S6532 if/check conversion - 3 issues, straightforward refactoring
4. **Medium Priority**: S6514 delegation - 1 issue, simple code improvement
5. **Low Priority**: S6517 functional interfaces - 7 issues, architectural decision required (keep as is or convert)
6. **Low Priority**: S1172/S1135 - 2 issues, maintenance/cleanup

