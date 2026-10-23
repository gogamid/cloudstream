# Configurable source startup wait

## Behavior

The user requested a configurable wait before moving from one video source to the
next. Active **Settings → Player → Source startup wait (seconds)** now provides a
0–120 second slider on TV and phone, with English/Russian title and explanation.

- Existing behavior remains the default: **5 seconds** of eligible startup time.
- **0 disables the no-playback startup deadline**. This does not suppress actual
  network/decoder errors, which can still fall back immediately.
- Each automatic source attempt receives a fresh budget from the saved setting.
  An in-flight attempt keeps its initial snapshot; the next reads any changed value.
- Only foreground time with play intent counts. Intentional pause, open dialogs,
  background time and interactive seek batching do not count.
- Once actual playback has started, the startup deadline cannot interrupt later
  playback/rebuffering. Manual source selection has no startup deadline.
- Waiting for complete source extraction unless Skip is pressed is unchanged.
  This setting limits an attempted video's startup, not provider collection time.
- The independent five-second successful-playback observation used to learn a
  last-working source stays unchanged; it is not another fallback timeout.

## Settings and safety

`PlayerPreferences.sourceStartupWaitSeconds` stores the global integer setting
under `source_startup_wait_seconds`. The active Compose slider and production
`sourceStartupWaitMs(context)` adapter share that definition. It is transferable
with the existing settings backup/restore mechanism; no older key is migrated,
user value rewritten or remote mapping assigned.

`SourceStartupTimeout` accepts the configured budget instead of hardcoding five
seconds. Negative invalid settings safely use the five-second default. Positive
restored values outside the current slider range are respected, not silently
clamped. Seconds convert using Long arithmetic and elapsed time saturates at the
budget, preventing overflow from a pathological clock delta. No new polling loop,
network request, video buffer or per-frame preference read is added.

## Validation / deployment

Run [40009053808](https://github.com/gogamid/cloudstream/actions/runs/40009053808)
passed for `c0807831b1da09f63753dd5e3d178058737a149b`:
**773 app/library tests (230 + 543), no failures/errors/skips; all 18 Android checks**,
lint, JVM/Android compilation, APK builds and permanent certificate verification.
Coverage includes:

- The original five startup-policy tests plus twelve configuration regressions:
  10-second boundary, all 1–120 values, independent attempts, disabled timeout,
  manual/started/paused exclusions, invalid/large restored values, negative/huge
  clock deltas and backup transferability.
- Two new Android checks: the actual active Player slider persists 15 seconds
  through Activity recreation and can disable the timeout; the same production
  adapter supplies independent saved budgets, updates and manual exemption.
- Full app/library tests, JVM/Android compilation, lint, debug/stable APKs,
  permanent certificate verification, and all **18 Android checks** (previous 16
  retained).

Stable APK SHA256:
`2e24cf9a5c2c7058097594432ac3fee22583df32d75831d874c391bd888b91bf`.
Artifact/reports: `~/downloads/cs-source-wait-40009053808/`.
Checksum-verified phone copy:
`/storage/emulated/0/Download/Cloudstream-Stable-SourceWait-c080783.apk`.

No new APK has been installed on either device or any user setting changed. The
TV remains on the previous validated settings/source-collection build from run
`40004040461`. Live-provider/TV timing still needs observation; fixture tests do
not establish host availability or long-duration codec stability.
