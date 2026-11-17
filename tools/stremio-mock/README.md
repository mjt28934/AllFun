# Stremio mock addon

A ~300-line Node script that implements the parts of the Stremio addon protocol AllFun
uses, so the video path can be verified without touching a third-party service or shipping
any catalogue. Everything it serves is generated: two demo films, one two-season demo
series, gradient posters drawn by its own PNG encoder, and a public-domain test clip with
WebVTT subtitles.

## Run it

```powershell
cd tools/stremio-mock
# one-time: the only binary asset, a 991 KB public-domain clip
Invoke-WebRequest -Uri https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4 -OutFile media/sample.mp4
node server.js
```

Then add `http://10.0.2.2:8099/manifest.json` as a Stremio source in AllFun
(`10.0.2.2` is the host machine as seen from the Android emulator; on a physical device use
the machine's LAN address).

## What it exercises

| Feature | How the mock exercises it |
|---|---|
| Manifest | `resources` = catalog/meta/stream/subtitles, `types` = movie/series |
| Catalog `extra` | movies declare `search` + `skip`; series declare only `search` |
| Catalog paging | `skip` is treated as an **item offset**, not a page number |
| Meta | videos with `id` `<seriesId>:<season>:<episode>` |
| Stream | one direct HTTP URL + one torrent-only result (AllFun must refuse the latter) |
| Proxy headers | `behaviorHints.proxyHeaders.request` sends `X-AllFun-Test` on the MP4 *and* the VTT; the server logs it |
| Subtitles | inline `subtitles[]` on the stream, plus a `subtitles` resource endpoint |
| Range requests | `206 Partial Content`, so the player seeks instead of downloading |

The server prints every request it serves, including the proxy header, which is how the
emulator runs were checked.
