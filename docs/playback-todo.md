# Playback fixes

Handle these separately, in this order. Do not mix subtitle architecture changes
into the autoplay regression fix.

## 1. First-source autoplay — in progress

- [x] Separate playback intent from actual playing/buffering status.
- [x] Ensure teardown/status callbacks cannot overwrite a new load's autoplay request.
- [x] Ignore held-OK/play-toggle repeats carried into a newly opened player.
- [ ] Regression tests: initial load, with/without Skip, source replacement,
      subtitle reload, and intentionally paused playback.
- [ ] GitHub Actions tests, lint, signed stable build.
- [ ] In-place TV update and user confirmation of initial autoplay.

Reported behavior: the first selected source prepares but remains paused until
Play is pressed again, whether source collection was skipped or completed.

## 2. Seamless external-subtitle switching — queued

- [ ] Replace the full-video reload path for English/Russian shortcuts and
      cycling same-language external subtitle sources.
- [ ] Keep video playback, decoder, position, and play/pause state uninterrupted.
- [ ] Retain full subtitle choice list without preparing every track at once.
- [ ] Preserve subtitle timing, styling, subtitles-off, series preferences,
      embedded subtitle selection, and missing/error handling.
- [ ] Regression tests, GitHub Actions build, then TV verification.

Reported behavior: choosing a different external subtitle rebuilds/rebuffers the
playing video. This is a regression/trade-off from selected-track-only attachment
and is not acceptable for the language shortcuts.
