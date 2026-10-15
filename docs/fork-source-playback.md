# Progressive source playback (fork)

## Goals

Start without waiting for every provider mirror, honor a series/profile's explicit
source choice, avoid cycling through dead variants of the same host, and never
replace manual intent with an automatic fallback. Provider extraction is opaque:
the app can rank links as they arrive but cannot force a plugin's internal host
request order or guarantee a server's startup time.

## Collection and initial selection

`PlayerGeneratorViewModel` keeps collection status truthful. Starting playback or
pressing Skip does not mark extraction complete or cancel it. Late links and
subtitles continue to arrive. Callbacks are bound to a generation/episode instance;
canceled older callbacks cannot populate the current episode.

For generators supporting early playback:

- Without a manual preference, allow 750ms after the first usable source for a
  small batch to arrive, then start the best candidate. Explicit profile priority
  >= 10 can still start immediately.
- With a saved series choice, start a strong match immediately. Otherwise give it
  up to 2.5s after the first usable link to arrive, then use the best fallback.
  New arrivals do not restart that deadline.
- Skip bypasses the grace period, not extraction. It cannot select a preferred
  source that has not arrived yet.
- Completed extraction starts immediately; an empty completed result shows the
  existing no-links error. Non-skippable generators retain completion semantics.
- A source arriving after playback starts does not interrupt an existing attempt.

## Ranking and identity

`SourcePlaybackPlan` ranks permitted, untried links by:

1. Fewest failures for that host/source family in this episode.
2. Match to the explicit manual series choice.
3. Match to a recently working source saved separately from the manual choice.
4. Existing quality/source profile priority (stable order for ties).

Matching considers provider/source, normalized source name, quality, format, and
optionally a hostname. Recognized episode and resolution labels are normalized;
arbitrary host/server numbers and other name content are not stripped. Older
preferences without a hostname remain readable. Weak source-only matches are
fallbacks, not a reason to bypass the preferred-source grace period.

Only source descriptors/hostnames are persisted, never episode URLs, query tokens
or request headers. Actual attempt URLs are used solely in memory to avoid retrying
the same episode link when objects/names/order change.

## Attempts and fallback

Automatic attempts retain the 5s foreground startup budget, paused while a dialog
is open or the app is backgrounded. Actual backend playback, not autoplay intent,
ends the startup deadline. Buffering after playback has started has no short
cutoff. Manual source selections have no short startup timeout and use the normal
backend error path.

On timeout or a definitive backend error, a failed source family is demoted for
this episode and the best remaining link is selected from the entire collection,
not only links below the current list position. New arrivals join the candidate
pool. If none remain while extraction is still running, wait for later sources
instead of closing the player. Once extraction finishes, exhausted links produce
the existing no-links error.

The original resume target is carried across failed startup attempts. Temporary
zero positions from an unstarted player must not replace the stored resume point.
Five seconds of actual playing records a working-source hint; this never changes
the explicit preference. Manual re-selection, including the currently selected
source, starts an unrestricted manual attempt.

## Episode preloading

Next-episode preloading has a separate job from current-episode extraction. It
captures its target episode before launching and avoids repeatedly preloading a
successfully completed target. Canceled/partial loads cannot mark the shared cache
as fully collected. Navigation uses a new selection plan per episode.

## Verification

Pure policy/serialization tests cover delayed preferred-source arrival, bounded
waiting, Skip, completion, disallowed sources, early-play restrictions, list
reordering, late candidates, failed-family demotion, URL identity, episode resets,
legacy saved descriptors, matching, and separation of manual/working preferences.
Existing timeout tests cover automatic/manual attempts and post-start buffering.

Real-device checks are still required with actual providers: delayed saved host,
manual selection across episodes/restarts, a dead first mirror, partial preload,
late subtitles, resume fallback, and subtitle sync. Aggregate `Source pipeline:`
logs record first usable arrival and initial selection timing without including
source URLs or credentials. Passing unit tests is not proof of seamless playback.
