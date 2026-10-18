# Playback fixes

Handle these separately, in this order. Do not mix subtitle architecture changes
into the autoplay regression fix.

## 1. First-source autoplay — installed, awaiting TV confirmation

- [x] Separate playback intent from actual playing/buffering status.
- [x] Ensure teardown/status callbacks cannot overwrite a new load's autoplay request.
- [x] Ignore held-OK/play-toggle repeats carried into a newly opened player.
- [x] Eight pure playback-intent regression tests: initial autoplay, buffering,
      stale teardown, source replacement, reload, and intentional pause.
- [x] GitHub Actions tests, lint, signed stable build (run `39127746072`, code `cae08cc`).
- [x] In-place TV update and launch.
- [ ] User confirmation: first playback starts automatically both with and without Skip.

Reported behavior: the first selected source prepares but remains paused until
Play is pressed again, whether source collection was skipped or completed.

## 2. Seamless external-subtitle switching — installed, awaiting TV confirmation

- [x] Implement a video-clock-driven external-subtitle controller, independent
      of the video media sources/decoder. Language shortcuts and cycling select it.
- [x] Remove video reloads for external subtitle selection/file loading; the
      source/subtitle chooser no longer pauses playback just to change text.
- [x] Keep all descriptors available, with a two-track/4 MiB raw-byte LRU cache.
- [x] Cancel stale reads/results on rapid changes and retain the previous working
      subtitle on load failure without routing it through video-source fallback.
- [x] Preserve timing/styling, subtitles-off, series preferences and native
      embedded selection; external sync offsets no longer seek the video.
- [x] Fifteen new pure selection/cache/read/clock tests passed, along with the
      existing language-cycling and autoplay tests, lint and signed stable build.
- [x] GitHub Actions run `39132259363`, tested code `3931594`.
- [x] In-place TV update and launch.
- [ ] User confirmation: English/Russian switching and repeated same-language
      cycling do not pause, restart or rebuffer video.
- [ ] TV checks for subtitle sync, embedded tracks, file subtitles and load failure.

Reported behavior: choosing a different external subtitle rebuilds/rebuffers the
playing video. This is a regression/trade-off from selected-track-only attachment
and is not acceptable for the language shortcuts.
