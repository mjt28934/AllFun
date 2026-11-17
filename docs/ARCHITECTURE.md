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
media(id PK, kind, sourceId, sourceKey, title, author, description, coverUrl,
      status, year, genres, totalUnits, unitLabel, inLibrary, favorite, addedAt,
      updatedAt, lastOpenedAt, memo)

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
6. `ApkInstaller` opens a `PackageInstaller` session, writes the APK, commits with an
   intent sender and records the system's answer in SharedPreferences through
   `InstallResultReceiver`. The user always confirms in Android's own dialog.
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
- `stream` results are scanned for a direct `http(s)` URL. `behaviorHints.proxyHeaders.request`
  becomes the player's request headers, so a stream that only works with a referer does.
- Subtitles come from the stream's inline `subtitles[]` first, then the addon's `subtitles`
  resource, and are attached as a Media3 subtitle track.

Torrent (`infoHash`) and browser-only (`externalUrl`) results are skipped and reported as
"nothing playable", rather than pretending to play them.

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

## Threading

- Room and OkHttp are called on `Dispatchers.IO` (repositories/importers are `suspend`).
- ViewModels own `viewModelScope`; process-lifetime writes use `AppContainer.appScope`.
- Compose reads Room via `Flow` + `stateIn(WhileSubscribed(5s))`.

## Deliberate omissions

- No Hilt: `AppContainer` is small and explicit. Moving to Hilt later is a mechanical
  translation of its constructor.
- No multi-module split yet: package boundaries already match the future modules.
- No torrent engine, no DRM, no scraping of unlicensed catalogues.
- Unit tests cover version comparison and release parsing (`app/src/test/.../data/update`).
  Room migrations are still covered by real upgrade installs during development, not by
  instrumentation tests, and the UI is verified manually on the emulator.
