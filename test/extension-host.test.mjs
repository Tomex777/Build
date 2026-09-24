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
