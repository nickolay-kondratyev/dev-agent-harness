
## Environment Prerequisites (ap.MKHNCkA2bpT63NAvjCnvbvsb.E)

### Asgard Libraries in Maven Local (required for build)

`./gradlew :app:build` requires `com.asgard:asgardCore:1.0.0` and `com.asgard:asgardTestTools:1.0.0`
to be present in `~/.m2`.

**Self-healing scripts** (`test.sh`, `test_with_integ.sh`) auto-publish missing libs before invoking
Gradle. No manual setup needed when using these scripts (ref.ap.gtpABfFlF4RE1SITt7k1P.E).

```bash
# Check status manually:
./gradlew checkAsgardInMavenLocal
```

```bash
# Publish manually if needed (e.g., when calling `./gradlew :app:build` directly):
./gradlew publishAsgardToMavenLocal
```

Or use the pre-build script directly:

```bash
bash _prepare_pre_build.sh
./gradlew :app:build
```

### QA
- Why Gradle `dependsOn` Cannot Self-Heal: ref.ap.D8WYDcHnzOjzU6v0206H2.E
