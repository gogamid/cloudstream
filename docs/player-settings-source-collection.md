# Respect player settings and wait for complete source collection

## User request and diagnosis

The user reported relative seeking always using 30 seconds instead of the saved
5s/10s amount, requested all sources be collected unless Skip is pressed, and
required future adaptations to preserve settings-driven behavior.

- `CS3IPlayer.handleEvent(SeekBack/SeekForward)` used a fixed 30 seconds, including
  mapped actions. TV D-pad separately cached shown/hidden settings; gesture/button
  paths separately read the phone setting. Native Media3 controls had another default.
- `SourcePlaybackPlan.ready` permitted automatic startup after 750ms/2.5s or a
  priority threshold, even without Skip. Extraction normally continued in the
  background, but playback and the initial source choice were premature relative
  to the user's requested full-collection behavior.

## Settings compatibility

`PlayerSeekSettings` reads the same typed `AppSettings.player` definitions used by
active Player settings. `SeekDurationPolicy` selects:

- TV/emulator controls shown: `android_tv_interface_on_seek_key`.
- TV/emulator controls hidden: `android_tv_interface_off_seek_key`.
- Phone controls/gestures: `double_tap_seek_time_key2`.

All retain their original keys/default 10 seconds and backup behavior. Positive
legacy values outside the current slider range remain respected; only nonpositive
invalid values fall back safely, without writing preferences.

D-pad/mapped events, on-screen buttons, gesture animations/labels and native
MediaSession/player controls use this live resolution. Shown/hidden values remain
independent. Native controls use `SettingsSeekPlayer`, which reports the effective
increment and routes relative commands through the existing TV burst controller.
The underlying Media3 builder also receives the configured increment, not another
library default. Source-generation guards invalidate old native seek callbacks.
The control adapter also preserves access to ExoPlayer's live audio session ID so
phone volume boost is not broken by removing a direct ExoPlayer cast. The native
control integration check asserts that the session ID is retained.

The 350ms TV batching window changes **when** one accumulated seek occurs, not
the setting's amount per press, pause intent, absolute chapter/scrub targets or
tiny subtitle refresh seek. No remote mappings are assigned/rewritten.

## Source collection

- Initial playback waits for truthful extraction completion (success or failure
  with usable results). Priorities and saved choices no longer imply Skip.
- Explicit Skip starts a usable source only for generators supporting it. It does
  not cancel collection or fabricate a completed loading state. Late sources and
  subtitles remain available. Skip resets with the episode/session.
- Preserve all ranking/failover policies: saved series source, last-working hint,
  quality/source priorities, hidden links, deduplication and attempted/family history.
- Remove obsolete 250ms startup polling and timed early-start thresholds. LiveData
  source/loading observers drive readiness instead.
- Collect descriptors/URLs supported by the provider, not simultaneous downloads
  or playback buffers for every video. Plugins may have their own limits/timeouts;
  this does not guarantee every remote host succeeds.
- Active quality-profile help now explains full collection/explicit Skip (English
  and Russian), without stale translated automatic-skip promises.

## Durable maintenance requirement

Root `AGENTS.md` records settings/UI/backup compatibility, all affected input paths,
non-default regression cases, explicit Skip semantics, safe builds and deployments
as standing instructions for future work.

## Validation / deployment

Run [40004040461](https://github.com/gogamid/cloudstream/actions/runs/40004040461)
passed for `200cdba5ef60ca9015509ad4ea2781883ba59d15`: **761 app/library tests
(218 + 543), no failures/errors/skips, all 16 Android checks**, lint, JVM/Android
compilation, both APKs and permanent signing verification. Added/updated checks:

- Nine pure seek-setting/batching tests, including independent shown/hidden/phone
  values, every slider value, positive legacy values, invalid values and 5s/10s bursts.
- Fourteen source-policy tests, including full-collection readiness, high priorities,
  saved preferences, explicit/non-skippable/empty/hidden/reset cases and ranking.
- Four new real Media3 integration checks: stored 5s remote rewind, 10s shown value,
  native controls using the independent phone amount without bypassing batching,
  and stale native controls unable to mutate a replacement backend.
- Two delayed-generator ViewModel/policy integration checks: no-Skip waits for all
  three links; Skip does not stop collection or discard late links.
- Retain all prior unit/library tests, lint, JVM/Android compilation, signed APK
  builds, and the previous ten Android checks. Instrumentation must report all
  **16 tests**, not a weaker subset.

The first run `39999902568` passed 761 tests/build/lint/signing and nine Android
checks, but seven Media3 checks failed because the expanded test listener cast the
normal null backend-detach event to a non-null Player. Restored the expected
null-event guard without removing assertions; the complete rerun above passed.
The final code also includes the volume-boost adapter compatibility correction.

Stable APK SHA256:
`ecc358bee61a8f45d4e6322576ccccb6ed013c2bebbe3fb0bd57f170a3a1fab2`.
Artifact/reports: `~/downloads/cs-settings-sources-40004040461/`.
A checksum-verified phone copy is saved at
`/storage/emulated/0/Download/Cloudstream-Stable-SettingsFix-200cdba.apk`, alongside
the earlier APK (not silently replacing its file).

## TV deployment verified

After the user's explicit “install on tv” request, connected to
`192.168.0.139:5555` and updated the stable package using
`adb install --no-streaming -r`: **Success**. No uninstall, data clearing,
preference changes or remote-binding assignments were performed.

The installed base.apk SHA256 matches the validated artifact above. Launched
`com.lagradost.cloudstream3`; PID `599` remained running after 12 seconds and the
crash buffer was empty. The prior TV seek-fix has been superseded by this build.
Phone installation remains unconfirmed; the verified Downloads copy remains available.

Automated local-audio/provider-fixture checks and TV startup verification are not
confirmation of live codec/host stability, rewind stress or memory behavior.
