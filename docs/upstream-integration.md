# Full upstream integration candidate

Branch: `feature/upstream-integration`, based on upstream `master` `4c4c679`.
Merged fork maintenance `c2c4841`, then refreshed upstream to `eb962b4`
(the newer +10 seek-button fix); no changes are pushed to upstream.
The TV remains on the validated maintenance build while real-device validation
of this integration candidate is pending; automated validation has passed.

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
real Compose editor/IME and dialog-window implementations, including the actual
state-based settings search field and shared value-based account/editor field.
It checks TV focus
without automatic keyboard opening, explicit OK, vertical escape/disposal,
unchanged phone keyboard activation, Apply key-up/repeat/cancel handling before
child focus, disabled Apply, exclusion of generic Login buttons, and retrying
an actual preference editor after validation rejects its first submission. Its host is
**debug-only**, not part of the signed stable release. No tests use the user's TV
or alter its data. Do not deploy a failing or unfinished integration build.

## Validation record and remaining device checks

Accepted code: `e2b6d9aa4f3e12d286604619aefffc7f14228956`.
[Run `39159294452`](https://github.com/gogamid/cloudstream/actions/runs/39159294452)
passed the complete gate:

- **152 app tests + 543 library tests = 695**, zero failures/errors/skips.
- **Six Android 12 emulator tests**, all passed, including the actual state-based
  settings search and shared value-based editors, keyboard reopening, Apply
  release/repeat/cancel/dismissal handling, disabled/generic-button exclusion,
  and the real preference editor's rejected-validation retry.
- Shared JVM compilation, Android compilation, lint, both APK builds, and
  verification of the permanent stable signing certificate.

The emulator gate first exposed a real repeated-Apply race during asynchronous
Compose dismissal. The immediate latch fix is validated by retaining that same
strict assertion. The retry test uses Compose's UI driver; the separate physical
focus/IME tests retain the actual Android frame clock.

Saved reports and APK: `~/downloads/cs-integration-39159294452/`.
Stable APK SHA256:
`b0e1320f54ddb285c42f1c167c8578564efa7f481c3f68a5c77150675f17a385`.

Require a completely green [latest branch workflow](https://github.com/gogamid/cloudstream/actions/workflows/fork-apk.yml?query=branch%3Afeature%2Fupstream-integration)
for the current code before deployment. No result on an earlier commit validates
subsequent code changes. Android emulator checks do not establish physical-TV
remote/IME behavior, live-provider availability, no-rebuffer playback on the TV,
codec/WebView stability or stress/memory behavior. Those remain real-device
checks; the user's TV has not been updated by this integration work.
