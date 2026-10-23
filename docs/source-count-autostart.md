# Auto-start after a configurable number of sources

## User request

Add an initial collection setting, default **50**: start playback after that many
video sources have loaded. This explicitly extends the previous wait-for-all
behavior; it does not restore the old priority/time-based automatic Skip rules.

## Behavior and settings compatibility

Active **Settings → Player → Auto-start after sources loaded** offers 0–500:

- **50** by default: wait while 49 usable sources are available; start once 50 or
  more arrive, even if extraction is still running. LiveData can coalesce bursts,
  so a callback may contain more than exactly 50 sources.
- **0** disables count-based startup and waits for collection completion unless
  the user manually presses Skip.
- A smaller completed collection starts without waiting for an impossible count.
  An empty/fully hidden completed collection uses the existing no-links handling.
- Manual Skip can start before the configured count. Providers declaring that
  they cannot skip still wait for completion, preserving their extraction contract.
- Count distinct **allowed video-source identities**, not subtitles, duplicate
  labels/callbacks, or hidden sources. The Skip count uses the same policy.
- Resolve the initial source through the existing saved-series/profile/last-working
  ranking. Starting early cannot wait for a preferred descriptor that has not yet
  arrived; late links remain selectable. Priorities themselves do not imply Skip.
- Auto-start does not set the manual-Skip flag, stop extraction, fake completion,
  reset failover history, or create a separate player for every source. Only one
  backend starts through the existing active-player guard; late sources/subtitles
  continue loading. Skip focus cannot steal focus after playback has started.
- This count applies only to initial collection. It is not an additional threshold
  between fallback mirrors or an episode URL persisted across sessions.

`PlayerPreferences.sourceAutoStartCount` stores `source_auto_start_count` (default
50). The production settings adapter and active Compose slider share this key;
backup/restore includes it. Each source-collection session snapshots the saved
count. Positive restored values beyond the slider range remain respected; invalid
negative values fall back to 50 without rewriting the preference.

The source startup wait setting (`source_startup_wait_seconds`, default 5, zero
turns its timeout off), start-paused, seek amounts, mappings, source visibility,
series descriptors and playback intent remain independent and unchanged. No user
settings or bindings are assigned by implementation/validation.

English/Russian active quality-profile help now describes all three startup routes.
Standing instructions in root `AGENTS.md` and older documentation cross-references
reflect this explicitly requested behavior change.

## Validation / deployment

Run [40020715901](https://github.com/gogamid/cloudstream/actions/runs/40020715901)
passed for `e3cb65b574aabe584acf838c403606882205f3e9`:
**789 app/library tests (246 + 543), no failures/errors/skips, all 22 Android checks**,
lint, JVM/Android compilation, APK builds and permanent signing verification.
Added/retained gates:

- Sixteen pure policy regressions: default 49/50 boundary; custom/zero/large/invalid
  thresholds; completion before count; manual Skip; non-skippable providers; hidden
  and duplicate identities; exhausted attempts; unchanged ranking/Skip state;
  new episodes; empty result; saved preferences; backup transferability.
- Prior full-collection tests explicitly use count 0, retaining their assertions
  instead of dropping coverage for the wait-for-all setting.
- Three phased-generator Android checks use the real ViewModel and production
  settings adapter: default 49→50 then 60 sources, custom 2→3 then 13 sources,
  and count 0 still waiting at 50 before completion. Verify truthful Loading and
  continued collection/late links, not simulated parallel video downloads.
- One actual active Compose preference check: default 50, custom 25 persists through
  Activity recreation, disable at 0, and per-source startup wait stays at 15 seconds.
- All prior tests/build/lint/signing gates retained; instrumentation requires all
  **22 Android checks**, up from 18. No local Termux Gradle build.

Stable APK SHA256:
`1d71021347fe673faa191a7330e7118e1aada93757477d37118e71c4f8e1f8ae`.
Artifact/reports: `~/downloads/cs-source-count-40020715901/`.

## TV deployment verified

After the user's explicit request to install before continuing CI optimization,
connected to `192.168.0.139:5555` and updated the stable package with
`adb install --no-streaming -r`: **Success**. No uninstall, data clearing, preference
rewrite or remote-key assignment was performed.

The installed base.apk SHA256 matches the validated artifact above. Launched
`com.lagradost.cloudstream3`; PID `15050` remained running after 12 seconds, with
an empty crash buffer. This supersedes the previously installed source-wait build.
The CI workflow optimization was still an uncommitted local edit and is not part
of this installed APK.

Automated policy/settings/provider-fixture results and startup verification are
not live-TV playback/codec/host or long-duration memory verification.
