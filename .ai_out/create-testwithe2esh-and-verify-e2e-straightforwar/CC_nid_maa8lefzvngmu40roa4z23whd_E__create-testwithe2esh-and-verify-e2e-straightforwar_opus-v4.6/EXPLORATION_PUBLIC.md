# Exploration Summary: Create test_with_e2e.sh

## Task
Create `test_with_e2e.sh` that runs ONLY `StraightforwardWorkflowE2EIntegTest` and verify it passes.

## Key Pattern: test_with_integ.sh
```bash
#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_prepare_pre_build.sh"
_prepare_asgard_dependencies
mkdir -p .tmp/
./gradlew :app:installDist 2>&1 | tee .tmp/installDist.txt
./gradlew :app:test -PrunIntegTests=true 2>&1 | tee .tmp/test_with_integ.txt
```

## What test_with_e2e.sh needs:
1. Same asgard prep pattern
2. `./gradlew :app:installDist` (binary must exist for ProcessBuilder)
3. `./gradlew :app:test -PrunIntegTests=true --tests "com.glassthought.shepherd.integtest.e2e.StraightforwardWorkflowE2EIntegTest"`
4. Output to `.tmp/test_with_e2e.txt`

## E2E Test Requirements
- **Env vars**: `MY_ENV`, `TICKET_SHEPHERD_AGENTS_DIR` (required), `HOST_USERNAME` (optional), `AI_MODEL__ZAI__FAST` (optional)
- **GLM token**: Must exist at `${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN`
- **Binary**: `app/build/install/app/bin/app` (built by installDist)
- **tmux**: Required for agent subprocess spawning
- **Timeout**: 15 minutes
- **Test class**: `com.glassthought.shepherd.integtest.e2e.StraightforwardWorkflowE2EIntegTest`

## Assertions
1. Process exits with code 0
2. `.ai_out/` directory created in temp repo
3. Feature branch created (not on main/master)
