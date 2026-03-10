
## Environment Prerequisites (ap.MKHNCkA2bpT63NAvjCnvbvsb.E)

### Asgard Libraries in Maven Local (required for build)

`./gradlew :app:build` requires `com.asgard:asgardCore:1.0.0` and `com.asgard:asgardTestTools:1.0.0`
to be present in `~/.m2`.

**Self-healing scripts** (`test.sh`, `test_with_integ.sh`) auto-publish missing libs before invoking
Gradle. No manual setup needed when using these scripts (ref.ap.gtpABfFlF4RE1SITt7k1P.E).

Check status manually:

```bash
./gradlew checkAsgardInMavenLocal
```

Publish manually if needed (e.g., when calling `./gradlew :app:build` directly):

```bash
export THORG_ROOT=$PWD/submodules/thorg-root
./gradlew publishAsgardToMavenLocal
```

Or use the pre-build script directly:

```bash
bash _prepare_pre_build.sh
./gradlew :app:build
```

### Why Gradle `dependsOn` Cannot Self-Heal

Gradle resolves maven coordinates (e.g. `com.asgard:asgardCore:1.0.0`) at **configuration time**,
before any task executes. A `dependsOn ensureAsgardInMavenLocal` wiring in `compileKotlin` cannot
heal a missing dependency — the build fails at configuration before the healing task can run.
`_prepare_pre_build.sh` solves this by running before Gradle starts.
