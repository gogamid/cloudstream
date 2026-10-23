# Progressive source playback (fork)

> Historical implementation/validation record. The later user-requested
> [settings and source-collection correction](player-settings-source-collection.md)
> supersedes the automatic 750ms/2.5s/priority-based early-start rules below:
> and the subsequent [source-count setting](source-count-autostart.md): wait for
> collection completion, manual Skip, or the saved count (default 50 usable sources;
> zero waits for all). Ranking, truthful collection, failover and preferences remain.

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
preferences without a hostname remain readable. Weak provider-only or
provider/quality/format matches are fallbacks, not a reason to bypass the
preferred-source grace period.

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

## TV memory and interaction hardening

The Smart TV Pro exit history reported foreground LOW_MEMORY terminations with
248–333 MB application PSS, without a retained Java exception trace. Its reported
RAM is about 1.7 GiB and it runs a 32-bit Android userspace. This confirms memory
pressure for those exits, not the cause of every reported interruption.

- TV uses a 32 MiB default media sample-buffer target and no 30-second rewind
  back-buffer. Explicit RAM-buffer preferences remain honored; this is not a cap
  on total process memory and does not restrict video resolution.
- External subtitles now have an independent controller (see below). They are
  not attached to the video media pipeline. All descriptors remain selectable;
  embedded tracks are selected directly in the native player.
- Subtitle-arrival bursts are coalesced, and automatic subtitle reloads wait for
  a ready player outside dialogs. Automatic reloads preserve play/pause intent.
- Source errors and fallback wait while dialogs/background lifecycle transitions
  are active. New source attempts invalidate deferred work, including reselecting
  the same URL. Stale source-dialog Apply actions cannot alter a new episode.
- Playback intent (`playWhenReady`) rather than cached actual-playing status is
  restored after dialogs, so opening a dialog during buffering does not leave
  playback silently paused. Intentionally paused startup has no timeout budget.
- Candidate descriptors are cached per sorted state; readiness does not sort the
  whole source pool on each timer tick. TV retains at most four episode link-cache
  entries. Preloading waits for current collection and active dialogs to finish.
- Subtitle sync reads synchronized cue snapshots rather than a decoder-mutated
  live list. Decoder references are player-local, not shared between players.
- WebView resolvers always destroy their WebViews on cancellation, including
  cancellation during polling. TV/low-RAM devices use at most two resolver
  WebViews concurrently. Renderer-process loss is handled by ending that resolver,
  and JavaScript callbacks do not act on already-destroyed views. These changes
  cover the built-in resolver, not arbitrary WebViews created inside plugins.

`PlaybackSafetyTest` covers operation invalidation, concurrent generation updates,
TV/phone buffer policy, bounded episode caches, and concurrent decoder cue
snapshots/resets. Subtitle attachment-policy tests were superseded by the new
independent-controller state/cache/clock tests. Real TV testing is required
for media codec turnover, WebView renderer death, rapid dialog/source changes,
subtitle cycling/file loading, and memory behavior with actual providers.

## Independent external subtitles

External URL/file subtitles are read asynchronously with the existing Media3
DataSource factories (provider headers/interceptors or SAF/file access), decoded
with the existing custom parsers/encoding rules, and rendered using the shared
subtitle styling. They are never merged into the video media source. This path
creates no additional ExoPlayer, decoder clock, audio session, or video reload.

The selected subtitle queries the current video's position every 100ms, using
Media3's legacy Subtitle event index. Cues are restyled/redrawn only when the
subtitle event changes, on seek/offset change, or when explicitly refreshed.
Pauses, speed changes, buffering and seeks follow the existing video clock rather
than a second independently advancing clock. External timing adjustments alter
the lookup offset; they do not seek the video or download the file again.

Selection publishes the requested ID immediately so rapid language-button
cycling progresses even during a pending read. Generation tokens prevent older
reads, parses and failures from winning a newer selection or subtitles-off.
Existing text continues while another external track loads. Failed external
loads preserve a working selection (or restore embedded/off), toast an error,
and do not call video source error/fallback. Failed automatic requests are not
retried on every late provider callback; an explicit user selection can retry.

Only two parsed tracks are cached, with a combined original-file-byte budget of
4 MiB. A single file is limited to 4 MiB and 30,000 parsed cue groups. Decoded cue
objects have additional memory overhead; this byte budget is not a process RAM
cap. At most two blocking loader workers overlap; canceled reads are closed off
main and streams also close in their normal finally/use path. Per-video release
clears the cache and invalidates pending results. Native embedded tracks retain
the existing ExoPlayer selection path, without synthetic external sources.

Opening the source/subtitle chooser no longer pauses video. Applying only a
subtitle does not call loadLink/reloadPlayer. An actual video-source change still
reloads video and honors the current playback intent. Encoding changes reparse
cached subtitle bytes; timing, styling, subtitles-off, source/language preferences
and the existing language cycling order remain supported.

`SeamlessSubtitlesTest` covers rapid selection, stale completion/error suppression,
off/reset, rollback, LRU/byte bounds, bounded/canceled reads, Cyrillic input byte
preservation and offset arithmetic. These are pure policy tests; parser formats,
visual timing and no-rebuffer behavior still require actual TV playback checks.
