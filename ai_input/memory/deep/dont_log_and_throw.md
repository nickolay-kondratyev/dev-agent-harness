---
desc: "Do NOT log and throw. Let exceptions bubble up, log at the top-most layer only."
---

## Anti-pattern: Log and Throw

Do NOT log an error and then throw an exception. This results in duplicate logging at higher layers.

### Bad
```kotlin
// BAD — logs within the logic layer and then throws
out.error("configured_port_occupied", Val(port, ValType.COUNT))
throw PortConflictException(port)
```

### Good
```kotlin
// GOOD — just throw, let the top-most layer handle logging
throw PortConflictException(port)
```

### Rule
Allow the exception to bubble up and be logged at the **top-most layer** only. The top-most layer (e.g., main entry point, top-level error handler) is the single place where exceptions are both logged and handled.
