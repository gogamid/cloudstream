# Remote controls: grouped Apply setting and next video source

Branch: `feature/remote-next-source`, based on the completed upstream integration.

## Settings

- Settings → Player → Remote buttons now contains the Dialog Apply button entry,
  including its currently selected key or Disabled. Remove the duplicate top-level
  Player preference; keep the original `dialog_apply_remote_key` storage/backup key.
- Playback mappings still use `player_remote_button_mappings` and stable action
  names. Existing keys, defaults and the saved Apply choice are not migrated or reset.
- The existing playback-only reset keeps that scope. Its label and confirmation
  now explicitly say playback mappings, so moving Apply here does not silently
  change reset behavior.
- To bind **5**: Remote buttons → Add mapping → press/release **5** →
  **Next video source**. An existing 5 mapping can also be edited directly.
  No configured key or default shortcut is changed automatically.

## Next video source

- New saved action: `NEXT_SOURCE`. No chooser is opened.
- Uses the collected links and current profile's visible source order, wraps,
  skips hidden sources and deduplicates episode-local source identities. The
  current source remains an order anchor even if a profile now hides it.
- This is manual navigation, not automatic error failover. Previously attempted
  links can be revisited; automatic attempt/family-penalty history is not reset.
- Uses the normal manual source load path, preserving resume position and
  play/pause intent, remembering the explicit series source choice, and leaving
  source/subtitle extraction running. Manual startup has no five-second cutoff.
- With no alternative, toast and leave the current stream untouched; a later
  press can use newly arriving links.
- Existing key-repeat, dialog, typing, lock and view/session/lifecycle guards still
  apply. Source switching can normally buffer; this is not subtitle-only switching.

## Video-first subtitle startup

- `CS3IPlayer` now queues the initial external subtitle selection until this
  backend reports Media3 `STATE_READY`. Previously its immediate post-prepare
  call bypassed the generator's deferred auto-selection and fetched too early.
- No external DataSource creation, download or parse before that point. This
  means prepare/buffer the video stream first, not download the entire movie.
  Metadata/source discovery continues normally.
- READY rather than actual playback supports start-paused. Once prepared,
  independent subtitle changes stay immediate even during later rebuffering.
- Latest pending selection wins; Off/embedded cancel it. Release/replacement
  clears it and uses generation tokens to reject an old backend's READY event.
- Automatic subtitle failures remain logged and roll back without popup spam;
  explicit selection/retry failures still notify. Automatic callbacks cannot
  downgrade a pending manual request's notification. Invalid/unavailable subtitle
  URLs can still fail; this is not proof every provider's download works.

## Validation

Require the full regression/lint/signed APK gate and the Android emulator gate.
New coverage: nine manual-cycle policy cases, button-5 action serialization,
plus a native settings test for grouped Apply navigation, real button-5 mapping,
independent Apply disabling and preservation of its value on playback-only reset.
The six existing Compose/TV input/Apply regression tests remain required.
Nine new subtitle-startup policy tests cover ordering, selection/cancellation,
paused preparation, later changes, one-shot drain and stale backend readiness.

Run `39163944750` built successfully but its seventh emulator test selected the
unfocused activity root between key-capture and action dialogs. Restrict native
queries to dialog roots; retain the actual key-up capture and all assertions.

A real provider/TV source cycle is a separate check; pure tests do not prove
provider availability, backend seek precision or rebuffer timing. This feature
has not yet been deployed to the user's TV.
