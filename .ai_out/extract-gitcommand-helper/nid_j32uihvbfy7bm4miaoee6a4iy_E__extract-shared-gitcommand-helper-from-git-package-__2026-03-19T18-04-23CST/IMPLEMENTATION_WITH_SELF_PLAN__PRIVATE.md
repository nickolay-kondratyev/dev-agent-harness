# Implementation Private Notes

## Status: COMPLETED

## Plan (all steps done)

1. [x] Create `GitCommandBuilder` class
2. [x] Update `GitBranchManagerImpl` constructor and usages
3. [x] Update `CommitPerSubPart` constructor and usages
4. [x] Update `WorkingTreeValidatorImpl` constructor and usages
5. [x] Update `CommitPerSubPartTest` constructor calls (only 1 call used `workingDir`)
6. [x] Write `GitCommandBuilderTest`
7. [x] Fix detekt baseline (3 SpreadOperator entries referenced old method name)
8. [x] Run `./test.sh` — all green

## Notes

- Detekt baseline needed updating because the SpreadOperator entries referenced `*gitCommand(...)` which changed to `*gitCommandBuilder.build(...)`.
- No other callers of the three class constructors were found outside the companion factory methods and tests.
