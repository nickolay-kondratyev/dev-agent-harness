This is a Kotlin CLI application that demonstrates integration with AsgardCore via composite Gradle build.

## Environment Prerequisites

### `THORG_ROOT` (required)
The build depends on `THORG_ROOT` being set in the environment. Without it, `./gradlew :app:build` will fail.

`THORG_ROOT` must point to the root of the `thorg-root` submodule (checked in under `submodules/thorg-root`):

```bash
export THORG_ROOT=$PWD/submodules/thorg-root
```

This is needed because the composite build in `settings.gradle.kts` includes
`submodules/thorg-root/source/libraries/kotlin-mp`, and that build uses `THORG_ROOT` internally
(e.g., for resolving version catalogs and sub-project paths).
