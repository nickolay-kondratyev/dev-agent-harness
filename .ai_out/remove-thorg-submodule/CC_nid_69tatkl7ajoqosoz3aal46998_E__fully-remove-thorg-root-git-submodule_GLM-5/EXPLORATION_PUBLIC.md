# Exploration Report: Fully Remove thorg-root Git Submodule

## Executive Summary

This exploration identifies all references to `submodules/thorg-root`, `.gitmodules`, `THORG_ROOT` environment variable usage, and IDEA module exclusion configurations in the codebase. The goal is to understand what needs to be modified when removing the thorg-root git submodule entirely.

**Key Finding**: The thorg-root submodule has ALREADY been partially removed in a previous task (nid_0h5gb1m47hyo0ljxb7v432q2k_E). The composite build has been removed, and dependencies now come from maven local. However, the actual git submodule directory and `.gitmodules` entry still exist.

---

## 1. Git Submodule Configuration

### 1.1 `.gitmodules` File
**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/.gitmodules`

```git
[submodule "submodules/thorg-root"]
	path = submodules/thorg-root
	url = git@gitlab.com:thorg/thorg-root.git
```

**Status**: Currently contains the thorg-root entry
**Action Required**: Must remove this entry

### 1.2 Submodule Status
```bash
$ git submodule status
4f9b3e32b77302c1d94395283dd18b78510ffc3e submodules/thorg-root (lucene_works_with_TEXOPHEN-13794-g4f9b3e32b)
```

**Status**: Submodule is present in working tree with "modified content"
**Action Required**: Unregister and remove the submodule

---

## 2. Build System References

### 2.1 Root `build.gradle.kts` (ALREADY MODIFIED)

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/build.gradle.kts`

#### 2.1.1 IDEA Module Exclusions (Lines 7-36)

The gradle idea plugin is configured to exclude most of thorg-root from IntelliJ IDEA indexing. This configuration should be **REMOVED** as the submodule itself will be deleted.

```kotlin
idea {
    module {
        val thorgRoot = file("submodules/thorg-root")
        
        // Exclude all top-level dirs under thorg-root except "source"
        thorgRoot.listFiles()
            ?.filter { it.isDirectory && it.name != "source" }
            ?.let { excludeDirs.addAll(it) }
        
        // Exclude all dirs under source/ except "libraries"
        val sourceDir = thorgRoot.resolve("source")
        if (sourceDir.exists()) {
            sourceDir.listFiles()
                ?.filter { it.isDirectory && it.name != "libraries" }
                ?.let { excludeDirs.addAll(it) }
            
            // Exclude all dirs under source/libraries/ except "kotlin-mp"
            val librariesDir = sourceDir.resolve("libraries")
            if (librariesDir.exists()) {
                librariesDir.listFiles()
                    ?.filter { it.isDirectory && it.name != "kotlin-mp" }
                    ?.let { excludeDirs.addAll(it) }
            }
        }
    }
}
```

**Action Required**: Remove the entire `idea { ... }` block (lines 7-36)

#### 2.1.2 `publishAsgardToMavenLocal` Task (Lines 38-82)

**Status**: This task still references `submodules/thorg-root` path directly.

```kotlin
tasks.register("publishAsgardToMavenLocal") {
    group = "publishing"
    description = "Publishes asgard libraries to maven local. Requires THORG_ROOT to be set."
    
    doLast {
        val thorgRoot = System.getenv("THORG_ROOT")
            ?: throw GradleException(
                "THORG_ROOT is not set. Set it before running this task:\n" +
                "  export THORG_ROOT=\$PWD/submodules/thorg-root"
            )
        
        // Resolve path relative to project dir using Java File (not Gradle file())
        val kotlinMpDir = java.io.File(project.projectDir, "submodules/thorg-root/source/libraries/kotlin-mp")
        
        if (!kotlinMpDir.exists()) {
            throw GradleException(
                "Submodule not initialized. Run: git submodule update --init"
            )
        }
        
        val processBuilder = ProcessBuilder("./gradlew", "publishAsgardLibsToMavenLocal")
            .directory(kotlinMpDir)
            .also { it.environment()["THORG_ROOT"] = thorgRoot }
            .inheritIO()
        val exitCode = processBuilder.start().waitFor()
        if (exitCode != 0) {
            throw GradleException("publishAsgardLibsToMavenLocal failed with exit code $exitCode")
        }
    }
}
```

**Action Required**: 
- Update the default error message to point to standalone THORG_ROOT location (not submodule)
- Remove the check for `submodules/thorg-root/source/libraries/kotlin-mp` existence
- Update documentation to indicate THORG_ROOT now points to standalone checkout (not submodule)

#### 2.1.3 `checkAsgardInMavenLocal` Task (Lines 84-115)

**Status**: No direct references to submodule paths, but may need update for consistency.

**Action Required**: Keep as-is (no submodule references)

---

### 2.2 `settings.gradle.kts`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/settings.gradle.kts`

**Status**: NO references to thorg-root/submodules

**Action Required**: None (already clean)

---

### 2.3 `app/build.gradle.kts`

**Status**: No direct references to thorg-root/submodules

**Action Required**: None

---

## 3. Environment Variable Usage (`THORG_ROOT`)

### 3.1 Documentation Files

#### 3.1.1 `CLAUDE.md` (Lines 6, 12, 28)

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/CLAUDE.md`

```markdown
### `THORG_ROOT` (only needed for publishing asgard libraries)
`THORG_ROOT` is NOT required for regular builds. `./gradlew :app:build` works without it.

`THORG_ROOT` is only required when explicitly publishing asgard libraries to maven local:

```bash
export THORG_ROOT=$PWD/submodules/thorg-root
./gradlew publishAsgardToMavenLocal
```

### `THORG_ROOT` (required)
The build depends on `THORG_ROOT` being set in the environment. Without it, `./gradlew :app:build` will fail.

`THORG_ROOT` must point to the root of the `thorg-root` submodule (checked in under `submodules/thorg-root`):

export THORG_ROOT=$PWD/submodules/thorg-root
```

**Action Required**: 
- Remove the second instance (lines 48-53) — it's outdated from the previous implementation
- Update examples to point to standalone checkout instead of submodule (e.g., `$HOME/thorg-root` or `$ENV_HOME/thorg-root`)

#### 3.1.2 `ai_input/memory/auto_load/0_env-requirement.md`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/ai_input/memory/auto_load/0_env-requirement.md`

**Status**: Already updated correctly (nid_0h5gb1m47hyo0ljxb7v432q2k_E)

**Current content**:
```markdown
### `THORG_ROOT` (only needed for publishing asgard libraries)
`THORG_ROOT` is NOT required for regular builds. `./gradlew :app:build` works without it.

`THORG_ROOT` is only required when explicitly publishing asgard libraries to maven local:

```bash
export THORG_ROOT=$PWD/submodules/thorg-root
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
export THORG_ROOT=$PWD/submodules/thorg-root
./gradlew publishAsgardToMavenLocal
```
```

**Action Required**: Update the example to point to standalone checkout:
```bash
export THORG_ROOT=$HOME/thorg-root
```

### 3.2 Shell Scripts

#### 3.2.1 `test.sh` (Line 4)

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/test.sh`

```bash
export THORG_ROOT=$PWD/submodules/thorg-root
```

**Action Required**: Update to point to standalone checkout:
```bash
export THORG_ROOT=$HOME/thorg-root
```

#### 3.2.2 `test_with_integ.sh` (Line 4)

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/test_with_integ.sh`

```bash
export THORG_ROOT=$PWD/submodules/thorg-root
```

**Action Required**: Update to point to standalone checkout:
```bash
export THORG_ROOT=$HOME/thorg-root
```

### 3.3 `build.gradle.kts` Default Error Messages

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/build.gradle.kts`

**Lines**: 58-61, 68-71, 110-112

**Status**: References `$PWD/submodules/thorg-root` in error messages

**Action Required**: Update error messages to reference standalone THORG_ROOT location

---

## 4. Shell Scripts in thorg-root Submodule

**Note**: These scripts are internal to thorg-root and should NOT be modified. They will remain in the standalone thorg-root checkout.

### Files that use `THORG_ROOT`:
1. `submodules/thorg-root/build.sh`
2. `submodules/thorg-root/release.sh` (multiple uses)
3. `submodules/thorg-root/docker_this.sh`
4. `submodules/thorg-root/third-party-repos/build_scripts/common/download.sh`
5. `submodules/thorg-root/third-party-repos/build_scripts/common/common.sh`
6. `submodules/thorg-root/third-party-repos/build_scripts/android/openssl/build-openssl.sh`
7. `submodules/thorg-root/build_and_run_thorg_tests_on_linux_prior_to_release.sh`

**Action Required**: None (these scripts stay in thorg-root)

---

## 5. Documentation and Wiki Files

### 5.1 Dendron Notes

**Directory**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/submodules/thorg-root/dendron_notes/`

Contains dendron configuration files that reference thorg-root. These are part of thorg-root and will remain unchanged.

**Action Required**: None (these stay in thorg-root)

---

## 6. AI Output Files

The following AI output files reference `submodules/thorg-root` but are documentation/internal and should be preserved as historical records:

**Directory**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/.ai_out/remove-thorg-submodule/CC_nid_0h5gb1m47hyo0ljxb7v432q2k_E__remove-thorg-submodule_sonnet-v4.6/`

Key files:
- `EXPLORATION_PUBLIC.md` (37 references)
- `IMPLEMENTATION_REVIEW__PUBLIC.md` (2 references)
- `IMPLEMENTATION_ITERATION__PUBLIC.md` (2 references)
- `DETAILED_PLAN_REVIEW__PUBLIC.md` (1 reference)
- `DETAILED_PLANNING__PUBLIC.md` (5 references)

**Action Required**: None (preserve as historical documentation)

---

## 7. AI Output Files (add-gradle-idea-plugin)

**Directory**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/.ai_out/add-gradle-idea-plugin-to-be-able-to-exclude-most-of-the-submodule-thorg-root/`

Contains exploration and implementation notes about excluding thorg-root from IDEA indexing. This was the task (nid_wgr6tfn0labkt9py8h24ikzwq_E) that added the IDEA exclusion logic now being removed.

**Action Required**: None (preserve as historical documentation)

---

## 8. Ticket Files

### 8.1 `_tickets/fully-remove-thorg-root-git-submodule.md`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/_tickets/fully-remove-thorg-root-git-submodule.md`

**Status**: This is the active ticket for the full removal

**Content summary**:
- Goal: Remove the thorg-root git submodule entirely
- Strategy: Rely on $THORG_ROOT env var pointing to standalone checkout
- Requirements:
  - Remove `submodules/thorg-root` directory
  - Remove `.gitmodules` entry
  - Update all documentation and scripts
  - Update build.gradle.kts to reference standalone THORG_ROOT

**Action Required**: This is the ticket being executed by the current agent

### 8.2 `_tickets/remove-thorg-submodule.md` (closed)

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/_tickets/remove-thorg-submodule.md`

**Status**: Closed (nid_0h5gb1m47hyo0ljxb7v432q2k_E)

**Achievements**:
- Removed includeBuild composite build from settings.gradle.kts
- Added mavenLocal() to app/build.gradle.kts
- Added publishAsgardToMavenLocal + checkAsgardInMavenLocal tasks to root build.gradle.kts
- Updated THORG_ROOT documentation (partially)
- Asgard libs now pulled from maven local

**Limitation**: Did not actually remove the git submodule itself

**Action Required**: None (this was the prerequisite task)

---

## 9. Summary of Files to Modify

### 9.1 Direct Modifications Required

| File | Lines | Change |
|------|-------|--------|
| `build.gradle.kts` | 7-36 | Remove entire `idea { ... }` block |
| `build.gradle.kts` | 58-61 | Update error message (change $PWD/submodules/thorg-root to standalone) |
| `build.gradle.kts` | 68-71 | Update error message for submodule not found |
| `build.gradle.kts` | 110-112 | Update error message in checkAsgardInMavenLocal |
| `CLAUDE.md` | 6 | Keep (already correct) |
| `CLAUDE.md` | 12 | Keep (already correct) |
| `CLAUDE.md` | 28-53 | Remove outdated second instance |
| `ai_input/memory/auto_load/0_env-requirement.md` | 12 | Update $PWD/submodules/thorg-root → $HOME/thorg-root |
| `ai_input/memory/auto_load/0_env-requirement.md` | 26 | Update $PWD/submodules/thorg-root → $HOME/thorg-root |
| `test.sh` | 4 | Update $PWD/submodules/thorg-root → $HOME/thorg-root |
| `test_with_integ.sh` | 4 | Update $PWD/submodules/thorg-root → $HOME/thorg-root |
| `.gitmodules` | 1-3 | Remove entire file content |
| `settings.gradle.kts` | - | None (already clean) |

### 9.2 Files to Delete

1. **Directory**: `submodules/thorg-root/` (entire directory)
2. **File**: `.gitmodules` (after editing, then delete)

### 9.3 Files to Preserve (No Changes)

- All thorg-root source code (will be in standalone checkout)
- All thorg-root scripts using THORG_ROOT
- All dendron notes
- All AI output files in `.ai_out/`
- All ticket files in `_tickets/`

---

## 10. Git Commands to Execute

```bash
# 1. Remove submodule from git (keeps directory)
git rm --cached submodules/thorg-root

# 2. Remove .gitmodules entry (manual or using git config)
git config -f .gitmodules --remove-section submodule.submodules/thorg-root
git config --unset -f .gitmodules submodule.submodules/thorg-root

# 3. Delete submodule directory
rm -rf submodules/thorg-root

# 4. Delete .gitmodules file (after editing)
rm .gitmodules

# 5. Commit changes
git add -A
git commit -m "Remove thorg-root git submodule entirely"

# 6. Update THORG_ROOT references in documentation (manual step)
# Edit all files listed in Section 9.1
```

---

## 11. Next Steps for Complete Removal

### Phase 1: Documentation Updates (Manual)
1. Update `ai_input/memory/auto_load/0_env-requirement.md` (2 locations)
2. Update `test.sh` (1 location)
3. Update `test_with_integ.sh` (1 location)
4. Update `CLAUDE.md` (remove outdated section)

### Phase 2: Build Script Updates (Manual or Automated)
1. Update `build.gradle.kts` error messages (3 locations)

### Phase 3: Git Submodule Removal (Automated)
1. Execute git commands to unregister and remove submodule

### Phase 4: Verification
1. Run `./gradlew :app:build` to ensure no submodule references
2. Run `./gradlew publishAsgardToMavenLocal --dry-run` to verify error messages work
3. Check that THORG_ROOT still works for standalone thorg-root

---

## 12. Potential Issues and Considerations

### 12.1 Development Environments
Users who have standalone THORG_ROOT checked out at non-standard locations may need to update their environment configuration.

**Mitigation**: Document this in release notes or update environment setup instructions.

### 12.2 CI/CD Pipelines
Check if CI/CD uses `THORG_ROOT=$PWD/submodules/thorg-root` anywhere. If yes, update.

**Action Required**: Search CI configuration files for `THORG_ROOT` and `submodules/thorg-root` references.

### 12.3 IDE Configuration
IDEA projects will have stale references to thorg-root. Users should:
1. File → Invalidate Caches / Restart
2. File → Close Project and Reopen

---

## 13. References to Similar Work

### 13.1 Previous Task: nid_wgr6tfn0labkt9py8h24ikzwq_E
**Title**: "Add gradle idea plugin to be able to exclude most of the submodule thorg root"

This task added the IDEA exclusion logic that is now being removed. The task notes are preserved in:
`.ai_out/add-gradle-idea-plugin-to-be-able-to-exclude-most-of-the-submodule-thorg-root/`

### 13.2 Previous Task: nid_zdmfnpevga8p1im90xmk2g9bm_E
**Title**: "Decouple from THORG_ROOT env variable"

This task documented the requirement for THORG_ROOT to work with standalone checkouts.

---

## Conclusion

The thorg-root git submodule removal is a straightforward process with clear file modifications required. The main changes are:

1. Remove IDEA exclusion block from `build.gradle.kts` (lines 7-36)
2. Update 5 documentation/error message references to point to standalone THORG_ROOT
3. Remove `.gitmodules` entry and entire `submodules/thorg-root` directory via git

The previous work (nid_0h5gb1m47hyo0ljxb7v432q2k_E) successfully switched from composite build to maven local resolution, making this final removal straightforward.
