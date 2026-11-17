/*
 * LNReader plugin runtime.
 *
 * LNReader plugins are CommonJS bundles that require a few `@libs/*` modules and
 * `cheerio`. This file provides those modules on top of the bundled libraries and
 * forwards every network call to the Android host, which is not subject to the
 * WebView's CORS rules.
 *
 * The host calls __allfunLoadPlugin(source) once, then __allfunCall(method, args)
 * for each operation and reads the resolved JSON through AllFunBridge.
 */
(function () {
  'use strict';

  var DEFAULT_COVER =
    'https://github.com/LNReader/lnreader-plugins/blob/main/icons/src/coverNotAvailable.jpg?raw=true';

  var currentPlugin = null;
  var currentPluginId = '';
  var fetchSeq = 0;
  var pendingFetches = {};
  var pluginStorage = {};

  function absUrl(url) {
    if (!url) return url;
    var value = String(url);
    if (/^[a-z][a-z0-9+.-]*:/i.test(value)) return value;
    var site = (currentPlugin && currentPlugin.site) || '';
    if (value.indexOf('//') === 0) return 'https:' + value;
    if (value.charAt(0) === '/') return site.replace(/\/+$/, '') + value;
    return site.replace(/\/+$/, '') + '/' + value.replace(/^\/+/, '');
  }

  function headerObject(headers) {
    var out = {};
    if (!headers) return out;
    if (typeof Headers !== 'undefined' && headers instanceof Headers) {
      headers.forEach(function (value, key) {
        out[key] = value;
      });
      return out;
    }
    Object.keys(headers).forEach(function (key) {
      var value = headers[key];
      if (value !== undefined && value !== null) out[key] = String(value);
    });
    return out;
  }

  function makeResponse(payload) {
    var headers = payload.headers || {};
    return {
      ok: payload.status >= 200 && payload.status < 300,
      status: payload.status,
      statusText: payload.statusText || '',
      url: payload.url || '',
      redirected: !!payload.redirected,
      headers: {
        get: function (name) {
          var key = String(name).toLowerCase();
          return Object.prototype.hasOwnProperty.call(headers, key)
            ? headers[key]
            : null;
        },
      },
      text: function () {
        return Promise.resolve(payload.body || '');
      },
      json: function () {
        return Promise.resolve(JSON.parse(payload.body || 'null'));
      },
      arrayBuffer: function () {
        var base64 = payload.bodyBase64 || '';
        if (!base64) {
          return Promise.resolve(new TextEncoder().encode(payload.body || '').buffer);
        }
        var binary = atob(base64);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return Promise.resolve(bytes.buffer);
      },
      __payload: payload,
    };
  }

  function bridgeFetch(url, init, binary) {
    return new Promise(function (resolve, reject) {
      var id = 'f' + ++fetchSeq;
      pendingFetches[id] = { resolve: resolve, reject: reject };
      var headers = {
        Connection: 'keep-alive',
        Accept: '*/*',
        'Accept-Language': '*',
        'Sec-Fetch-Mode': 'cors',
        'User-Agent':
          'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36',
      };
      if (init && init.headers) {
        var supplied = headerObject(init.headers);
        Object.keys(supplied).forEach(function (key) {
          headers[key] = supplied[key];
        });
      }
      var options = {
        method: (init && init.method) || 'GET',
        headers: headers,
        body:
          init && init.body !== undefined && init.body !== null
            ? String(init.body)
            : null,
        binary: !!binary,
      };
      try {
        AllFunBridge.fetch(id, absUrl(url), JSON.stringify(options));
      } catch (error) {
        delete pendingFetches[id];
        console.log('[allfun] bridge fetch threw: ' + error);
        reject(error);
      }
    });
  }

  window.__allfunFetchResolve = function (id, payloadJson) {
    var pending = pendingFetches[id];
    if (!pending) return;
    delete pendingFetches[id];
    try {
      var payload =
        typeof payloadJson === 'string' ? JSON.parse(payloadJson) : payloadJson;
      if (payload.error) {
        console.log('[allfun] fetch error: ' + payload.error);
        pending.reject(new Error(payload.error));
      } else pending.resolve(makeResponse(payload));
    } catch (error) {
      pending.reject(error);
    }
  };

  async function fetchApi(url, init) {
    return bridgeFetch(url, init, false);
  }

  async function fetchHtml(url, init) {
    var response = await bridgeFetch(url, init, false);
    return CheerioLib.load(await response.text());
  }

  async function fetchFile(url, init) {
    try {
      var response = await bridgeFetch(url, init, true);
      if (!response.ok) return '';
      var payload = response.__payload;
      return payload && payload.bodyBase64 ? payload.bodyBase64 : '';
    } catch (error) {
      return '';
    }
  }

  async function fetchText(url, init, encoding) {
    try {
      if (!encoding || /^utf-?8$/i.test(encoding)) {
        var response = await bridgeFetch(url, init, false);
        return response.ok ? await response.text() : '';
      }
      var binaryResponse = await bridgeFetch(url, init, true);
      if (!binaryResponse.ok) return '';
      var buffer = await binaryResponse.arrayBuffer();
      return new TextDecoder(encoding).decode(buffer);
    } catch (error) {
      return '';
    }
  }

  var filterTypes = {
    TextInput: 'Text',
    Picker: 'Picker',
    CheckboxGroup: 'Checkbox',
    Switch: 'Switch',
    ExcludableCheckboxGroup: 'XCheckbox',
  };

  var novelStatus = {
    Unknown: 'Unknown',
    Ongoing: 'Ongoing',
    Completed: 'Completed',
    Licensed: 'Licensed',
    PublishingFinished: 'Publishing Finished',
    Cancelled: 'Cancelled',
    OnHiatus: 'On Hiatus',
    STUB: 'STUB',
    Inactive: 'Inactive',
  };

  function storageKey(key) {
    return currentPluginId + ':' + key;
  }

  // LNReader's `storage` is synchronous, so keep the values in memory and mirror
  // writes to the Android side for the next launch.
  var storage = {
    set: function (key, value, expires) {
      pluginStorage[key] = {
        created: Date.now(),
        value: value,
        expires:
          expires instanceof Date ? expires.getTime() : expires || undefined,
      };
      try {
        AllFunBridge.storageSet(
          storageKey(key),
          JSON.stringify(pluginStorage[key])
        );
      } catch (error) {
        /* storage is best-effort */
      }
    },
    get: function (key, raw) {
      var item = pluginStorage[key];
      if (item && item.expires && Date.now() > item.expires) {
        storage.delete(key);
        return undefined;
      }
      return raw ? item : item && item.value;
    },
    getAllKeys: function () {
      return Object.keys(pluginStorage);
    },
    delete: function (key) {
      delete pluginStorage[key];
      try {
        AllFunBridge.storageSet(storageKey(key), null);
      } catch (error) {
        /* storage is best-effort */
      }
    },
    clearAll: function () {
      Object.keys(pluginStorage).forEach(function (key) {
        storage.delete(key);
      });
    },
  };

  var localStorageShim = {
    get: function () {
      return {};
    },
  };
  var sessionStorageShim = {
    get: function () {
      return {};
    },
  };

  var shims = {
    '@libs/fetch': {
      fetchApi: fetchApi,
      fetchHtml: fetchHtml,
      fetchText: fetchText,
      fetchFile: fetchFile,
    },
    '@libs/filterInputs': { FilterTypes: filterTypes },
    '@libs/novelStatus': { NovelStatus: novelStatus },
    '@libs/storage': {
      storage: storage,
      localStorage: localStorageShim,
      sessionStorage: sessionStorageShim,
    },
    '@libs/defaultCover': { defaultCover: DEFAULT_COVER },
    '@libs/aes': { gcm: globalThis.NobleGcm },
    '@libs/isAbsoluteUrl': {
      isUrlAbsolute: function (url) {
        return /^[a-z][a-z0-9+.-]*:\/\//i.test(String(url));
      },
    },
  };

  function requireModule(name) {
    if (name === 'cheerio') return CheerioLib;
    if (name === 'dayjs') return DayjsLib;
    if (name === 'htmlparser2') return Htmlparser2Lib;
    if (Object.prototype.hasOwnProperty.call(shims, name)) return shims[name];
    throw new Error('Unknown module in plugin: ' + name);
  }

  window.__allfunLoadPlugin = function (source) {
    var module = { exports: {} };
    var factory = new Function(
      'require',
      'module',
      'exports',
      source + '\n//# sourceURL=lnreader-plugin.js'
    );
    factory(requireModule, module, module.exports);
    var exported = module.exports;
    var plugin = exported && exported.default ? exported.default : exported;
    if (!plugin || typeof plugin !== 'object') {
      throw new Error('Plugin did not export a plugin object');
    }
    currentPlugin = plugin;
    currentPluginId = String(plugin.id || '');
    pluginStorage = {};
    console.log('[allfun] loaded plugin ' + currentPluginId + ' v' + plugin.version);
    return {
      id: plugin.id || '',
      name: plugin.name || '',
      site: plugin.site || '',
      version: plugin.version || '',
      lang: plugin.lang || '',
      hasFilters: !!plugin.filters,
    };
  };

  function callMethod(method, args) {
    var plugin = currentPlugin;
    if (!plugin) throw new Error('No plugin loaded');
    var page = args[0] || 1;

    switch (method) {
      case 'info':
        return Promise.resolve({
          id: plugin.id || '',
          name: plugin.name || '',
          site: plugin.site || '',
          version: plugin.version || '',
          lang: plugin.lang || '',
          hasFilters: !!plugin.filters,
        });
      case 'popular': {
        if (typeof plugin.popularNovels !== 'function') return Promise.resolve([]);
        return Promise.resolve(
          plugin.popularNovels(page, { filters: plugin.filters || {} })
        ).then(function (items) {
          return items || [];
        });
      }
      case 'search': {
        var query = args[0] || '';
        var searchPage = args[1] || 1;
        if (typeof plugin.searchNovels !== 'function') return Promise.resolve([]);
        return Promise.resolve(
          plugin.searchNovels(query, searchPage, plugin.filters || {})
        ).then(function (items) {
          return items || [];
        });
      }
      case 'novel': {
        var novelPath = args[0] || '';
        var parser =
          typeof plugin.parseNovel === 'function'
            ? plugin.parseNovel
            : plugin.parseNovelAndChapters;
        if (typeof parser !== 'function') {
          throw new Error('Plugin cannot parse novels');
        }
        return Promise.resolve(parser.call(plugin, novelPath)).then(function (novel) {
          var result = novel || {};
          var chapters = Array.isArray(result.chapters) ? result.chapters : [];
          return {
            name: result.name || 'Untitled',
            path: result.path || novelPath,
            cover: result.cover || null,
            summary: result.summary || null,
            author: result.author || null,
            artist: result.artist || null,
            status: result.status || null,
            genres: result.genres || null,
            chapters: chapters.map(function (chapter) {
              return {
                name: chapter.name || '',
                path: chapter.path || '',
                releaseTime: chapter.releaseTime || null,
                chapterNumber:
                  chapter.chapterNumber === undefined
                    ? null
                    : chapter.chapterNumber,
              };
            }),
          };
        });
      }
      case 'chapter': {
        if (typeof plugin.parseChapter !== 'function') {
          throw new Error('Plugin cannot parse chapters');
        }
        return Promise.resolve(plugin.parseChapter(args[0] || '')).then(
          function (html) {
            return html || '';
          }
        );
      }
      case 'filters':
        return Promise.resolve(plugin.filters || {});
      default:
        throw new Error('Unknown method: ' + method);
    }
  }

  window.__allfunCall = function (method, args) {
    try {
      console.log('[allfun] call ' + method);
      return callMethod(method, args || []);
    } catch (error) {
      console.log('[allfun] call ' + method + ' threw: ' + (error && error.message));
      return Promise.reject(error);
    }
  };

  if (window.AllFunBridge && AllFunBridge.onReady) {
    AllFunBridge.onReady();
  }
})();
