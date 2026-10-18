# Full upstream integration candidate

Branch: `feature/upstream-integration`, based on upstream `master` `4c4c679`.
Merged fork maintenance `c2c4841`, then refreshed upstream to `eb962b4`
(the newer +10 seek-button fix); no changes are pushed to upstream.
The TV remains on the validated maintenance build while this branch is validated.

## Integration decisions

- Adopt upstream Compose settings/accounts, updater, dependencies, audio compressor,
  start-paused preference, extractor/intro/storage fixes, HLS URL resolution and
  other default-branch changes rather than restoring the old settings navigation.
- Keep provider-independent Discover/filmography/Library statuses and defaults,
  Continue Watching-only Home, TV grid navigation, backed-up remote/Apply mappings,
  progressive collection/selection, resume checkpoints, per-series preferences,
  playback-intent/startup safeguards, bounded TV resources and independent text
  subtitles. Upstream does not supersede these policies.
- Retire the fork additions to **inactive** legacy SettingsPlayer/SettingsUI and
  their XML preference screens; expose remote/Apply controls in Compose Player
  and filter defaults in Compose UI. Preserve the original preference keys.
- Keep upstream-equivalent extractor implementations where possible. Keep the
  tested BCP-47 regex whitespace fix, optional SAF-grant helper and intro conversion
  helper; do not restore token logging in Hexload.
- Start-paused applies at a fresh episode boundary, not every backend reload.
  Source replacement preserves intentional pause or a subsequent manual Play.
  A first manual chooser selection marked `sameEpisode` is still an initial load,
  not a paused replacement; it autoplays unless start-paused is enabled. Actual
  backend playback still governs the automatic-source startup timer.
- Compose editors use TV-only read-only focus to avoid implicit IME sessions;
  OK enables editing, horizontal arrows retain caret handling and vertical arrows
  move focus. Phone keyboard options remain unchanged. Focus ownership is cleared
  on disposal and participates in playback shortcut suppression.
- Compose Apply support explicitly opts real settings/account Apply actions in.
  Login/Add/Delete/Play and generic dialog confirmations are not inferred. The
  Android hook binds the dialog window from inside its content, works without an
  initially focused child, uses the existing saved key/key-up policy, observes
  current actions/enabled state and restores the previous callback on disposal.
  Closing actions latch immediately to prevent another gesture during Compose's
  asynchronous dismissal; text editors retain their own busy/closed guards so a
  rejected validation can retry without allowing duplicate successful saves.
- Both updater implementations target `gogamid/cloudstream`, never upstream APKs.
  This does not publish releases; missing fork releases remain an update-check
  error rather than falling back to upstream. CI still verifies the permanent
  stable signing certificate. Extension repositories are not retargeted.
- Retain upstream compressor functionality, but reuse drained PCM storage, reset
  unsupported configurations, and saturate positive 16-bit peaks without wrapping
  their sign. Include its native Apply button in the opt-in shortcut and dismiss
  its window during player view teardown, like the other player dialogs. No
  measured audio-quality or TV CPU improvement is claimed.
- Positive subtitle offsets mean earlier cues: embedded timestamps subtract the
  offset, independent subtitle lookup adds it, and upstream sync UI uses that same
  convention. Existing clock tests cover both directions. No preference key is
  changed; a version marker converts legacy video-instance bundle offsets once
  on task restoration so their physical sync is retained. Compose subtitle-style changes refresh text through the backend
  interface instead of unconditionally seeking video. Embedded refresh can still
  use the existing tiny ordinary seek; external refresh never does.

## Validation gate

Run the entire app unit suite (including the existing 130 fork regressions and
upstream subtitle-language tests), fourteen new boundary tests, and the full library
JVM suite including upstream's loopback HTTP HLS-download fixtures. Reuse those
HLS fixtures instead of retaining redundant app-side copies. Compile shared JVM
code as well as Android code. Lint, debug APK, stable release APK and permanent
certificate verification remain required. Extractor fixtures intercept HTTP;
HLS fixtures use localhost, not live hosts.

A separate Android 12 emulator gate runs six instrumentation tests against the
real Compose editor/IME and dialog-window implementations. It checks TV focus
without automatic keyboard opening, explicit OK, vertical escape/disposal,
unchanged phone keyboard activation, Apply key-up/repeat/cancel handling before
child focus, disabled Apply, exclusion of generic Login buttons, and retrying
an actual preference editor after validation rejects its first submission. Its host is
**debug-only**, not part of the signed stable release. No tests use the user's TV
or alter its data. Do not deploy a failing or unfinished integration build.

## Validation record and remaining device checks

Run `39154735954` passed **695 app/library tests**, zero failures/errors/skips,
shared JVM compilation, lint, both APK builds and permanent signing verification.
Its emulator tests passed TV focus/IME/reopen/vertical escape, phone IME, disabled
Apply and generic Login exclusion. The fifth check exposed a real repeated-Apply
race during asynchronous dismissal; the immediate latch fix and editor retry
check are included in the next run, not excused by weakening that assertion.

Require a completely green [latest branch workflow](https://github.com/gogamid/cloudstream/actions/workflows/fork-apk.yml?query=branch%3Afeature%2Fupstream-integration)
for the current code before deployment. No result on an earlier commit validates
subsequent code changes. Android emulator checks do not establish physical-TV
remote/IME behavior, live-provider availability, no-rebuffer playback on the TV,
codec/WebView stability or stress/memory behavior. Those remain real-device
checks; the user's TV has not been updated by this integration work.
