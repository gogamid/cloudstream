# Full upstream integration (work in progress)

Branch: `feature/upstream-integration`, based on upstream `master` `4c4c679`.
Merged fork maintenance `c2c4841`; no changes are pushed to upstream.
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
  Actual backend playback still governs the automatic-source startup timer.
- Compose editors use TV-only read-only focus to avoid implicit IME sessions;
  OK enables editing, horizontal arrows retain caret handling and vertical arrows
  move focus. Phone keyboard options remain unchanged. Focus ownership is cleared
  on disposal and participates in playback shortcut suppression.
- Compose Apply support explicitly opts real settings/account Apply actions in.
  Login/Add/Delete/Play and generic dialog confirmations are not inferred. The
  Android hook binds the dialog window from inside its content, works without an
  initially focused child, uses the existing saved key/key-up policy, observes
  current actions/enabled state and restores the previous callback on disposal.
- Both updater implementations target `gogamid/cloudstream`, never upstream APKs.
  This does not publish releases; missing fork releases remain an update-check
  error rather than falling back to upstream. CI still verifies the permanent
  stable signing certificate. Extension repositories are not retargeted.
- Retain upstream compressor functionality, but reuse drained PCM storage, reset
  unsupported configurations, and saturate positive 16-bit peaks without wrapping
  their sign. No measured audio-quality or TV CPU improvement is claimed.
- Positive subtitle offsets mean earlier cues: embedded timestamps subtract the
  offset, independent subtitle lookup adds it, and upstream sync UI uses that same
  convention. Existing clock tests cover both directions; no key/sign migration
  is needed. Compose subtitle-style changes refresh text through the backend
  interface instead of unconditionally seeking video. Embedded refresh can still
  use the existing tiny ordinary seek; external refresh never does.

## Validation gate

Run the entire app unit suite (including the existing 130 fork regressions and
upstream subtitle-language tests), ten new boundary tests, and the full library
JVM suite including upstream's loopback HTTP HLS-download fixtures. Reuse those
HLS fixtures instead of retaining redundant app-side copies. Compile shared JVM
code as well as Android code. Lint, debug APK, stable release APK and permanent
certificate verification remain required. Extractor fixtures intercept HTTP;
HLS fixtures use localhost, not live hosts.

A separate Android 12 emulator gate runs five instrumentation tests against the
real Compose editor/IME and dialog-window implementations. It checks TV focus
without automatic keyboard opening, explicit OK, vertical escape/disposal,
unchanged phone keyboard activation, Apply key-up/repeat/cancel handling before
child focus, disabled Apply and exclusion of generic Login buttons. Its host is
**debug-only**, not part of the signed stable release. No tests use the user's TV
or alter its data. Do not deploy a failing or unfinished integration build.

Pending: CI results and a real UI/device pass. Unit/fixture tests and a successful
signed build alone do **not** establish TV D-pad/IME behavior, no-rebuffer subtitle
switching, live providers, codecs/WebView stability, or stress/memory behavior.
