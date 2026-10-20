# Remote action: Toggle subtitles on/off

Adds `TOGGLE_SUBTITLES` to Settings → Player → Remote buttons → Add mapping.
Pick any mappable key; existing bindings/defaults (including button 5) stay intact.

- An active or pending subtitle selection switches Off immediately.
- Switching On restores the last selected track, using a fresh available descriptor
  by ID or stable identity. Otherwise prefer the saved/settings choice, then the
  first displayed available track. Never reuse a missing episode URL or search
  subtitle providers. With no available track, toast without changing preferences.
- Retain one episode-local descriptor, clear it for a new source-collection session,
  and keep it through a same-episode video-source change. Filter accumulated native
  descriptors against the current backend's text-track IDs before restoring one.
- Save explicit Off separately from the previous per-series descriptor, preserving
  the last track for a later On and retaining existing backup/storage keys.
- An explicit Off also saves the global no-subtitle language preference even before
  the first auto-selection arrives; late tracks must not silently re-enable it.
- Use the normal explicit subtitle-selection path. CS3 independently disables or
  restores text without video reload, seek, pause or rebuffer; cached external
  subtitles can be reused. Pending reads are canceled on Off, and a restored track
  before video preparation still obeys the video-first startup gate.
- Embedded/URL/downloaded-file tracks, manual error notification, key-repeat,
  dialog/typing, lifecycle/session and control-lock guards remain supported.

## Validation

Require the full Actions gate before deployment. Add thirteen selection/preference
policy tests and one action-name serialization test. Extend the seven-test Android
UI gate to physically capture button 6 and bind the new action while retaining
button 5's next-source mapping. Button 6 is only a test assignment, not a default.

The docs-only predecessor run `39397625179` passed builds but all seven UI tests
were obstructed by a **Nexus Launcher ANR** system window (logcat identifies input
injection being denied to that other application's overlay). Increase the disposable
emulator's RAM to 4 GiB, wake/unlock it and stop the unused launcher before tests;
retain app ANR reporting, all assertions, and collect window diagnostics as well.
This is emulator setup, not a modification to the user's TV.

Real-provider playback checks and TV deployment are not yet confirmed. The prior
validated video-first/next-source build is recorded in `remote-next-source.md`;
its green result does not cover this new action.
