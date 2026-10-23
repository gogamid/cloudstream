# Fork maintenance requirements

- Existing settings are a behavior contract. Read the active settings UI and its
  preference definitions before changing playback/input/loading behavior. Reuse
  those keys and values; do not introduce hardcoded shortcuts that bypass them,
  silently rewrite settings, or break backup/restore compatibility.
- Trace every affected entry point: TV D-pad, mapped/media keys, on-screen/native
  controls, MediaSession, phone gestures, reload and lifecycle paths. Respect
  independent TV shown/hidden seek amounts and the phone double-tap seek amount.
- Regression-test non-default values (especially 5s/10s seek), not just defaults.
  A batching/safety mechanism may change when work runs, not the user's amount,
  source profile, subtitle choice, start-paused setting or playback intent.
- Initial source collection waits for completion, manual Skip, or the saved
  `source_auto_start_count` threshold (default 50 usable distinct sources; zero
  waits for all). Count only allowed sources using the same identities as ranking.
  Source/quality priority must not imply Skip. Manual/count-based early playback
  must not cancel extraction or fabricate completion. Collect
  source descriptors, not buffers/downloads for every video. Respect hidden-source
  and per-series preferences, and keep generation/cancellation guards.
- Automatic source startup uses the saved `source_startup_wait_seconds` budget
  (default 5, zero disables). Give each new attempt its own budget; exclude pause,
  dialog, background and interactive-seek time. Never apply this startup deadline
  after actual playback starts or to manual selections. Explicit playback errors
  can still fall back immediately. Do not confuse this with source collection.
- Build/test through the fork's GitHub Actions, not local Termux Gradle. Push only
  to `gogamid/cloudstream`; no upstream pushes or master merges without permission.
- Keep trusted fork Gradle caches writable across Actions runs. The optimized
  workflow is validated only when `build`, `signed-stable` and `compose-input-smoke`
  all pass. Never treat an uploaded APK alone as a green build or skip regression,
  lint, emulator or permanent-certificate checks for speed. New branches may still
  build cold; cache/task timing claims require measured runs.
- Install only validated permanently signed APKs after authorization. Update in
  place, preserving app data and mappings; never uninstall/clear data to resolve
  a signing conflict without explicit permission. Distinguish automated checks
  and startup verification from actual live-provider/TV stress testing.
- Follow `AI-POLICY.md` for any pull requests/issues.
