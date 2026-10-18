# Selective upstream maintenance

Base: `e0312ef` (latest installed seamless-subtitle branch).
Reviewed upstream: `4c4c679` from `recloudstream/cloudstream:master`.

## Included

- `81dbdf4` / #3183: cache the language-name, BCP-47 and flag regexes in
  `SubtitleHelper`. Preserve matching rules; do not import the Compose
  subtitle-settings screen. The new tests also caught an existing BCP-47 bug:
  literal newlines in its formatted regex rejected even `en`. Join the lines
  before compiling the cached pattern, so normal and regional tags validate.
- `d9c55e2` / #3252: tolerate `SecurityException` when a SAF folder provider
  grants temporary but not persistable access. Continue delivering the selected
  URI. The narrow guard is extracted for tests; unrelated errors are not masked.
  Temporary access is not promised to survive a restart.
- `5cddd5b`: TheIntroDB API v4 and response parsing without a mandatory
  `tmdb_id`. Extract response-to-stamp mapping for direct tests; preserve all
  four stamp categories, TMDB preference, IMDB fallback and null boundaries.
- `b9b0dbb`, `aabcd80`, `31b7bbc`, `9feeaed` / #3179, #3180, #3178:
  Firestream mirror-query handling and `.site` domain, Vixeo extraction, Hexload
  extraction and the associated public API snapshot adjustments. Unlike upstream,
  do not log Hexload request arguments, which may contain access tokens.
- `921c78b` / #3202: clear search suggestions on resume and when submitting a
  search, without changing our TV text-field behavior.

## Deliberately excluded

- Compose settings/accounts migration and related build/dependency changes.
- Dynamic-range audio compressor.
- Start-paused setting and changes to playback/subtitle timing conventions.
- HLS download URL resolution (#3174), which was reviewed but was not part of
  the approved five-item batch.
- Updater replacement/configuration and consolidating the current branch into
  fork `master`; those remain separate decisions.

Our source-selection deadlines, resume checkpoints, playback intent, independent
external subtitles, resource limits, series preferences, remote mappings, Dialog
Apply and Discover/Library/Home behavior are inherited unchanged from the base.

## Validation

- `UpstreamMaintenanceTest`: language-name/tag/flag compatibility, concurrent
  use of shared regexes, optional folder grants, v4 response parsing and stamps.
- `UpstreamExtractorFixtureTest`: intercepted HTTP fixtures for existing Vidsonic,
  new Vixeo and malformed config, Firestream mirror resolver, Hexload form
  submission, v4 movie/series requests and missing identifier/season cases.
- HTTP fixture requests never reach the network; the global request helper is
  restored after each test and fixture resources are closed.
- Existing discovery, remote/navigation, autoplay, source selection and seamless
  subtitle policy tests remain in the GitHub Actions test filter.
- GitHub Actions run `39147096797`, code `a975a76`: 130 tests passed,
  including all 18 maintenance/HTTP-fixture tests; lint and signed debug/stable
  builds passed, and the permanent stable certificate verification passed.
- In-place TV installation and launch succeeded. App process remained alive and
  the crash buffer was empty at the startup check. Installed APK SHA-256 matches
  the downloaded artifact:
  `c3e469973511a1df41fb6a6c76ab5ad0e85af0f86afa77fabb55f10754903e5e`.
- Initial run `39145822398` caught the pre-existing multiline BCP-47 validation
  bug; the follow-up joins the pattern lines and passes both validation and
  concurrent-use checks. No failing build was installed.

Fixtures verify expected request/response formats, not current availability of
third-party hosts or every Android file manager. Real provider/folder interaction
and the previously queued playback confirmations still need TV verification.
