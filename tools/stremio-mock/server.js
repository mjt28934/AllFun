// Minimal Stremio addon used to verify AllFun's video path without touching any
// third-party service. It serves one movie and one series, a real MP4 (Range-capable),
// WebVTT subtitles, and proxy headers so the player's header plumbing is exercised.
const http = require('http');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const PORT = 8099;
const HOST = '0.0.0.0';
const MEDIA = path.join(__dirname, 'media');

// ---- tiny PNG encoder (no dependencies) -------------------------------------
const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const typeBuf = Buffer.from(type, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])));
  return Buffer.concat([len, typeBuf, data, crc]);
}

function makePoster(width, height, top, bottom) {
  const stride = width * 3 + 1;
  const raw = Buffer.alloc(stride * height);
  for (let y = 0; y < height; y++) {
    const t = y / (height - 1);
    const r = Math.round(top[0] + (bottom[0] - top[0]) * t);
    const g = Math.round(top[1] + (bottom[1] - top[1]) * t);
    const b = Math.round(top[2] + (bottom[2] - top[2]) * t);
    raw[y * stride] = 0;
    for (let x = 0; x < width; x++) {
      const o = y * stride + 1 + x * 3;
      raw[o] = r;
      raw[o + 1] = g;
      raw[o + 2] = b;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 2;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', zlib.deflateSync(raw)),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

const POSTERS = {
  'allfun-demo-one': makePoster(240, 360, [34, 51, 102], [10, 14, 30]),
  'allfun-demo-two': makePoster(240, 360, [102, 34, 68], [30, 10, 20]),
  'allfun-demo-series': makePoster(240, 360, [24, 82, 74], [8, 26, 24]),
};

// ---- addon data -------------------------------------------------------------
const MOVIES = [
  {
    id: 'allfun-demo-one',
    type: 'movie',
    name: 'The Lantern Library',
    description:
      'A generated demo film: a night librarian discovers that every returned book replays one hour of its reader\'s life.',
    releaseInfo: '2024',
    genres: ['Drama', 'Fantasy'],
    runtime: '12 min',
  },
  {
    id: 'allfun-demo-two',
    type: 'movie',
    name: 'Signal from Pier Nine',
    description:
      'A generated demo film: two radio operators on a decommissioned pier keep receiving tomorrow\'s shipping forecast.',
    releaseInfo: '2023',
    genres: ['Mystery', 'Sci-Fi'],
    runtime: '9 min',
  },
];

const SERIES = {
  id: 'allfun-demo-series',
  type: 'series',
  name: 'Paper Harbour',
  description:
    'A generated demo series about a coastal town whose map redraws itself every spring.',
  releaseInfo: '2025',
  genres: ['Adventure', 'Drama'],
  videos: [
    { id: 'allfun-demo-series:1:1', season: 1, episode: 1, name: 'Low Tide' },
    { id: 'allfun-demo-series:1:2', season: 1, episode: 2, name: 'The Second Jetty' },
    { id: 'allfun-demo-series:2:1', season: 2, episode: 1, name: 'Fresh Ink' },
    { id: 'allfun-demo-series:2:2', season: 2, episode: 2, name: 'Chart Room' },
  ],
};

const MANIFEST = {
  id: 'app.allfun.mock',
  version: '1.0.0',
  name: 'AllFun Mock Addon',
  description: 'Local addon used to verify catalog, meta, stream and subtitle handling.',
  logo: 'http://10.0.2.2:8099/poster/allfun-demo-one.png',
  resources: ['catalog', 'meta', 'stream', 'subtitles'],
  types: ['movie', 'series'],
  catalogs: [
    {
      type: 'movie',
      id: 'allfun-movies',
      name: 'AllFun Movies',
      extra: [
        { name: 'search', isRequired: false },
        { name: 'skip', isRequired: false },
      ],
    },
    {
      type: 'series',
      id: 'allfun-series',
      name: 'AllFun Series',
      extra: [{ name: 'search', isRequired: false }],
    },
  ],
};

function metaStub(item) {
  return {
    id: item.id,
    type: item.type,
    name: item.name,
    poster: `http://10.0.2.2:${PORT}/poster/${item.id}.png`,
    releaseInfo: item.releaseInfo,
    description: item.description,
  };
}

function sendJson(res, status, value) {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
  });
  res.end(body);
}

function catalogFor(type, id) {
  if (type === 'movie' && id === 'allfun-movies') return MOVIES;
  if (type === 'series' && id === 'allfun-series') return [SERIES];
  return null;
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const parts = url.pathname.split('/').filter(Boolean);
  const tag = req.headers['x-allfun-test'] ? ` [X-AllFun-Test=${req.headers['x-allfun-test']}]` : '';
  console.log(`${req.method} ${url.pathname}${url.search}${tag}`);

  if (url.pathname === '/manifest.json') return sendJson(res, 200, MANIFEST);

  if (parts[0] === 'poster' && parts[1]) {
    const id = parts[1].replace(/\.png$/, '');
    const poster = POSTERS[id];
    if (!poster) return sendJson(res, 404, { error: 'no poster' });
    res.writeHead(200, {
      'Content-Type': 'image/png',
      'Content-Length': poster.length,
      'Cache-Control': 'public, max-age=3600',
    });
    return res.end(poster);
  }

  // /catalog/{type}/{id}/{extra}.json  or  /catalog/{type}/{id}.json
  if (parts[0] === 'catalog') {
    const type = parts[1];
    const id = (parts[2] || '').replace(/\.json$/, '');
    const extra = (parts[3] || '').replace(/\.json$/, '');
    const items = catalogFor(type, id);
    if (!items) return sendJson(res, 404, { err: 'unknown catalog' });
    let metas = items.map(metaStub);
    const params = new URLSearchParams(extra);
    const search = params.get('search');
    if (search) {
      const needle = search.toLowerCase();
      metas = metas.filter((m) => m.name.toLowerCase().includes(needle));
    }
    const skip = Number(params.get('skip') || 0);
    metas = metas.slice(skip, skip + 100);
    return sendJson(res, 200, { metas });
  }

  if (parts[0] === 'meta') {
    const type = parts[1];
    const id = (parts[2] || '').replace(/\.json$/, '');
    const movie = MOVIES.find((m) => m.id === id);
    if (movie) {
      return sendJson(res, 200, {
        meta: { ...metaStub(movie), genres: movie.genres, runtime: movie.runtime },
      });
    }
    if (SERIES.id === id) {
      return sendJson(res, 200, {
        meta: {
          ...metaStub(SERIES),
          genres: SERIES.genres,
          videos: SERIES.videos,
        },
      });
    }
    return sendJson(res, 404, { err: 'unknown meta' });
  }

  if (parts[0] === 'stream') {
    const type = parts[1];
    const id = (parts[2] || '').replace(/\.json$/, '');
    const known = MOVIES.some((m) => m.id === id) || id.startsWith(SERIES.id);
    if (!known) return sendJson(res, 200, { streams: [] });
    return sendJson(res, 200, {
      streams: [
        {
          name: 'Mock',
          title: '720p direct stream',
          url: `http://10.0.2.2:${PORT}/media/sample.mp4`,
          behaviorHints: {
            notWebReady: false,
            proxyHeaders: { request: { 'X-AllFun-Test': 'stremio-proxy-header' } },
          },
          subtitles: [
            {
              id: 'en',
              url: `http://10.0.2.2:${PORT}/media/sample.vtt`,
              lang: 'en',
            },
          ],
        },
        { name: 'Torrent only', infoHash: 'deadbeefdeadbeefdeadbeefdeadbeefdeadbeef' },
      ],
    });
  }

  if (parts[0] === 'subtitles') {
    const type = parts[1];
    const id = (parts[2] || '').replace(/\.json$/, '');
    return sendJson(res, 200, {
      subtitles: [
        {
          id: 'en',
          url: `http://10.0.2.2:${PORT}/media/sample.vtt`,
          lang: 'en',
        },
      ],
    });
  }

  if (url.pathname.startsWith('/media/')) {
    const name = path.basename(url.pathname);
    const file = path.join(MEDIA, name);
    if (!fs.existsSync(file)) return sendJson(res, 404, { err: 'missing media' });
    const stat = fs.statSync(file);
    const type = name.endsWith('.vtt') ? 'text/vtt; charset=utf-8' : 'video/mp4';
    const range = req.headers.range;
    if (range) {
      const match = /bytes=(\d*)-(\d*)/.exec(range);
      const start = match && match[1] ? Number(match[1]) : 0;
      const end = match && match[2] ? Number(match[2]) : stat.size - 1;
      res.writeHead(206, {
        'Content-Type': type,
        'Content-Range': `bytes ${start}-${end}/${stat.size}`,
        'Accept-Ranges': 'bytes',
        'Content-Length': end - start + 1,
      });
      return fs.createReadStream(file, { start, end }).pipe(res);
    }
    res.writeHead(200, {
      'Content-Type': type,
      'Content-Length': stat.size,
      'Accept-Ranges': 'bytes',
    });
    return fs.createReadStream(file).pipe(res);
  }

  sendJson(res, 404, { err: 'not found' });
});

server.listen(PORT, HOST, () => {
  console.log(`AllFun mock addon listening on http://${HOST}:${PORT}`);
});
