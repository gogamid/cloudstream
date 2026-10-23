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

Pending the fork's Actions gates. Added/updated checks:

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

No new APK has been installed. Automated local-audio/provider-fixture checks are
not confirmation of live TV codec/host stability, rewind stress or memory behavior.
