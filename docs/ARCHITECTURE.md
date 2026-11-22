# AllFun — architecture notes

## The one idea

A media item is not "a manga" or "a movie" to the app; it is a row with a `MediaKind`, a
list of units, and one progress value. Everything else is a property of the source.

Consequences:

- Home, Library, Continue, search and progress do not branch on media type except for
  presentation (icon, accent colour, unit label).
- A new kind (podcasts, audiobooks, comics) means a new `MediaKind` + a reader screen; it
  does not mean a new shelf, a new progress model, or a new library screen.

## Tables

```
media(id PK, kind, sourceId, sourceKey, title, author, artist, description,
      coverUrl, backdropUrl, status, year, genres, cast, totalUnits, unitLabel,
      inLibrary, favorite, addedAt, updatedAt, lastOpenedAt, memo)

episodes(mediaId PK, id PK, orderIndex, title, season, number,
         url, payload, durationMs, pageCount, sourceUnitId, memo)
         FK media(id) ON DELETE CASCADE

progress(mediaId PK, episodeId, position, total, updatedAt)
         FK media(id) ON DELETE CASCADE

sources(id PK, type, name, baseUrl, configJson, enabled, builtIn,
        repoUrl, version, addedAt)
```

`favorite` is independent of `inLibrary`: the heart pins a title even if it is only a
preview. `sources.repoUrl`/`version` are what update checks compare; `memo` carries the
extension's own `SManga`/`SChapter` JSON so chapter and page requests keep working after the
catalogue row is saved.

`episodes` is shared on purpose:

| kind | `season` | `number` | `url` | `pageCount` / `durationMs` |
|---|---|---|---|---|
| MANGA | 0 | chapter no. | source handle | page count |
| NOVEL | 0 | chapter no. | EPUB href inside the book dir | — |
| MOVIE | 0 | 0 | — | duration |
| SERIES | season | episode no. | source handle | duration |

`sourceUnitId` is the source's own id for that unit. It exists so refreshing a detail page
maps new `UnitStub`s back onto existing rows instead of duplicating them (ids are derived
from it).

Builds up to 0.2 seeded a demo manga and a demo EPUB into the library. 0.3 drops both on
startup: rows whose `sourceId` is the gone demo source are deleted outright, and the demo
book — which had been imported through the normal EPUB path and so carries no marker — is
matched by the exact title/author it shipped with, then its row and extracted `books/<id>`
directory are removed. Episodes and progress go with them through the foreign-key cascade.

`progress.position` meaning by kind:

- MANGA — zero-based page index, `total` = page count.
- NOVEL — scroll fraction × 1000, `total` = 1000.
- VIDEO — milliseconds, `total` = duration.

`continue` is a join of `media` and `progress` ordered by `progress.updatedAt`, which is
why one row can mix all four kinds.

## Source contract

`MediaSource` is a superset of what each kind needs:

| Member | Manga | Novel | Video |
|---|---|---|---|
| `browse` / `search` | catalogue | imported books | library / addon catalogue |
| `details` | metadata | book metadata | movie/show metadata |
| `units` | chapters | chapters | episodes |
| `pages` | image URLs | — | — |
| `chapterText` / `chapterBody` | — | chapter content (plain text or HTML) | — |
| `chaptersAreRemote` | — | true for network sources | — |
| `stream` | — | — | playable URL + headers |

`SourceHub` owns construction: `SourceEntity.configJson.kind` selects the implementation
(`local-novel`, `local-video`, `stremio`, `jellyfin`). Remote sources are therefore rows,
not code paths scattered through the app.

### Extension hosts

Two of the kinds are not compiled into the app:

- **Komikku/Mihon extension APK** — `ExtensionManager` copies the APK into
  `filesDir/extensions`, makes it `0444`, and loads it with `PathClassLoader`. The host
  implements the `eu.kanade.tachiyomi.*` API locally (the published `extensions-lib` is a
  stub), so extension classes link against real classes in the app process.
- **LNReader plugin** — `NovelPluginHost` runs the plugin in an offscreen `WebView`. The
  runtime provides `fetch` (bridged to OkHttp so cookies/headers behave), a synchronous
  `storage` backed by SharedPreferences, `console`, and CommonJS `require` shims; bundled
  libraries (`cheerio`, `dayjs`, `htmlparser2`) come from `assets/lnreader/lnlibs.bundle.js`.
  Each plugin is loaded with `__allfunLoadPlugin(source)` and then driven through
  `__allfunCall(method, args)`.

### Extension ecosystem

`ExtensionRepository` reads the two formats the ecosystems actually publish:

| Index | Parsed as |
|---|---|
| Keiyoushi / Mihon `index.json` | `extensionList.extensions[]` with a top-level `signingKey`; the legacy bare array is also accepted |
| LNReader `plugins.min.json` | flat array of plugin descriptors |

An index is parsed only if the payload is JSON of the expected shape and yields at least one
installable entry; an HTML error page or an empty list is rejected instead of producing an
empty Discover screen. `isNewer` compares numeric version components, so "1.6.77" > "1.6.9"
and re-listing the same version does not create an update. `BUILT_IN` holds the two public
index URLs; user repositories are just more `RepositoryConfig` rows.

An install is a download, a validation and only then a write:

1. Manga: fetch the APK (96 MB cap) into cache, compare its signature with the repository's
   `signingKey`, then `ExtensionManager` copies it to `filesDir/extensions`, makes it
   read-only and registers its sources.
2. Novel: fetch the script (4 MB cap), install it, and load it through `NovelPluginHost`.
   A plugin that cannot load fails the install.
3. Temporary downloads are deleted in `finally`; a failure leaves the previous install in
   place. Repository installs record `repoUrl` and `version` on every source row so updates
   can be detected later.

Enable/disable writes `sources.enabled` for all of an extension's sources; the extension
itself is presented as enabled while any source is. Uninstall deletes the source rows and,
when no other source references it, the APK or plugin file.

### Adding a remote-source format later

The intended next step is a JSON extension definition:

```json
{
  "id": "example.manga",
  "name": "Example",
  "type": "MANGA",
  "baseUrl": "https://example.org",
  "search": "/search?q={query}",
  "selectors": { "item": ".card", "title": ".title", "link": "a@href" }
}
```

A `DeclarativeSource(config)` implementing `MediaSource` would then be registered the same
way Stremio is. Keeping `MediaSource` as the boundary means readers/players need no change.

## Content aggregation

The app has exactly three content destinations — Movies & Shows, Books & Novels, Manhwa &
Manga — and a provider never becomes one. `ContentRepository` is the only place that turns
enabled `sources` rows into what those screens show:

1. **Category assignment by kind, not by extension.** `SourceType.MANGA` feeds Manhwa &
   Manga, `NOVEL` feeds Books & Novels, and the video kinds (`STREMIO`, `JELLYFIN`,
   `LOCAL_VIDEO`) feed Movies & Shows. Those are the only places a source can appear.
2. **Staged feeds.** Sources are fetched in parallel (six at a time) but the repository
   publishes a `CategoryFeed` snapshot to `feedProgress` after *each* source answers, so the
   first shelf is on screen in seconds instead of waiting for the slowest provider.
3. **Budgets.** Each request has a 15 s deadline (`timed`, built on `withTimeoutOrNull`) and
   each whole feed has a 45 s budget; unified search and source confirmation have 30 s.
   `withTimeout` is deliberately avoided here: it throws a `CancellationException`, which
   callers read as "the screen went away" and rethrow — that bug left a category spinning
   forever when one provider hung. A slow provider is now a normal `ProviderStatus` failure
   ("No answer within N seconds"), not a cancelled feed.
4. **Fair scheduling.** `balancedOrder` interleaves sources by provider name. MangaDex ships
   one source per language (62 rows), so a plain list would fill the parallel window with a
   single provider and starve every other extension.
5. **Dedupe without losing providers.** Items are merged by normalised title (case,
   punctuation, leading articles), keeping the first occurrence's cover and metadata and
   accumulating every provider that returned it. Detail pages list those providers as
   alternatives; nothing is guessed about them.
6. **Failure isolation.** A provider that throws, times out, or returns nothing contributes a
   `ProviderStatus` entry and no items. The category still renders everything else, and the
   UI can say which provider failed and why.
7. **Caching and pagination.** Feeds and searches have short TTLs in memory keyed by
   category+source+page (and query). "Load more" asks each provider for the next page and
   merges the same way, so pagination stays round-robin and duplicate-free.
8. **Unified search.** Every enabled source of the matching kind is searched once; results
   are grouped back into the three categories, deduped, and each card shows its provider.
   The screen can filter to one category or one provider, and a single failed provider only
   removes its own results.
9. **Preferred source per title.** `SettingsStore.preferredSources` maps
   `CATEGORY|normalisedTitle` to a source id. Confirmation only marks a source available
   after an exact title match from its own search, and the preferred source is never applied
   until it is confirmed.
10. **Page convention.** `MediaSource` pages are 0-based. The Tachiyomi API is 1-based, so
    `KomikkuMediaSource` converts with `tachiyomiPage(page) = page.coerceAtLeast(0) + 1`.
    Mixing the two produced `offset=-20` and HTTP 400 from MangaDex.
11. **Detail pages open at catalogue speed.** `CategoryViewModel.open` and
    `GlobalSearchViewModel.open` write a preview row from the catalogue stub
    (`ContentItem.previewDetails()`), navigate on the same frame, and fetch metadata and
    units afterwards. The stub only fills what the shelf already showed (title, cover,
    year, kind); `saveEpisodes` refuses an empty unit list, so an enrichment answer that
    carries no `videos[]` cannot wipe an existing episode list. The detail screen shows
    "Looking for … from your add-ons" while the lookup is in flight instead of claiming
    the title has nothing.

The category screens (`ui/category/`) and global search (`ui/search/`) know nothing about
extensions: they collect a `CategoryFeed` / `SearchResponse` and render shelves, chips and
provider labels. Adding a new adapter only means teaching `SourceHub` to build it.

## Backup and restore

`BackupManager` serialises settings, repository URLs, media, chapters/episodes and progress
to one JSON document with `format: "allfun-backup"` and `schemaVersion: 1`. It intentionally
omits extension code and Jellyfin/Stremio credentials.

Import parses and validates the whole document before touching the database, refuses a
schema newer than the build understands, and then upserts media first, followed by units and
progress (foreign keys require that order). Rows whose kind or id this version cannot read
are counted and reported as skipped. It is additive: nothing already in the library is
deleted. Export/import I/O happens through SAF URIs, so the file can live anywhere the user
picks.

## Settings and motion

`SettingsStore` is a typed DataStore wrapper: enums are stored by name, repositories as a
compact string list, and every screen collects one `Settings` flow. There is no second copy
of a preference, so a change in Settings is visible to the reader/player without a restart.
Invalid or out-of-range values in an imported backup are clamped or replaced with defaults.
`LocalReduceMotion` drives the navigation cross-fade and any other non-essential animation.

## Update pipeline

The updater is a thin, testable chain rather than a web view pointed at a download page:

1. `UpdateApi` calls `repos/mjt28934/AllFun/releases/latest`. Draft and prerelease payloads
   are rejected explicitly — GitHub's `latest` endpoint already excludes them, and the check
   is repeated so a malformed proxy response cannot slip through. The APK is chosen by
   `.apk` suffix; the checksum comes from the asset `digest` (`sha256:…`) or, when absent,
   from a `.sha256` asset fetched and parsed afterwards.
2. `UpdateRepository` stores the release and the check timestamp in a **separate** DataStore
   (`updates`), so a cached release never lands in a settings backup. `checkIfDue` enforces
   the 12-hour cooldown shared by launch checks and the worker; a 404 is reported as "no
   release published", everything else as a readable network error.
3. `AppVersion` compares numeric components only, padding missing parts with zero. There is
   exactly one comparison rule, used by the screen, the worker and the release workflow's
   `versionCode` derivation (`major*10000 + minor*100 + patch`).
4. `UpdateDownloader` streams the asset to `cacheDir/updates` while updating a SHA-256
   digest, emits byte progress, and only renames `.part` to the final name when the digest
   matches the published value. Cancellation and every failure delete the partial file; a
   release without a checksum is refused before the request.
5. `ApkInspector` reads the downloaded archive with `getPackageArchiveInfo`: package name,
   `versionCode` greater than installed, and signer SHA-256 equal to the running app's. A
   `versionName` mismatch with the release tag is also rejected.
6. `ApkInstaller` opens a `PackageInstaller` session, writes the APK and commits with an
   intent sender. The session's first answer is `STATUS_PENDING_USER_ACTION`; the receiver
   starts the installer activity from `EXTRA_INTENT` so Android can show its confirmation
   dialog, then records the final status in SharedPreferences. Without that hand-off the
   session waits forever, which is the bug the first published build had. The user always
   confirms in Android's own dialog.
7. `UpdateCheckWorker` is a unique periodic job (12 h, `NetworkType.CONNECTED`). It reads
   the user's `autoCheckUpdates` preference, and only posts a notification when
   `notifyUpdates` is on and the tag has not been announced before. `UpdateScheduler.sync`
   enqueues or cancels it when the switch changes.

Notification permission is checked at the point of use (`UpdateNotifier.notificationsAllowed`)
and requested by the screen on Android 13+, so the switch never claims a notification the
system will suppress.

## Stremio protocol

`configJson` stores the **whole manifest**, because the manifest is the contract:

- `catalogs[].extra[]` decides which query shapes are legal. `search` is only sent to
  catalogs that declare it (undeclared ones are tried after, as a fallback), and `skip` is an
  **item offset**, not a page number — page *n* requests `skip = n * 100`.
- `meta` supplies `videos[]` for series; entries are sorted by season/episode when the data
  allows it, with `season == 0` kept as specials.
- `stream` results are parsed into direct and torrent options. `behaviorHints.proxyHeaders.request`
  becomes the player's request headers, so a stream that only works with a referer does.
- Subtitles come from the stream's inline `subtitles[]` first, then the addon's `subtitles`
  resource, and are attached as a Media3 subtitle track.

A **stream-only addon** (no `catalogs`, but a `stream` resource) is a valid Stremio addon:
Torrentio and similar answer for ids supplied by another addon. It is accepted at install
time, excluded from category feeds and source counts by `SourceHub.isStreamProviderOnly`, and
queried whenever a title is played.

Metadata is resolved the same way as streams, by resource rather than by origin.
`SourceHub.detailsWithUnits` asks the origin addon only when its manifest declares `meta`
for the id, and then fans out to every other installed video addon that declares `meta` and
accepts the id, in parallel with a 12 s cap. `StremioManifest` is the single resource test
shared by this fan-out and `StremioSource`. The rule exists because a catalogue-only addon
(Streaming Catalogs declares `resources: ["catalog"]`) answers any other request with an
HTML page — asking it for `meta` was what made its titles fail with
`InvalidResponseException`. Answers that include `videos[]` win; the bundle is cached for
five minutes and dropped (`forgetDetails`) when the addon that served it is removed.
`StremioSource.metaBundle` performs one `meta` request that serves both `details()` and
`units()`, so the fan-out costs one round trip per addon, not two.

`SourceHub.streamsFor` asks **every** installed Stremio addon that declares the `stream`
resource and accepts the id, in parallel with a 20 s cap, and merges the answers:

- The origin addon is still queried; non-Stremio sources (Jellyfin) keep using their own
  single stream and never fan out to IMDb-keyed addons.
- Duplicates collapse on the infohash (`xt=urn:btih:`), not on the URL, because addons
  advertise the same torrent through different trackers and display names.
- Direct HTTP results come first — they start instantly. Torrents follow, ordered by quality
  rank (1080p, 720p, 2160p, 480p, then unknown) and then by advertised seed count. 1080p
  leads because it buffers on a phone far faster than a 4K remux; every resolution stays in
  the picker.
- At most 60 options are returned, so a dead tracker still has alternatives without an
  endless picker.

Browser-only results (`externalUrl`) are still skipped: AllFun cannot hand a page to an
external player and claim it played the title.

## Torrent playback

A torrent result is not a URL, so it is played in three steps:

1. `StremioSource` builds a `TorrentRef` from `infoHash`, `sources[]` trackers and the
   optional `fileIdx`/`filename` hints (magnet construction, base32 hash decoding and
   quality detection live in `StremioTorrents`). `StreamInfo.torrent` being non-null is the
   only marker the player needs.
2. `TorrentEngine` (TorrentStream + libtorrent4j) opens the magnet, waits for metadata,
   selects the addon's file index — or the largest file when the addon sent no filename —
   and calls `startStream()`, which prioritises pieces sequentially around the play
   position. Its callbacks report piece buffer percentage, peers and speed to the player,
   which shows them instead of a blank spinner.
3. `TorrentDataSource` implements Media3's `DataSource` over the prepared torrent: open,
   read, length and seek are served from the local file libtorrent is filling, and HTTP
   downloads fall back to `DefaultDataSource`. The player therefore uses
   `torrent://allfun/stream` as an ordinary media URI, and seeking, subtitles and progress
   saving need no special cases.

The engine holds one session at a time: opening another episode or leaving the player calls
`stop()`, so the app does not seed a video the viewer has left. R8 must keep `org.libtorrent4j`
and `com.github.se_bastiaan.torrentstream` intact — both are reached through JNI and
reflection.

## Download queue

The queue is a schedule, not a list of wishes, and it is persisted:

- **Explicit order.** `downloads.queueOrder` holds the position; `DownloadDao` orders every
  query by `(queueOrder, createdAt)`. Enqueueing appends after the current maximum, with the
  batch sorted newest chapter first (Komikku's `sortedByDescending(sourceOrder)`), so a
  freshly queued volume starts with what the reader most likely wants. `reorder`,
  `moveToTop`, `moveToBottom`, `moveGroup` and `sortQueue` rewrite the column; a restart
  therefore restores the same order instead of falling back to creation time.
- **One pause for the whole engine.** `pauseAll` cancels running workers, writes
  `downloadQueuePaused` to settings and leaves every active row `QUEUED` with the note
  `Paused`; `pump` refuses to start anything while that flag is set. `startAll` clears the
  flag, requeues failed rows and lets `pump` continue from the same positions and partial
  files. A single row pauses as `WAITING`/`Paused` and resumes the same way, so the screen
  can treat both as "paused by the user" while Wi-Fi blocking (`Waiting for Wi-Fi`) is
  never mistaken for one.
- **Retries belong to the transfer.** `retryTransfer` retries a failed transfer three times
  with 2 s / 4 s / 8 s backoff; cancellation is rethrown, not retried. Metadata fetches and
  page downloads share the wrapper, so one flaky request no longer fails a whole chapter.
- **Progress is measured, not assumed.** Group headers average only rows that reported a
  percentage, and leave the number out when no row has one, so a queue entry that has not
  started cannot pin the header at 0%.
- **Grouping by title, not by source.** Rows are grouped under `mediaId` (deliberate
  divergence from Komikku's per-source headers) so "pause/cancel/move all" maps onto the
  title the user sees. `clearQueue` deletes unfinished rows and their partial files but
  keeps completed downloads, because the Library's offline filter depends on those files.

## Release builds and dynamic code

Anything loaded at runtime (extension APKs) resolves classes **by name**, so R8 must keep
those names and must not delete stdlib members the app itself never calls. Two consequences:

1. `app/proguard-rules.pro` keeps `eu.kanade.tachiyomi.**`, `uy.kohesive.injekt.**` and the
   libraries extensions use directly (Kotlin stdlib, coroutines, serialization, OkHttp, Okio,
   Jsoup, AndroidX preference).
2. Extension hosts must be re-verified on a **release** build, not just debug. A missing keep
   rule shows up as `Failed resolution of: Lkotlin/collections/SetsKt;` at install time and
   never in debug, because debug does not shrink.

## Progress and resume

Every reader/player picks its own start point:

1. `startEpisodeId` nav argument (user tapped a specific chapter/episode), else
2. Saved `progress.episodeId`, else
3. First unit, at position 0.

Writes are debounced in the UI layer but flushed through `container.appScope` so a save
lands even when the screen (and its `viewModelScope`) is being torn down.

## EPUB pipeline

1. `BookImporter` copies the picked stream to a temp file.
2. `EpubParser.extract` unzips into `filesDir/books/<mediaId>/` with zip-slip protection,
   reads `META-INF/container.xml` → OPF, spine, nav/NCX titles and cover.
3. Media + chapter rows are written; `episodes.url` is the href relative to the book dir.
4. The reader renders that file in its WebView (see below), so EPUB and network chapters
   share one screen, one theme and one progress model.

## Novel reader pipeline

Two shapes, one screen:

- **Imported EPUB** — `episodes.url` is a relative href; the reader loads
  `file://…/books/<id>/<href>` and injects the theme.
- **Network source** (`MediaSource.chaptersAreRemote`) — the chapter body is fetched through
  `chapterBody` (HTML) or `chapterText` (plain text), sanitised, wrapped in a minimal
  document and rendered with `loadDataWithBaseURL(null, …)`. Remote markup is untrusted: the
  sanitiser drops `script`/`style`/`iframe`/`object`/`embed`, every `on*` attribute and
  `javascript:` URLs, and rewrites relative `href`/`src` to absolute.

Progress is a 0..1000 scroll fraction either way, so a chapter read from a plugin and one
read from an EPUB resume identically.

## Local video pipeline

SAF tree picker → persistable read permission → recursive scan (depth 4, video extensions)
→ group by parent folder. A folder with ≥2 videos becomes a SERIES (episode numbers parsed
from filenames); loose files become MOVIES. Episode `url` is the `content://` document URI,
which ExoPlayer plays directly while the persisted permission is held. The player wraps
`DefaultHttpDataSource` in `DefaultDataSource`, so `content://` and `file://` resolve
through ContentResolver while Jellyfin/Stremio headers still apply to http(s) streams.

## Player surface and errors

`PlayerScreen` distinguishes two failures: the source layer failing to resolve a playable
`StreamInfo` (`state.error`, already a user-facing message plus technical text) and Media3
failing while opening or decoding the stream (`playbackError`, built from
`PlaybackException.errorCodeName` and its message). Both render the same actionable panel:
a readable sentence, Back, Retry, and an optional **Technical details** disclosure.

While that panel is visible, `useController` is turned off and buffering is set to
`SHOW_BUFFERING_NEVER`, so PlayerView's control bar and spinner cannot draw over the error.
Retry clears the playback error and re-resolves the stream; if the same stream still fails,
the error returns rather than looping silently.

`PlayerViewModel` keeps the whole candidate list. One option plays directly; more than one
opens the stream picker overlay before Media3 is touched, so an unreachable torrent never
becomes a dead end. A torrent option first passes through `TorrentEngine`, which reports
buffer percentage, peers and speed; only `onReady` hands the pseudo-URL to the player.

## Threading

- Room and OkHttp are called on `Dispatchers.IO` (repositories/importers are `suspend`).
- ViewModels own `viewModelScope`; process-lifetime writes use `AppContainer.appScope`.
- Compose reads Room via `Flow` + `stateIn(WhileSubscribed(5s))`.

## Deliberate omissions

- No Hilt: `AppContainer` is small and explicit. Moving to Hilt later is a mechanical
  translation of its constructor.
- No multi-module split yet: package boundaries already match the future modules.
- No DRM, no scraping of unlicensed catalogues, no offline torrent storage (torrents stream
  only; direct HTTP files still download).
- Unit tests cover version comparison, release parsing, mapping helpers, the torrent helpers,
  Stremio resource support and the download queue's pure state rules
  (`app/src/test/.../`). Room migrations are still covered by real upgrade installs during
  development (including the v5 → v6 download-queue migration), not by instrumentation
  tests, and the UI is verified manually on the emulator.
