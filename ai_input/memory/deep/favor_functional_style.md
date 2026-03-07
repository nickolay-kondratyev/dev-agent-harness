---
desc: "Prefer functional collection operations (map, filter, zip, takeWhile) over manual loops with index tracking."
---

## Favor Functional Style

Prefer functional collection operations over manual loops, especially when tracking indices across multiple collections.

### Example

Instead of:
```kotlin
var i = 0
var j = 0
var count = 0
while (i < list1.size && j < list2.size) {
    if (condition(list1[i], list2[j])) {
        count++
        i++
        j++
    } else break
}
```

Do this:
```kotlin
val count = list1.zip(list2)
    .takeWhile { (a, b) -> condition(a, b) }
    .count()
```

### Benefits
- More declarative and readable
- Eliminates index management errors
- Clearly expresses intent

### Common Operations
- `map`, `filter`, `flatMap` for transformations
- `zip` for parallel iteration
- `takeWhile`, `dropWhile` for conditional sequences
- `fold`, `reduce` for accumulation
- `groupBy`, `associate` for restructuring
- `any`, `all`, `none` for predicate checks
