# Implementation Plan: Add Environment Interface

## Goal
Add an `Environment` interface with `ProductionEnvironment` and `TestEnvironment` implementations,
update `Initializer.initialize()` to accept it as a default parameter, and add unit tests.

## Steps
- [x] Read exploration context and existing files
- [x] Create `Environment.kt` at `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/data/Environment.kt`
- [x] Update `Initializer.kt`: add `environment: Environment = Environment.production()` parameter to both interface and impl
- [x] Create `EnvironmentTest.kt` at `app/src/test/kotlin/com/glassthought/chainsaw/core/initializer/data/EnvironmentTest.kt`
- [x] Run tests and verify — BUILD SUCCESSFUL, 2/2 tests passed
- [x] Iteration 1 (reviewer feedback):
  - [x] Changed `interface Environment` to `sealed interface Environment`
  - [x] Made `ProductionEnvironment` and `TestEnvironment` `internal`
  - [x] Added TODO comment in `initializeImpl` at `ap.ifrXkqXjkvAajrA4QCy7V.E`
  - [x] Run tests and verify — BUILD SUCCESSFUL

## Status: COMPLETE
