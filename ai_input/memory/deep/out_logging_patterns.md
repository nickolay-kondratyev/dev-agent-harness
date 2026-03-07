---
desc: "How to use Out structured logging. Covers Out interface, Val, ValType, and lazy debug patterns."
---

## Out Logging System

### Getting an Out instance
```kotlin
import com.asgard.core.out.OutFactory

class MyClass(outFactory: OutFactory) {
  private val out = outFactory.getOutForClass(MyClass::class)
}
```

### Log Levels (least to most severe)
`TRACE` < `DEBUG` < `INFO_CHATTER` < `INFO_VERBOSE` < `INFO` < `INFO_HIGHLIGHT` < `DATA_ERROR` < `WARN` < `ERROR` < `FATAL`

### Key Methods
```kotlin
// Debug with lazy-evaluated values (values only computed if DEBUG enabled)
out.debug("message_snake_case") {
  listOf(Val(value, ValType.SPECIFIC_TYPE))
}

// Debug without values
out.debugWithoutValues("message_snake_case")

// Info with vararg values (always evaluated)
out.info("message_snake_case", Val(value, ValType.SPECIFIC_TYPE))

// Info with lazy values
out.info("message_snake_case") {
  listOf(Val(value1, ValType.TYPE1), Val(value2, ValType.TYPE2))
}

// Warn and error
out.warn("message_snake_case", Val(value, ValType.SPECIFIC_TYPE))
out.error("message_snake_case", Val(value, ValType.SPECIFIC_TYPE))
out.error(exception, Val(context, ValType.SPECIFIC_TYPE))
```

### Val and ValType
```kotlin
import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
```

### Rules
- All Out methods are `suspend` functions.
- Use **lazy lambda** form for DEBUG/TRACE to avoid serialization overhead.
- Use **snake_case** for log message strings.
- Never embed values in message strings — always pass as `Val` parameters.
- ValType must be **semantically specific** to the value (e.g., `ValType.FILE_PATH_STRING` not `ValType.STRING`).
- `toVal()` extensions available for: `Throwable`, `Duration`, `Enum`.

### Anti-pattern: Do NOT log and throw
Let exceptions bubble up. Log at the top-most layer only. See `dont_log_and_throw.md`.
