import test from "node:test";
import assert from "node:assert/strict";
import { ExtensionHost, parseYouTubeVideoUrl, youtubeEmbedUrl } from "../src/extension-host.mjs";

const descriptor = (id, mediaTypes = ["anime"], extra = {}) => ({
  id, name: id, mediaTypes, enabled: true, ...extra
});
const adapter = (id, search, extra = {}) => ({
  descriptor: descriptor(id, ["anime"], extra),
  search
});

test("invalid and unsupported requests do not call providers", async () => {
  let called = false;
  const host = new ExtensionHost([adapter("alpha", async () => { called = true; return []; })]);
  assert.equal((await host.search("anime", "  ")).status, "invalid-query");
  assert.equal((await host.search("unknown", "Naruto")).status, "unsupported-media");
  assert.equal(called, false);
});

test("default provider runs before priority fallbacks", async () => {
  const order = [];
  const host = new ExtensionHost([
    adapter("fast-fallback", async () => { order.push("fallback"); return [{ title: "Found" }]; }, { priority: 1 }),
    adapter("preferred", async () => { order.push("preferred"); return []; }, { priority: 80 })
  ], { anime: "preferred" });
  const result = await host.search("anime", "Example");
  assert.deepEqual(order, ["preferred", "fallback"]);
  assert.equal(result.status, "matched");
  assert.equal(result.items[0].extensionId, "fast-fallback");
});

test("normalizes duplicate results and drops malformed entries", async () => {
  const host = new ExtensionHost([adapter("alpha", async () => [
    { title: " Example  Show ", year: 2024 },
    { title: "example show", year: 2024 },
    { title: "" },
    null
  ])]);
  const result = await host.search("anime", " Example ");
  assert.equal(result.items.length, 1);
  assert.equal(result.items[0].title, "Example  Show");
});

test("provider exceptions and malformed payloads fall through", async () => {
  const host = new ExtensionHost([
    adapter("broken", async () => { throw new Error("network down"); }, { priority: 1 }),
    adapter("malformed", async () => ({ title: "not a list" }), { priority: 2 }),
    adapter("good", async () => [{ title: "Good result" }], { priority: 3 })
  ]);
  const result = await host.search("anime", "query");
  assert.equal(result.status, "matched");
  assert.deepEqual(result.attempts.map(item => item.status), ["error", "error", "matched"]);
});

test("timeouts fall through to the next extension", async () => {
  const host = new ExtensionHost([
    adapter("slow", () => new Promise(() => {}), { priority: 1 }),
    adapter("ready", async () => [{ title: "Ready" }], { priority: 2 })
  ]);
  const result = await host.search("anime", "query", { timeoutMs: 5 });
  assert.equal(result.status, "matched");
  assert.equal(result.attempts[0].status, "timeout");
  assert.equal(result.attempts[0].code, "EXTENSION_TIMEOUT");
});

test("a disabled or incompatible extension is not selected", async () => {
  const host = new ExtensionHost([
    { descriptor: { ...descriptor("disabled"), enabled: false }, search: async () => [{ title: "No" }] },
    { descriptor: descriptor("music-only", ["music"]), search: async () => [{ title: "No" }] }
  ]);
  assert.equal((await host.search("anime", "query")).status, "unavailable");
});

test("duplicate extension ids and invalid defaults are rejected", () => {
  assert.throws(() => new ExtensionHost([adapter("same", async () => []), adapter("same", async () => [])]), /Duplicate extension id/);
  assert.throws(() => new ExtensionHost([adapter("alpha", async () => [])], { anime: "missing" }), /Unknown default extension/);
});

test("cancellation stops fallback execution", async () => {
  const controller = new AbortController();
  const host = new ExtensionHost([
    adapter("first", async () => { controller.abort(new Error("user cancelled")); throw new Error("cancel"); }, { priority: 1 }),
    adapter("second", async () => [{ title: "must not run" }], { priority: 2 })
  ]);
  const result = await host.search("anime", "query", { signal: controller.signal });
  assert.equal(result.status, "cancelled");
  assert.equal(result.items.length, 0);
});

test("YouTube links are validated and embedded without download endpoints", () => {
  assert.equal(parseYouTubeVideoUrl("https://youtu.be/dQw4w9WgXcQ"), "dQw4w9WgXcQ");
  assert.equal(parseYouTubeVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=12"), "dQw4w9WgXcQ");
  assert.equal(parseYouTubeVideoUrl("https://youtube.com/shorts/dQw4w9WgXcQ"), "dQw4w9WgXcQ");
  assert.equal(parseYouTubeVideoUrl("https://notyoutube.com/watch?v=dQw4w9WgXcQ"), null);
  assert.equal(parseYouTubeVideoUrl("https://youtube.com/watch?v=short"), null);
  assert.equal(youtubeEmbedUrl("https://youtu.be/dQw4w9WgXcQ").includes("youtube-nocookie.com/embed/"), true);
  assert.equal(youtubeEmbedUrl("https://example.com/video"), null);
});


test("YouTube search URL is constrained to embeddable video results", async () => {
  const { youtubeSearchUrl } = await import("../src/extension-host.mjs");
  const url = new URL(youtubeSearchUrl("  artist song  "));
  assert.equal(url.origin, "https://www.googleapis.com");
  assert.equal(url.pathname, "/youtube/v3/search");
  assert.equal(url.searchParams.get("q"), "artist song");
  assert.equal(url.searchParams.get("type"), "video");
  assert.equal(url.searchParams.get("videoEmbeddable"), "true");
  assert.equal(url.searchParams.get("videoSyndicated"), "true");
  assert.equal(youtubeSearchUrl("  "), null);
});

test("YouTube search results discard malformed IDs and unsafe thumbnails", async () => {
  const { normalizeYouTubeSearchPayload } = await import("../src/extension-host.mjs");
  const results = normalizeYouTubeSearchPayload({ items: [
    { id: { videoId: "dQw4w9WgXcQ" }, snippet: { title: " Song ", channelTitle: "Artist", thumbnails: { medium: { url: "https://i.ytimg.com/vi/x/mqdefault.jpg" } } } },
    { id: { videoId: "bad" }, snippet: { title: "Bad ID" } },
    { id: { videoId: "dQw4w9WgXcQ" }, snippet: { title: " " } }
  ] });
  assert.equal(results.length, 1);
  assert.equal(results[0].title, "Song");
  assert.equal(results[0].thumbnail.startsWith("https://i.ytimg.com/"), true);
  const unsafe = normalizeYouTubeSearchPayload({ items: [
    { id: { videoId: "dQw4w9WgXcQ" }, snippet: { title: "Song", thumbnails: { medium: { url: "https://attacker.example/image.jpg" } } } }
  ] });
  assert.equal(unsafe[0].thumbnail, "");
});

test("YouTube search rejects oversized queries and deduplicates video IDs", async () => {
  const { youtubeSearchUrl, normalizeYouTubeSearchPayload } = await import("../src/extension-host.mjs");
  assert.equal(youtubeSearchUrl("x".repeat(501)), null);
  const results = normalizeYouTubeSearchPayload({ items: [
    { id: { videoId: "dQw4w9WgXcQ" }, snippet: { title: "First result" } },
    { id: { videoId: "dQw4w9WgXcQ" }, snippet: { title: "Duplicate result" } }
  ] });
  assert.equal(results.length, 1);
  assert.equal(results[0].title, "First result");
});

test("YouTube adapter reports key, quota, and network errors without leaking credentials", async () => {
  const { createYouTubeMusicExtension } = await import("../src/extensions/youtube-music.mjs");
  const missingKey = createYouTubeMusicExtension({ getApiKey: () => "" });
  await assert.rejects(() => missingKey.search({ query: "song" }), error => error.code === "YOUTUBE_KEY_REQUIRED");

  let request;
  const working = createYouTubeMusicExtension({
    getApiKey: () => "test-key",
    fetchImpl: async (url, options) => {
      request = { url: new URL(url), options };
      return {
        ok: true,
        json: async () => ({ items: [
          { id: { videoId: "dQw4w9WgXcQ" }, snippet: { title: "Song", channelTitle: "Artist" } }
        ] })
      };
    }
  });
  const results = await working.search({ query: "artist song", signal: new AbortController().signal });
  assert.equal(results.length, 1);
  assert.equal(request.url.searchParams.has("key"), false);
  assert.equal(request.options.headers["X-Goog-Api-Key"], "test-key");
  assert.equal(request.options.cache, "no-store");

  const quota = createYouTubeMusicExtension({
    getApiKey: () => "test-key",
    fetchImpl: async () => ({ ok: false, status: 403, json: async () => ({ error: { errors: [{ reason: "quotaExceeded" }] } }) })
  });
  await assert.rejects(() => quota.search({ query: "song" }), error => error.code === "YOUTUBE_QUOTA");

  const offline = createYouTubeMusicExtension({ getApiKey: () => "test-key", fetchImpl: async () => { throw new Error("offline"); } });
  await assert.rejects(() => offline.search({ query: "song" }), error => error.code === "NETWORK_UNAVAILABLE");
});

test("the three catalog slots are separate, correctly scoped, and disabled", async () => {
  const weeb = await import("../src/extensions/weeb-central.mjs");
  const tfpdl = await import("../src/extensions/tfpdl.mjs");
  const subsplease = await import("../src/extensions/subsplease.mjs");
  assert.equal(weeb.descriptor.id, "weeb-central");
  assert.deepEqual(weeb.descriptor.mediaTypes, ["manga"]);
  assert.equal(weeb.descriptor.enabled, false);
  assert.deepEqual(tfpdl.descriptor.mediaTypes, ["movie", "tv"]);
  assert.equal(tfpdl.descriptor.enabled, false);
  assert.deepEqual(subsplease.descriptor.mediaTypes, ["anime"]);
  assert.equal(subsplease.descriptor.enabled, false);
  const host = new ExtensionHost([
    { descriptor: weeb.descriptor, search: weeb.search },
    { descriptor: tfpdl.descriptor, search: tfpdl.search },
    { descriptor: subsplease.descriptor, search: subsplease.search }
  ]);
  for (const [type, query] of [["manga", "Example"], ["movie", "Example"], ["tv", "Example"], ["anime", "Example"]]) {
    assert.equal((await host.search(type, query)).status, "unavailable");
  }
});


test("result identity is collision-safe when metadata contains separators", async () => {
  const host = new ExtensionHost([adapter("collision", async () => [
    { title: "a|b", year: "c" },
    { title: "a", year: "b|c" }
  ])]);
  const result = await host.search("anime", "query");
  assert.equal(result.items.length, 2);
});

test("equal-priority providers preserve registration order", async () => {
  const order = [];
  const host = new ExtensionHost([
    adapter("registered-first", async () => { order.push("first"); return []; }, { priority: 20 }),
    adapter("registered-second", async () => { order.push("second"); return [{ title: "Found" }]; }, { priority: 20 })
  ]);
  const result = await host.search("anime", "query");
  assert.deepEqual(order, ["first", "second"]);
  assert.equal(result.items[0].extensionId, "registered-second");
});


test("provider error codes survive fallback without exposing provider messages", async () => {
  const coded = Object.assign(new Error("secret provider detail"), { code: "PROVIDER_AUTH_REQUIRED" });
  const host = new ExtensionHost([
    adapter("coded", async () => { throw coded; }, { priority: 1 }),
    adapter("empty", async () => [], { priority: 2 })
  ]);
  const result = await host.search("anime", "query");
  assert.equal(result.status, "failed");
  assert.deepEqual(result.attempts, [
    { extensionId: "coded", status: "error", code: "PROVIDER_AUTH_REQUIRED" },
    { extensionId: "empty", status: "empty" }
  ]);
  assert.equal("message" in result.attempts[0], false);
});


test("AniList catalog uses official GraphQL metadata search without media delivery", async () => {
  const { createAniListCatalogExtension } = await import("../src/extensions/anilist-catalog.mjs");
  let request;
  const extension = createAniListCatalogExtension({
    fetchImpl: async (url, options) => {
      request = { url, options, body: JSON.parse(options.body) };
      return {
        ok: true,
        json: async () => ({ data: { Page: { media: [
          { id: 1, type: "ANIME", title: { english: "Cowboy Bebop", romaji: "Cowboy Bebop" }, startDate: { year: 1998 }, episodes: 26, chapters: null, status: "FINISHED", coverImage: { medium: "https://s4.anilist.co/file/anilistcdn/media/anime/cover/medium/x1.jpg" } },
          { id: 1, type: "ANIME", title: { english: "Duplicate" } },
          { id: -1, type: "ANIME", title: { english: "Malformed" } }
        ] } } })
      };
    }
  });
  const result = await extension.search({ query: " Cowboy Bebop ", mediaType: "anime", signal: new AbortController().signal });
  assert.equal(request.url, "https://graphql.anilist.co");
  assert.equal(request.options.method, "POST");
  assert.equal(request.options.cache, "no-store");
  assert.equal(request.body.variables.search, "Cowboy Bebop");
  assert.equal(request.body.variables.type, "ANIME");
  assert.match(request.body.query, /isAdult:\s*false/);
  assert.equal(result.length, 1);
  assert.equal(result[0].title, "Cowboy Bebop");
  assert.equal(result[0].mediaType, "anime");
  assert.equal(result[0].episodes, 26);
  assert.equal(result[0].url, "https://anilist.co/anime/1");
  assert.equal("streamUrl" in result[0], false);
  assert.equal("downloadUrl" in result[0], false);
});

test("AniList catalog rejects unsupported, oversized, rate-limited, and unsafe results", async () => {
  const { createAniListCatalogExtension, normalizeAniListPayload } = await import("../src/extensions/anilist-catalog.mjs");
  const unused = createAniListCatalogExtension({ fetchImpl: async () => { throw new Error("must not call"); } });
  await assert.rejects(() => unused.search({ query: "x".repeat(201), mediaType: "anime" }), error => error.code === "ANILIST_QUERY_INVALID");
  await assert.rejects(() => unused.search({ query: "title", mediaType: "movie" }), error => error.code === "ANILIST_MEDIA_UNSUPPORTED");

  const limited = createAniListCatalogExtension({
    fetchImpl: async () => ({ ok: false, status: 429, json: async () => ({}) })
  });
  await assert.rejects(() => limited.search({ query: "title", mediaType: "manga" }), error => error.code === "ANILIST_RATE_LIMIT");

  const normalized = normalizeAniListPayload({ data: { Page: { media: [
    { id: 2, type: "MANGA", title: { romaji: "Safe title" }, coverImage: { medium: "https://attacker.example/cover.jpg" } }
  ] } } });
  assert.equal(normalized[0].thumbnail, "");
  assert.equal(normalized[0].url, "https://anilist.co/manga/2");
});
