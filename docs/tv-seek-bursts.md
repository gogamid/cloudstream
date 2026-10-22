# TV seek-burst safety

Branch: `fix/tv-seek-bursts`, based on the installed remote-controls/subtitle build.

## Report and evidence

User reports the TV app exits after many repeated rewind presses and suggests
pausing while choosing the new position. Read-only diagnostics after the earlier
update found an empty crash buffer and historical main-process LOW_MEMORY exits,
including foreground importance 100 at ~199 MB PSS and a later ~250 MB PSS exit.
These records do **not** establish that rewind caused those particular exits.
Current startup sample was ~207 MiB PSS, with one WebView; no long-duration or
playback/seek memory improvement is claimed.

Local diagnostics: `$TMPDIR/cs-seek-{exit-info,crash,memory-before}.txt`.
The prior successfully deployed APK remains run `39401600263` until this fix is
validated and explicitly deployed; no crash has been deliberately reproduced on
live content or app data cleared.

## Implementation

- Coalesce TV UI relative seeks (D-pad, rewind/fast-forward and mapped seek actions).
  Phone behavior, absolute seek-bar/chapter/restart choices and tiny subtitle-sync
  refresh seeks are not put through this delayed interaction.
- On the first press, pause the existing backend temporarily. Accumulate offsets
  against one pending target, not the unchanged backend position. Show the existing
  text-only seek overlay; never allocate seek-preview bitmaps or another player.
- After 350 ms without another press, seek once, clear the preview and restore the
  current logical play/pause intent. OK can commit immediately. Explicit Play,
  Pause/toggle or lifecycle pause also commits, honoring the updated intent.
- An initially paused video stays paused. The internal pause must not overwrite
  user intent or cause an automatic source-startup timeout; real audio-focus/lifecycle
  interruptions still win. Transport UI reflects logical intent during preview.
- Bound targets to valid times, saturate large deltas, avoid exact-end auto-next
  and skip a redundant backend seek if the final position did not change.
- New source/release cancels old work without resuming a replacement. Absolute
  seeks supersede the pending relative target, including seeks made directly by
  Media3's native time bar/media session. Generation-checked delayed tasks cannot
  commit a newer interaction.
- Preview events are separate from PositionEvent: no fake resume checkpoints,
  chapter invocation, episode preloading or provider work while picking a target.
- A pending real seek can flush embedded text at commit; avoid a second tiny native
  subtitle reset mid-preview. External text remains independent as before.

## Validation gate

- Nineteen deterministic controller/intent/scheduler tests: long bursts, one seek,
  logical pause/play/focus changes, bounds/overflow/unset/changing duration,
  no-op/reverse input, release/replacement/stale tasks and exception cleanup.
- Three real Android Media3 tests with a small generated silent local WAV fixture:
  one seek after 100 rewind requests while paused, real temporary-pause intent
  preservation with explicit Pause, and cancellation across backend replacement.
- Keep the previous seven Compose/input/Apply/remote-settings checks. The emulator
  gate now requires all ten tests, alongside full app/library tests, lint, APKs,
  Android/JVM compilation and permanent stable signing.

Audio-only fixture checks establish real Media3/main-loop integration, **not** TV
video-decoder stability, WebView pressure or elimination of system low-memory kills.
Targeted physical-TV testing remains necessary before calling the reported exits
resolved. A pause/batch mitigation is not proof of the original crash's cause.
