# Fork Actions build-speed improvements

## Baseline and evidence

The user requested faster builds, then explicitly asked to install the already
validated source-count build first. That TV installation is recorded separately
in [source-count-autostart.md](source-count-autostart.md); no CI experiment is
installed automatically.

Baseline run [40020715901](https://github.com/gogamid/cloudstream/actions/runs/40020715901):

- Run created 20:37:56 UTC, completed 20:56:20 UTC: **18m24s** wall time.
- Combined build/checks/signed release job: **14m57s**; actual Gradle **14m30s**.
- Emulator job: **3m21s**, started only after both variants had built.
- All 789 app/library tests, 22 Android checks, lint and signing passed.
- Repository Actions cache inventory was **zero entries**. Actual setup log:
  `cache-read-only: true`, then `Cache is read-only: will not save state for use
  in subsequent builds.` Every feature build therefore started cold.

## Changes

- Explicit `cache-read-only: false` in trusted fork push/manual jobs. Reuse Gradle
  dependencies, transformed libraries, compiled scripts and task outputs; retain
  successful-build cache cleanup. No PR/untrusted-fork workflow is enabled.
- Split permanently signed stable assembly into `signed-stable`, concurrent with
  `build` (debug APK/test APK, full app/library unit tests, shared JVM compile and
  lint). Emulator `compose-input-smoke` depends only on `build`, so release
  assembly no longer delays its start.
- **All three jobs must pass** before an APK is considered validated. Artifact
  availability alone is not acceptance. Stable certificate matching is unchanged;
  signing secrets/keystore are now needed only by the stable job.
- Gradle `--parallel --max-workers=2 --build-cache` in CI only. Bound worker count
  to avoid excessive parallel heap pressure; do not change Termux/local Gradle
  configuration, dependencies, application behavior or release optimization flags.
- Add Gradle timing profiles (`gradle-profile-debug`, `gradle-profile-stable`) for
  actual task measurements instead of guessing based on interleaved console lines.
- Already-compressed APK uploads use artifact `compression-level: 0`.
- Disposable emulator uses `-no-snapshot-save`; no later job reused its previously
  saved shutdown snapshot. Keep Android 12/KVM, 4096MiB, all input/IME/seek/source
  assertions, launcher-ANR safeguards and the exact `OK (22 tests)` gate.
- Preserve existing APK/test/lint report artifact names and permanent package/signing.

## Boundaries and tradeoffs

- Gradle build/configuration caching was already enabled locally; the missing part
  was persistence between Actions runs. Do not claim merely adding those Gradle
  properties fixes a cold hosted runner.
- Feature-branch cache entries are visible only within that branch (and default
  branch caches can be shared). New branches can still have cold builds. No master
  merge/cache seeding or cross-branch artifact substitution is performed.
- No project configuration-cache persistence/encryption secret is added. Gradle
  configuration includes signing credentials and time-based build metadata; do
  not upload that project state unencrypted or accidentally freeze build dates.
  `setup-gradle` does not persist project configuration-cache without its encryption
  input. Existing in-job configuration-cache behavior remains unchanged.
- Parallel release/checks jobs use more concurrent runner minutes and can duplicate
  some cold compilation. Caching consumes repository cache space and entries may
  be evicted. Wall-clock gains must be measured, not promised from the YAML alone.
- Warm task-cache test results are tied to Gradle's inputs. Real Android emulator
  checks still execute on every run. No test subset, lint or certificate check is
  removed to manufacture a speed improvement.

## Validation / measurement

Pending cold and warm full Actions runs. `actionlint` 1.7.12 validated workflow
structure/expressions locally; external shellcheck/pyflakes discovery was disabled
because Linux Go's executable lookup hits an Android-blocked syscall. No local
Gradle build was run. Baseline/full APK installation is unaffected.

References checked before implementation:
- https://github.com/gradle/actions/blob/v5/docs/setup-gradle.md
- https://github.com/gradle/actions/blob/v5/setup-gradle/action.yml
