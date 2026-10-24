# Optional playback diagnostics

Based on the latest local `feature/source-count-autostart` checkout, matching
`fork/feature/source-count-autostart` at the start of implementation. The TV's
installed APK hash matches the validated build recorded in `source-count-autostart.md`.

Settings → Player → Playback diagnostics is off by default. It takes effect during
existing playback without reloading or changing buffers, source selection, seek
amounts, subtitles, playback speed or play/pause intent.

ADB can explicitly set the same saved preference on a normal or already-running
installation (this is not an Android debuggable APK and requires no data clearing):

```sh
adb shell am start -n com.lagradost.cloudstream3/.MainActivity --ez playback_diagnostics true
adb logcat -v threadtime -s CSPlaybackDebug:I '*:S'
# Disable via Settings or:
adb shell am start -n com.lagradost.cloudstream3/.MainActivity --ez playback_diagnostics false
```

The ADB option persists until disabled, just like the switch. It is consumed so
activity recreation cannot reapply an old launch value over a later UI change.

## Output and interpretation

- A sample every five seconds while the player callbacks are attached, plus state
  changes, position discontinuities, dropped frames and errors.
- Position, buffered duration, loading/playing/intent, speed, video MIME,
  dimensions, frame rate, declared bitrate and Java heap allocation.
- Upstream bytes and active transfers for this backend only, excluding disk-cache
  hits and unrelated TV/WebView traffic. Network rate is the byte delta since the
  previous snapshot divided by its elapsed time, not a connection speed test.
  First rate is -1 (no previous sample); short event intervals may be bursty.
- Media3 bandwidth estimates are separately labelled and may be stale. Format
  bitrate can be unknown (-1), so neither alone proves a bandwidth bottleneck.
- Decoder names and initialization time; load error class, HTTP response code
  where directly available, task/type/track, bytes, elapsed time and cancellation.
- No new logging of URLs, headers, cookies, exception messages or subtitle text.
  Existing app/provider logs are unchanged: share the filtered tag, not unrestricted
  logcat. No persistent diagnostic files or external telemetry are added.

Buffer near zero with active transfers and little byte growth suggests delivery
starvation. A substantial buffer with frame drops suggests local rendering/decode
pressure. No active transfer can mean a load-control decision, a retry gap, cache
reading or an ended request; correlate multiple samples instead of treating it as
proof. Java heap does not include all native/graphics/system memory.

WebView CPU attribution still requires ADB `top` alongside these logs; these
counters intentionally do not pretend that resolver traffic is video traffic.

## Validation

The existing Actions gates remain required: build/regression tests/lint,
permanently signed stable APK/certificate, and emulator checks. Two additional
Android checks cover the real settings switch across recreation and toggling
filtered diagnostics during real local Media3 playback without reloading or
changing pause/seek behavior. The required emulator total becomes 24.

These checks do not establish throughput accuracy on every network stack or
resolve the reported physical-TV 4K stall. A validated APK must be installed with
authorization, then the problematic source and seek repeated with logging enabled.
