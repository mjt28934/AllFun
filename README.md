# AllFun

One Android app for **manga, light novels, movies and series** — a single shelf, a single
progress bar, one Continue row that doesn't care what kind of media it is.

Komikku/Mihon and LNReader each pick one medium. AllFun treats "what you're reading or
watching" as the unit, not "what app you opened".

```
Home      ─┬─ Continue watching / Continue reading (mixed kinds)
           ├─ Favorites · Recently opened · Recently added
           ├─ Movies & Shows · Books & Novels · Manhwa & Manga cards
           └─ search across every category at once
Movies    ─ films, series, catalogues, compact toolbar, filters in a sheet
Books     ─ novels, light novels, authors, reading history
Manga     ─ manga, manhwa, manhua, comics, chapters, reading history
Library   ─ All · Movies · Books · Manga
            Everything · Downloaded · Favorites · Recently Added · Recently Opened
Settings  ─ Appearance · Accessibility · Reading · Playback · Extensions & sources ·
            Downloads (queue and storage) · Storage · Network · App Updates · Backup ·
            About
Detail    ─ metadata, source selector, favourite, chapter/episode list, resume,
            per-chapter download controls
Reader    ─ vertical / paged / right-to-left manga with zoom;
            offline pages, or a network novel reader with live typography
Player    ─ Media3, resume, subtitles, next episode, offline files, readable errors
Extensions─ full screen marketplace opened from a category toolbar:
            Discover · Installed · Updates · Repositories
Sources   ─ add/manage remote providers (reached from Settings and empty states)
```

There are exactly **six** destinations. Extensions, add-ons and providers feed the three
content categories in the background: a Stremio addon can only contribute to Movies &
Shows, a novel plugin to Books & Novels, a Komikku extension to Manhwa & Manga. None of
them ever becomes its own tab, and the marketplace opens scoped to the category whose
toolbar button was pressed.

## Install

AllFun requires Android 8.0 (API 26) or newer. Grab the latest signed APK from
[GitHub Releases](https://github.com/mjt28934/AllFun/releases/latest):

```bash
AllFun-<version>-release.apk        # the app
AllFun-<version>-release.apk.sha256 # its SHA-256, published next to it
```

Verify the checksum, then open the APK. Releases are signed with one fixed certificate, so
each version installs over the previous one and keeps the library:

```powershell
(Get-FileHash .\AllFun-0.7.2-release.apk -Algorithm SHA256).Hash.ToLower()
Get-Content .\AllFun-0.7.2-release.apk.sha256
```

From inside the app, **Settings → About → App Updates** does the same work without a
browser: check, download, verify, install.

## Status

Built and verified locally with JDK 17 + Android SDK 35:

| Check | Result |
|---|---|
| `:app:assembleDebug` | **BUILD SUCCESSFUL** |
| `:app:assembleRelease` | **BUILD SUCCESSFUL** — signed, R8 + resource shrinking, 11,134,950 bytes |
| `:app:testDebugUnitTest` | **36 tests pass** — serialization compatibility (generic, polymorphic, defaults, unknown fields), semantic version comparison, release JSON parsing, checksum-file parsing, category/dedupe/format mapping, category sorting, Tachiyomi page conversion |
| APK metadata | `app.allfun`, `versionCode 702`, `versionName 0.7.2`, v2 signature, `CN=AllFun` |
| Runtime, Android 15 / API 35 (emulator) | Signed 0.7.2 APK installed over 0.7.1 (same signer, library, extensions and downloads kept) → six destinations, compact category toolbars with the extension button top-right, category-scoped marketplace with focused search, real Weeb Central downloads, offline reading, Settings → Downloads, Library download badges and sizes; empty and error states; no crash |
| Unified content layer | Three categories are built from every enabled source of the matching kind: staged arrival, round-robin shelves, title dedupe with provider merging, per-request timeouts and a feed budget, provider state reporting with Retry and diagnostics, pagination, global search grouped by category, and a remembered preferred source per title |
| Manga category, end to end | Weeb Central (real Keiyoushi extension installed from Discover) → Popular shelf with real covers → One Piece detail (1198 chapters, author, genres) → reader loads real page images → progress on the shelf |
| Offline downloads | Manga chapters saved as page folders + `pages.json`, novels as saved chapter HTML, direct HTTP video streamed with progress; chapter 1055 (11,087,549 bytes) and chapter 24 (4,968,054 bytes) completed, opened with Wi-Fi and mobile data off, and survived a force-stop; HLS/DASH manifests are refused honestly |
| Download manager | Settings → Downloads: active, queued, completed and failed sections, real progress, pause/resume/retry/cancel/remove, clear finished, Wi-Fi-only, concurrency limit, confirmations |
| Library | All / Movies / Books / Manga with Everything / Downloaded / Favorites / Recently Added / Recently Opened scopes, download count, size and completion date on the cards, remove-download with confirmation |
| Extension marketplace | Real Keiyoushi and LNReader indexes; category scopes All / Movies / Books / Manga; Discover / Installed / Updates / Repositories; install, update, update all, enable, disable, uninstall, permissions |
| Source states | Working, Loading, No content, Temporarily unavailable, Auth required, Invalid response, Disabled and Unsupported, each with Retry and a separate technical-details disclosure; one failing provider does not block the rest |
| Cross-source search | "Lantern" returns 3 grouped results across Movies & Shows, Books & Novels and Manhwa & Manga with provider chips and source filters; "one piece" returns real manga results from the installed extension |
| Extension ecosystem, end to end | Real APK installed from storage → catalogue → details → chapters → page images in the reader → progress on the shelf; real LNReader plugin → catalogue → details → chapter text |
| Modern extension compatibility | Keiyoushi extensions built with Kotlin 2.4 / kotlinx-serialization 1.13 load and browse without `AbstractMethodError`: Asura Scans (feed, search, details, 2 chapters, 16-page reader), MangaFire, Vortex Scans, Flame Comics and Drake Scans (feed, 190 titles), plus an in-app repository install; Comick fails only on the site's Cloudflare 403 and is reported per source |
| Stremio addon, end to end | Manifest → catalogue → details → direct HTTP stream with proxy headers → sidecar subtitles → season/episode mapping |
| Backup and restore | 371 KB JSON export → restore reported 5 titles, 927 chapters/episodes, 5 progress entries and settings; a non-backup file is refused with a readable message |
| Settings maintenance | Storage stats, clear cached files without touching the library, repository connection test (1,710 extensions in 1,194 ms) |
| Release (R8) runtime | All paths above re-verified on a signed release build |
| In-app updates | GitHub Releases API → cached release → download with progress → SHA-256 + package + signer verification → `PackageInstaller` confirmation |
| Published release | [v0.7.2](https://github.com/mjt28934/AllFun/releases/tag/v0.7.2) — `AllFun-0.7.2-release.apk`, 11,134,950 bytes, SHA-256 `67df2030e2476014f3988b4dfc3393dea77623039fcbf81dcd5016827ddd8eab`; downloaded back, checksum and signer re-checked against the workflow build, and the release is the repository's latest |
| Version | `0.7.2` (versionCode 702) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |

There is no bundled sample content: a fresh install has an empty shelf and no sources at
all until you add a provider or install an extension. Nothing is seeded, pre-filled or
recommended behind your back. Builds up to 0.2 shipped a demo manga and a demo EPUB; 0.3
removed both from the APK, an upgrade deletes the library rows those builds had seeded, and
0.7.0 removes the local EPUB/video importers entirely (startup cleanup drops any rows they
left behind).

Screenshots from the emulator run live in `docs/screenshots/`:

| | |
|---|---|
| `home.png`, `library.png` | empty first run — no bundled demo content |
| `category-books.png` | Books & Novels: reading history from a real plugin |
| `search-global.png` | "Lantern" grouped across Movies & Shows, Books & Novels and Manhwa & Manga |
| `search-manga.png` | Manga search results with real covers and provider labels |
| `reader-manga.png` | a real extension's chapter pages in the manga reader |
| `browse.png` | the Sources screen: add or manage remote providers |
| `browse-stremio.png`, `detail-stremio.png` | a Stremio addon's catalogue and a movie |
| `detail-series-stremio.png` | season/episode grouping from addon metadata |
| `extensions-installed.png` | installed extensions with version, developer and status |
| `extensions-repositories.png` | the real Keiyoushi/LNReader indexes, add and retry |
| `settings.png` | About on 0.5.0 after an in-app update, with the App Updates row |
| `updates.png` | App Updates after installing 0.5.0: versions, and the system installer's result |
| `settings-playback.png` | novel typography and playback settings |
| `player-subtitles.png` | a direct stream playing with its subtitle track |
| `reader-novel-plugin.png` | chapter text from an LNReader plugin |

## Sources

The source layer is what makes the app more than a player. Every source implements the
same small contract, and the UI never knows which kind it is talking to.

| Source | Type | What it does |
|---|---|---|
| **Jellyfin** | video | Username/password login, catalogue, show episodes, direct-play stream with token header. |
| **Stremio addon** | video | Manifest/catalog/meta/stream/subtitles protocol. Catalog `extra` capabilities are honoured (search only where declared, `skip` as an item offset), `proxyHeaders` become player headers, inline or resource subtitles become a track. Torrent-only results are reported honestly, not faked. |
| **Manga extension (.apk)** | manga | A real Tachiyomi/Mihon/Komikku extension APK. Its `CatalogueSource` is driven through AllFun's own `eu.kanade.tachiyomi.*` host classes: browse, search, details, chapter list and page images. |
| **Novel plugin (.js)** | novel | A real LNReader plugin. It runs in an offscreen `WebView` with bundled `cheerio`/`dayjs`/`htmlparser2`, a CommonJS `require` shim for `@libs/*`, an OkHttp-backed `fetch` bridge and a synchronous `storage` that mirrors to SharedPreferences. Chapter HTML is sanitised (scripts, inline handlers and `javascript:` links removed, relative links made absolute) before rendering. |

Stremio addons are installed by URL; Jellyfin by URL + credentials; extensions either by
picking a test-signed APK/plugin file or from a repository index inside **Extensions**.
Nothing executable is downloaded behind the user's back: a repository install is a file the
user explicitly chose, is validated first, and is copied into private storage and made
read-only (Android 14+ refuses to map a writable dex file). Manga APKs from a repository are
signature-checked against the index's `signingKey` before they are installed.

### How the extension hosts work

Komikku/Mihon extensions are compiled against `extensions-lib`, which on Maven Central is a
stub ABI. AllFun **implements** the real `eu.kanade.tachiyomi.*` classes the extensions link
against (`source.CatalogueSource`, `source.Source`, `network.Requests`, `network.Headers`,
`source.model.*`, …) and loads extension APKs with a `PathClassLoader` whose parent is the app
class loader. That is why no `extensions-lib` runtime is needed and why an unmodified
extension APK works.

Because the extension resolves those classes by name at runtime, the release build must not
rename or strip them. `app/proguard-rules.pro` keeps the host ABI **and** the libraries
extensions call directly (Kotlin stdlib, coroutines, serialization, OkHttp, Okio, Jsoup).
Without that, a release build installs an extension and then dies on
`Failed resolution of: Lkotlin/collections/SetsKt;` — a failure that never appears in debug,
because debug does not shrink.

## Extensions in the app

The marketplace is one screen for every installable kind. It is opened from the extension
button at the top right of Movies & Shows, Books & Novels or Manhwa & Manga — already scoped
to that category, with the search field focused — or from Settings. There are four sections:

- **Discover** — the repository catalogue for the current scope (All, Movies, Books, Manga),
  searchable, with All / Available / Installed / Updates / Compatible filters. The Install
  button becomes Update when that key is already installed.
- **Installed** — real installed files with name, icon, description, kind, version,
  developer, source count, install/update status and repository. Tapping one opens details:
  permissions declared by the APK, compatibility, and enable/disable or uninstall.
- **Updates** — installed extensions whose repository version is newer. Each row updates on
  its own, or **Update all** runs them sequentially and reports each result.
- **Repositories** — the built-in Keiyoushi (manga) and LNReader (novel) indexes plus any
  the user adds. Each row shows the entry count, last error and a Retry action; repositories
  can be removed. Adding a URL validates it and parses the index before saving.

The Sources screen (Settings → Extensions & sources → Sources) still lists every connected
provider in one place, including Jellyfin and Stremio addons, and never becomes a tab.

The two built-in indexes are the real public ones:

| Kind | Index | Shape |
|---|---|---|
| Manga | `raw.githubusercontent.com/keiyoushi/extensions/repo/index.json` | `extensionList.extensions[]`, top-level `signingKey` |
| Novel | `lnreader-plugins/.../.dist/plugins.min.json` | array of plugin descriptors |

Both the modern Keiyoushi object and the older bare-array index are parsed; LNReader entries
carry the plugin id, script URL and metadata. Version comparison decides whether to show
Update, so a repository re-listing the same version does not produce a phantom update.

Before anything becomes usable it has to prove it is what it says: a manga APK's signature
must match the repository's `signingKey`, and its classes must load; a novel plugin must
load successfully in the plugin host. A failed validation is a real error with the reason,
not a disabled placeholder row. Downloads and installs run off the main thread with a visible
busy label, and failures leave the previous installation untouched.

## Reading and playback

- **Manga reader** — vertical scroll or paged mode, left-to-right or right-to-left paging,
  fit width / fit height / whole page, pinch-to-zoom and pan, a page slider, and a searchable
  chapter picker. Errors show a readable sentence and a separate technical-details disclosure.
- **Novel reader** — downloaded chapters and network chapters share one WebView. Font size,
  line spacing and Dark/Sepia/Light themes apply live without reloading the chapter, and the
  chapter picker is searchable.
- **Player** — Media3/ExoPlayer, resume where you stopped, live subtitle on/off, next
  episode, periodic progress saves and guidance buttons on failure. A downloaded direct-HTTP
  file plays from app storage without a network. A source error and a playback error are
  different states: each has a readable message, Retry, and the raw `ERROR_CODE_…` behind
  **Technical details**. While the error column is shown, the player's own controller and
  buffering spinner are hidden so nothing is drawn over it.

## Downloads

Downloads are real files owned by AllFun, not bookmarks:

- **Manga** — every page is saved as a numbered image plus a `pages.json` manifest in
  `files/downloads/<title>/<chapter>/`.
- **Novels** — the chapter HTML is saved and rendered by the same sanitiser as the live text.
- **Video** — only direct HTTP files are streamed to disk with byte progress. HLS and DASH
  manifests (`.m3u8`, `.mpd`) are refused with a plain explanation instead of a fake entry.
- **Queue** — `Settings → Downloads` shows active, queued, completed and failed items with
  real progress; pause, resume, retry, cancel and remove, plus clear finished, Wi-Fi-only and
  a concurrency limit. Removal asks before deleting files.
- **Library** — a completed download appears immediately under **Downloaded** with its size,
  completion date and a badge on the card. Rows and files survive a restart; missing files
  are reported rather than silently listed as available.

## Settings, backup and storage

Settings has eleven sections, and every control writes a real preference that the app reads:

| Section | What it changes |
|---|---|
| Appearance | System/Dark/Light, Material You colours, library grid/list and sort, Home shelf visibility |
| Accessibility | Reduce motion (all non-essential animation), plus a statement of the app's font-scaling and touch-target behaviour |
| Reading | Manga layout, fit, page order, novel text size, line spacing and theme |
| Playback | Resume position, subtitle tracks |
| Extensions & sources | Links to the Sources and marketplace screens and a summary of how providers feed the categories |
| Downloads | The download queue: progress, pause/resume/retry/cancel/remove, Wi-Fi-only, concurrency, storage use |
| Storage | Cache/extension/database sizes, clear cached files, refresh |
| Network | Timeouts and a real repository connection test |
| App Updates | GitHub Releases check, download and install |
| Backup | JSON export/restore through the system file picker |
| About | Version, what the source layer runs, built-in repositories, unsupported features |

**Backup** contains settings, repository URLs, the library, chapters/episodes and progress.
It deliberately excludes extension code and Jellyfin/Stremio credentials, so it cannot
smuggle secrets onto another device. Import parses the whole file before writing, refuses a
newer schema, and is additive: rows are upserted by id, nothing currently in the library is
deleted. Export and import use SAF, so the file can live in Downloads or cloud storage.

## In-app updates

`Settings → About → App Updates` is a real client for this repository's GitHub Releases,
not a link to a browser tab.

**Checking.** `UpdateApi` calls
`https://api.github.com/repos/mjt28934/AllFun/releases/latest`. GitHub only returns a
published, non-draft, non-prerelease release there, so a commit or a bare tag can never be
mistaken for an update. The response supplies the tag, notes, publication date, APK asset
and URL, size, and the checksum — either the asset's `digest` (`sha256:…`) or the release's
`.sha256` asset, which is downloaded and parsed when present. Missing values are shown as
not published rather than invented.

**Versions.** Tags are compared numerically, component by component
(`1.0.10 > 1.0.9`), treating missing components as zero. The installed side comes from
`BuildConfig.VERSION_NAME`; `versionCode` is read from the installed package and from the
downloaded APK. The release workflow derives `versionCode` from the tag with the documented
scheme, so both values always agree.

**Automatic checks.**
- On launch, at most once per 12 hours (`UpdateRepository.CHECK_INTERVAL_MS`).
- A periodic WorkManager job every 12 hours, constrained to `NetworkType.CONNECTED`.
- The result is cached in a dedicated `updates` DataStore together with the last check
  time, so the screen opens instantly and repeated launches do not hammer the API.
- Both behaviours are user switches. Turning automatic checks off cancels the unique
  work; notification permission (Android 13+) is requested before promising a
  notification, and one low-priority notification is posted per published version.

**Downloading.** The APK is streamed from the release asset into the app cache, hashed with
SHA-256 in the same pass, and only renamed into place when the digest matches the published
one. Progress is real bytes over the HTTP body, the action can be cancelled (the partial
file is deleted), and a failed or mismatched download can be retried. Without a published
checksum the updater refuses to install instead of trusting the file.

**Installing.** Before the installer sees the file, `ApkInspector` checks:
- the package name is `app.allfun`;
- the APK's `versionCode` is greater than the installed one;
- the signing certificate is the same as the running app's (SHA-256 of the signer);
- the APK's `versionName` matches the release.

Then `PackageInstaller` copies it through a session and commits with an intent sender. The
system shows its own confirmation dialog; AllFun never installs silently and never turns
off Android's install protections. The result comes back through `InstallResultReceiver` and
is shown as "Last installation" the next time the screen opens. If "install unknown apps"
is not granted yet, the screen offers the system setting instead of failing with an opaque
error.

**Verified end to end.** A signed 0.4.0 build (versionCode 400, same keystore) was installed
with a restored library: a movie with saved progress, a novel and a manga. Inside the app,
App Updates found 0.5.0 through the real API, downloaded the published asset (11,039,153
bytes), verified its size, SHA-256, package name and signing key, opened the system's "Do
you want to update this app?" dialog, and installed it. Afterwards the package reports
`0.5.0` / `500`, the same library and progress are on Home, and the installer reported
`INSTALL_SUCCEEDED: Session installed`. The installed `base.apk` hashes to
`d2a16106…900df`, the same SHA-256 as the GitHub asset, so the running app is byte-for-byte
the published file, not a local rebuild.

## Design and motion

The visual system is Material 3-inspired and dark-first: ink surfaces (`Ink`, `InkRaised`,
`InkCard`), an amber accent, a 32/25/21 sp display-to-section scale, 16 and 22 dp shapes,
and a consistent 16–20 dp gutter. Posters carry their title and metadata below the cover
instead of over it; cards show format, the number of alternate providers and saved progress.

Motion is centralised in `ui/theme/Motion.kt` (140 / 220 / 320 ms with shared easing):
tab switches cross-fade while pushed screens slide in, the bottom bar's pill glides to the
selected destination, cards scale slightly on press, shelves fade/slide in as real content
arrives, progress bars animate, and descriptions expand smoothly. Shimmer skeletons appear
only while a request is genuinely running. `Settings → Accessibility → Reduce motion`
(or Android's animator duration scale) turns all of it off; code checks
`LocalReduceMotion` before every non-essential animation.

## Accessibility

The UI follows the system font scale (text wraps rather than clipping), keeps interactive
targets at 48 dp or larger, gives icons spoken labels, uses logical focus order, and never
encodes status in colour alone. Errors are sentences with optional raw details. Reduce motion
turns off non-essential animation, including navigation cross-fades. Portrait and landscape
both lay out; the library switches between grid and list.

## Adding a source in code

```kotlin
class MySource(private val http: Http) : MediaSource() {
    override val id = "my-source"
    override val name = "My Source"
    override val type = SourceType.MANGA

    override suspend fun browse(catalog: String?, page: Int): List<MediaStub> = ...
    override suspend fun search(query: String, page: Int): List<MediaStub> = ...
    override suspend fun details(key: String): MediaDetails = ...
    override suspend fun units(key: String): List<UnitStub> = ...
    override suspend fun pages(key: String, unitId: String): List<String> = ...   // manga only
    override suspend fun chapterText(key: String, unitId: String): String? = null // text sources
    override suspend fun chapterBody(key: String, unitId: String): String? = null // HTML sources
    override val chaptersAreRemote: Boolean get() = false                         // network text
    override suspend fun stream(key: String, unitId: String, payload: String?): StreamInfo? = null
}
```

Register it in `SourceHub.instance()` and add a `SourceEntity` row. Optional hooks are
ignored by kinds that don't need them, which is why one interface covers a manga scraper,
an EPUB library and a video addon.

## Architecture

```
app/src/main/java/app/allfun/
├── AllFunApp.kt              Application + AppContainer bootstrap
├── MainActivity.kt           edge-to-edge Compose host
├── data/
│   ├── AppContainer.kt       manual DI graph (swap for Hilt later)
│   ├── db/                   Room: media, episodes, progress, sources (schema v4)
│   ├── model/                MediaKind, sources DTOs
│   ├── content/              ContentRepository + ContentModels: the three categories,
│   │                         aggregation, dedupe, caching, pagination, unified search
│   ├── source/               MediaSource + local/Jellyfin/Stremio + SourceHub
│   ├── extension/            ExtensionManager (APKs), ExtensionRepository (indexes),
│   │                         NovelPluginHost (LNReader JS)
│   ├── backup/               JSON export/import of settings, library and progress
│   ├── epub/                 dependency-free EPUB parser (OPF spine, nav/NCX titles, cover)
│   ├── importer/             EPUB import, SAF video tree import
│   ├── repo/                 LibraryRepository (single write path), progress, sources
│   ├── net/                  OkHttp + kotlinx.serialization helpers, UserError mapping
│   ├── update/               GitHub Releases API, version compare, download + SHA-256,
│   │                         APK inspector, PackageInstaller session, notifier, worker
│   └── settings/             DataStore preferences (including preferred sources)
├── ui/
│   ├── AllFunRoot.kt         routes + five-tab bottom bar (sliding pill) + transitions
│   ├── home/ category/ search/ detail/ library/ browse/ extensions/ settings/
│   ├── update/               App Updates screen and its ViewModel
│   ├── reader/               manga (vertical/paged/zoom) and novel (WebView) readers
│   ├── player/               Media3 ExoPlayer
│   └── theme/ components/
└── (top level)               eu/kanade/tachiyomi/** — the host ABI extension APKs link to

app/src/test/java/app/allfun/data/content/   category, dedupe and format unit tests
app/src/test/java/app/allfun/data/source/    Tachiyomi page-conversion unit tests
app/src/test/java/app/allfun/data/update/    version and release-parsing unit tests
app/src/main/assets/lnreader/                offscreen plugin runtime + bundled JS libraries
```

**Unified data model.** One `media` table with a `MediaKind`, an `episodes` table used for
manga chapters, novel chapters *and* TV episodes, and one `progress` row per title.
`position`/`total` mean pages for manga, a 0..1000 scroll fraction for novels and
milliseconds for video — the Continue row only needs the ratio.

**Stable ids.** Media ids are UUIDv3 hashes of `sourceId|sourceKey`, episode ids of
`mediaId|sourceUnitId`, so re-fetching a source never orphans progress.

**Source writes go one way.** A card from a category or from search →
`LibraryRepository.savePreview()` → a `media` row (+episodes) → navigate by media id.
Detail and readers only read the database, so a source disappearing never orphans progress.

**Categories are a view, providers are the storage.** `ContentRepository` turns enabled
`SourceEntity` rows into the three category feeds. It fetches the matching sources in
parallel (bounded to six requests, 15 s per request, 45 s per feed), publishes partial
feeds as answers arrive, merges duplicate titles while keeping every confirmed provider,
and labels failures per provider instead of failing the category. Search fans out to every
compatible source and groups the same way, with a 30 s budget so an unreachable provider
cannot stall the screen. Source confirmation on the detail page uses the same rule: only an
exact title match marks a provider available, and the preferred provider is stored per
title in DataStore.

## Build

Requirements: JDK 17, Android SDK 35.

```powershell
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

`local.properties` must point at your SDK (`sdk.dir=...`); Android Studio writes it for you.
The project is a single `:app` module on purpose for milestone 1 — packages are already
split so `core/data/feature` modules can be extracted without moving logic.

The build on the machine that produced this used a Google Maven mirror because
`dl.google.com` was unreachable; `settings.gradle.kts` lists it as a fallback *after*
`google()`, so normal networks are unaffected. GitHub-hosted runners use the normal
`google()` repository.

### Versioning

Versions are semantic, and the tag is the single source of truth:

| Tag | `versionName` | `versionCode` |
|---|---|---|
| `v0.4.0` | `0.4.0` | `400` |
| `v0.5.0` | `0.5.0` | `500` |
| `v0.5.1` | `0.5.1` | `501` |
| `v0.6.0` | `0.6.0` | `600` |
| `v0.7.0` | `0.7.0` | `700` |
| `v0.7.1` | `0.7.1` | `701` |
| `v1.0.0` | `1.0.0` | `10000` |

`versionCode = major * 10000 + minor * 100 + patch`. The release workflow computes both
from the tag and passes `-PappVersionName` / `-PappVersionCode` to Gradle, so the APK can
never disagree with its release. Local builds fall back to `0.7.1` / `701`.

The first published build was numbered 1.0.0; it has been renumbered and re-published as
0.4.0, the three-category redesign is 0.5.0, the UI/motion redesign is 0.6.0, and the
navigation, library and download rebuild is 0.7.0, and 0.7.1 polishes the bottom bar. The
numbering is now monotonic again (0.4.0 → 0.5.0 → 0.6.0 → 0.7.0 → 0.7.1 → …), so installs upgrade in place.

### Release process

Publishing a version takes one push of a tag; no APK is uploaded by hand.

```bash
git tag -a v0.7.1 -m "AllFun 0.7.1"
git push origin v0.7.1
```

What `.github/workflows/release.yml` does with that push:

1. Validates the tag against `vMAJOR.MINOR.PATCH` and rejects anything else.
2. Runs the unit tests.
3. Builds and signs the release APK with the tag's version.
4. Verifies the artifact with `aapt2` (package `app.allfun`, matching `versionName` and
   `versionCode`) and `apksigner` (a real v2 signature, not the debug key).
5. Writes `AllFun-<version>-release.apk.sha256` in `sha256sum` format.
6. Creates a **draft** GitHub Release. Notes come from `docs/releases/<tag>.md` when that
   file exists; otherwise they are generated from the commits since the previous tag.
7. Uploads the APK and the checksum to the draft, then verifies both assets and re-downloads
   the published checksum to compare it with the freshly built APK.
8. Publishes the release as **latest** and confirms GitHub agrees before finishing.
9. Attaches the signed APK and checksum to the workflow run for 90 days.

A failure before publishing leaves the release as a draft, or creates nothing at all if it
happens earlier — either way nothing becomes public, and the app's `releases/latest`
endpoint keeps pointing at the previous version. Fix the cause, move the tag (or delete it
and tag the fixed commit), and push again; the workflow replaces its own draft. A version
that has already been published is never overwritten: bump the patch number instead.

Optional curated notes live in `docs/releases/<tag>.md` (`docs/releases/v0.7.1.md` is
this release). If that file has no install heading, the workflow appends a short install
block with the asset names and the in-app path.

### Signing

Release builds are signed with one fixed keystore, which is what allows an installed AllFun
to be updated instead of replaced. Certificate SHA-256:

```
77def786ae61b45f030689aa8d41835033aa6d668ef00f00f2f749b5e68868c2   CN=AllFun
```

The identity has been used since the first signed builds (0.2 onward), so APKs from
GitHub update an existing install in place.

Locally, signing reads `keystore.properties` and `keystore/allfun-release.jks` from the
project root. Both are git-ignored and must never be committed. `keystore.properties`
contains:

```properties
storeFile=keystore/allfun-release.jks
storePassword=…
keyAlias=allfun
keyPassword=…
```

CI gets them from repository secrets; no key material is stored in the repository:

| Secret | Contents |
|---|---|
| `KEYSTORE_BASE64` | base64 of `allfun-release.jks` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `allfun` |
| `KEY_PASSWORD` | the key password |

Set them from the project root without printing the values:

```powershell
# base64 the keystore, copy the clipboard value into the secret
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\allfun-release.jks"))
$b64 | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS
gh secret set KEY_PASSWORD
```

The release workflow fails with a named error if any secret is missing, and never echoes
their contents.

**Owner action:** back up `allfun-release.jks` and both passwords somewhere durable (a
password manager plus offline copy). Losing the key means future APKs cannot be installed
as updates over existing ones; recovering would require a new application id or an
uninstall, which erases the library.

### GitHub Actions

`.github/workflows/ci.yml` runs on every push to `main`, on pull requests and on demand:
JDK 17 (Temurin), Android SDK with `platforms;android-35` and `build-tools;35.0.0`,
Gradle with dependency caching, `:app:testDebugUnitTest`, `:app:assembleDebug`, the debug
APK as an artifact, and on failure a job summary plus the test reports. Concurrent runs for
the same ref are cancelled.

`.github/workflows/release.yml` runs on `v*.*.*` tags with `contents: write`. It is never
cancelled mid-run, because a half-published release is worse than a slow one. It uses the
same JDK/SDK/caching setup, computes the version, restores the keystore, tests, builds,
verifies, checksums, drafts, verifies the draft, publishes, and confirms the result.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Release run fails: `Repository secret KEYSTORE_BASE64 is not set` | One of the four signing secrets is missing. Set them as shown under [Signing](#signing). |
| Release run fails at **Verify the APK** with `CN=Android Debug` | The signed build did not receive the keystore; check `KEYSTORE_*` and that the workflow's `keystore.properties` step ran. |
| Tag pushed but no workflow ran | The tag must match `vMAJOR.MINOR.PATCH` exactly (`v0.5.0`, not `0.5.0` or `v0.5`). Tags also have to be pushed explicitly: `git push origin <tag>`. |
| App says "No stable release has been published yet" | The release is still a draft or marked prerelease, or only commits/tags exist. GitHub's `releases/latest` endpoint returns neither. Publish the release. |
| Update check fails with a 403 or rate-limit message | GitHub allows 60 unauthenticated API requests per hour per IP. Checks are cached for 12 hours; try again later or from another network. |
| "Allow Installs" instead of "Install Update" | Android has not granted AllFun the *install unknown apps* app-op. The button opens the system screen; return and install. The confirmation dialog still appears. |
| "That APK is signed by a different key…" | The installed copy came from a debug build or a different keystore. Uninstall once (this erases local data) and install the GitHub release. |
| Download is discarded with a SHA-256 mismatch | The asset did not match its published checksum. Retry; if it repeats, the release itself is wrong — do not bypass the check. |
| Download progress stays at 0% | The asset host is unreachable or the release asset is missing. The error text names the HTTP status. |
| Build prints `R8: An error occurred when parsing kotlin metadata` | R8 in AGP 8.7 is older than Kotlin 2.2. It is a warning, not a failure; the build continues. |
| A source answers with `Content-Encoding: zstd` and the app stops | Fixed in 0.5.0 by keeping `com.squareup.zstd.**` in `proguard-rules.pro`; R8 had stripped the JNI compressor that OkHttp 5 needs. |
| Local build cannot reach `dl.google.com` | The Aliyun Google mirror in `settings.gradle.kts` is already a fallback after `google()`. |

## Not built yet

Honest list, roughly in priority order:

1. **Downloads** — offline chapters/episodes with a queue (`WorkManager`), per-title storage.
   Settings says so instead of showing a dead switch.
2. **Tracking** — AniList/MAL/Shikimori sync for manga and novels, Trakt for video.
3. **Paged EPUB and annotations** — the novel reader is a themed scrollable WebView;
   pagination, highlights and bookmarks are not there yet.
4. **Categories and bulk library actions** — kind filters, search, sort and favourites work;
   user-defined categories and multi-select actions do not.
5. **Declarative sources** — a JSON extension format (`{ "search": "/s?q={query}",
   "selectors": … }`) for sites that need no code, since `MediaSource` is already the
   boundary.
6. **UI and instrumentation tests** — 26 JVM unit tests cover version comparison, release
   parsing, category/dedupe/format mapping and Tachiyomi page conversion. Compose/UI tests,
   Room tests and an on-device instrumentation suite are not written yet; those paths are
   still verified manually on the emulator.
7. **Immersive player** — playback uses Media3's controller; fullscreen/gesture controls and
   background audio are not implemented.

## Legal note

AllFun is a client. It ships no catalogue and no sample media: there are no bundled books,
manga or demo library rows, and every title on the shelf came from a source the user added.
Jellyfin and local files are your own media. Stremio addons and extensions are supplied by
the user; the app only follows their protocols, and it refuses torrent-only streams rather
than embedding a torrent engine.

Demo screenshots for the video path were taken against a **local mock addon** in
[`tools/stremio-mock/`](tools/stremio-mock/): generated posters, one demo film and one demo
series, and a real MP4 test clip (*Big Buck Bunny*, CC BY 3.0, Blender Foundation) with a
WebVTT subtitle — not against a real catalogue.
