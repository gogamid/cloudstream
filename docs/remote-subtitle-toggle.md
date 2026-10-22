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
  restores text without requesting a video reload, seek or pause; cached external
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

## Validated build — installed on TV

Run [39401600263](https://github.com/gogamid/cloudstream/actions/runs/39401600263)
passed for code `1b3ae9a1162afb53d83aeda60b2ddb87e5a59485`:

- 728 app/library tests (185 + 543), no failures/errors/skips.
- All seven Android UI tests, including actual capture/mapping of the toggle key
  and preservation of the next-source binding.
- Lint, Android/JVM compilation, both APK builds and permanent stable signing check.
- Stable APK SHA256:
  `8dd8c70c2fe5ed6bc546cd087499f40912d68e99a5ae0a88fa40dd96af7b91ae`.
- APK/reports: `~/downloads/cs-subtitle-toggle-39401600263/`.

After the user repeated “install”, the TV reconnected at `192.168.0.139:5555`.
Installed this validated APK with `adb install --no-streaming -r`: **Success**.
No uninstall or data clearing; existing settings/bindings retained. Device base.apk
SHA256 exactly matches the above artifact. Launched the stable package and observed
PID `10472` after 12 seconds; crash buffer was empty. This deployed build includes
next-source cycling, grouped Apply, video-first external subtitles and the toggle.

Real-provider playback behavior is not yet confirmed; selection-policy tests and
mapping UI checks do not establish every provider, codec or subtitle format.
The prior validated video-first/next-source build is in `remote-next-source.md`.
