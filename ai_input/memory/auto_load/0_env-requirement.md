
## Environment Prerequisites (ap.MKHNCkA2bpT63NAvjCnvbvsb.E)

### `THORG_ROOT` (only needed for publishing asgard libraries)
`THORG_ROOT` is NOT required for regular builds. `./gradlew :app:build` works without it.

`THORG_ROOT` is only required when explicitly publishing asgard libraries to maven local:

```bash
export THORG_ROOT=$HOME/thorg-root
./gradlew publishAsgardToMavenLocal
```

### Asgard Libraries in Maven Local (required for build)

`./gradlew :app:build` requires `com.asgard:asgardCore:1.0.0` and `com.asgard:asgardTestTools:1.0.0`
to be present in `~/.m2`. Check status with:

```bash
./gradlew checkAsgardInMavenLocal
```

If missing, publish them:

```bash
export THORG_ROOT=$HOME/thorg-root
./gradlew publishAsgardToMavenLocal
```
