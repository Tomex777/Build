export const MEDIA_TYPES = Object.freeze(["anime", "movie", "tv", "manga", "music"]);

export function validateDescriptor(descriptor) {
  if (!descriptor || typeof descriptor !== "object") throw new TypeError("Extension descriptor is required");
  if (typeof descriptor.id !== "string" || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(descriptor.id)) {
    throw new TypeError("Extension id must be a lowercase slug");
  }
  if (typeof descriptor.name !== "string" || !descriptor.name.trim()) throw new TypeError("Extension name is required");
  if (!Array.isArray(descriptor.mediaTypes) || descriptor.mediaTypes.length === 0) {
    throw new TypeError("At least one supported media type is required");
  }
  for (const type of descriptor.mediaTypes) {
    if (!MEDIA_TYPES.includes(type)) throw new TypeError("Unsupported media type: " + type);
  }
  if (descriptor.enabled !== undefined && typeof descriptor.enabled !== "boolean") {
    throw new TypeError("enabled must be a boolean");
  }
  return Object.freeze({
    id: descriptor.id,
    name: descriptor.name.trim(),
    mediaTypes: Object.freeze([...new Set(descriptor.mediaTypes)]),
    enabled: descriptor.enabled !== false,
    priority: Number.isFinite(descriptor.priority) ? descriptor.priority : 100
  });
}

function normalizeTitle(value) {
  return String(value || "").normalize("NFKC").trim().toLowerCase().replace(/\s+/g, " ");
}

function resultKey(item) {
  return JSON.stringify([normalizeTitle(item.title), item.year ?? "", item.season ?? "", item.episode ?? ""]);
}

function raceWithTimeout(operation, milliseconds, signal) {
  const controller = new AbortController();
  return new Promise((resolve, reject) => {
    let settled = false;
    const finish = (callback, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      signal?.removeEventListener("abort", onAbort);
      callback(value);
    };
    const onAbort = () => {
      const reason = signal.reason || new Error("Search cancelled");
      controller.abort(reason);
      finish(reject, reason);
    };
    const timer = setTimeout(() => {
      const error = Object.assign(new Error("Extension search timed out"), { code: "EXTENSION_TIMEOUT" });
      controller.abort(error);
      finish(reject, error);
    }, milliseconds);
    if (signal?.aborted) return onAbort();
    signal?.addEventListener("abort", onAbort, { once: true });
    Promise.resolve().then(() => operation(controller.signal)).then(
      value => finish(resolve, value),
      error => finish(reject, error)
    );
  });
}

/**
 * Runs enabled extensions in explicit default-first, then priority and registration order.
 * A fallback is queried only when the preceding provider fails or returns no usable results.
 */
export class ExtensionHost {
  #extensions = new Map();
  #defaultByType = new Map();

  constructor(extensions = [], defaults = {}) {
    for (const extension of extensions) this.register(extension);
    for (const [mediaType, extensionId] of Object.entries(defaults)) {
      if (!MEDIA_TYPES.includes(mediaType)) throw new TypeError("Unsupported default media type: " + mediaType);
      if (!this.#extensions.has(extensionId)) throw new TypeError("Unknown default extension: " + extensionId);
      this.#defaultByType.set(mediaType, extensionId);
    }
  }

  register(extension) {
    const descriptor = validateDescriptor(extension?.descriptor);
    if (this.#extensions.has(descriptor.id)) throw new TypeError("Duplicate extension id: " + descriptor.id);
    if (typeof extension.search !== "function") throw new TypeError("Extension must implement search()");
    this.#extensions.set(descriptor.id, Object.freeze({ ...extension, descriptor }));
    return descriptor;
  }

  list({ mediaType } = {}) {
    return [...this.#extensions.values()]
      .map(extension => extension.descriptor)
      .filter(descriptor => !mediaType || descriptor.mediaTypes.includes(mediaType));
  }

  async search(mediaType, query, options = {}) {
    const text = typeof query === "string" ? query.trim() : "";
    if (!text) return { status: "invalid-query", items: [], attempts: [] };
    if (!MEDIA_TYPES.includes(mediaType)) return { status: "unsupported-media", items: [], attempts: [] };

    const defaultId = options.defaultExtensionId || this.#defaultByType.get(mediaType);
    const candidates = [...this.#extensions.values()]
      .filter(extension => extension.descriptor.enabled && extension.descriptor.mediaTypes.includes(mediaType))
      .sort((left, right) => {
        if (left.descriptor.id === defaultId) return -1;
        if (right.descriptor.id === defaultId) return 1;
        return left.descriptor.priority - right.descriptor.priority;
      });
    if (candidates.length === 0) return { status: "unavailable", items: [], attempts: [] };

    const attempts = [];
    const timeoutMs = Number.isFinite(options.timeoutMs) ? Math.max(1, options.timeoutMs) : 12000;
    for (const extension of candidates) {
      if (options.signal?.aborted) return { status: "cancelled", items: [], attempts };
      try {
        const response = await raceWithTimeout(
          signal => extension.search({ query: text, mediaType, signal }),
          timeoutMs,
          options.signal
        );
        if (!Array.isArray(response)) throw new TypeError("Extension returned a non-list result");
        const unique = new Map();
        for (const entry of response) {
          if (!entry || typeof entry.title !== "string" || !entry.title.trim()) continue;
          const item = { ...entry, title: entry.title.trim(), extensionId: extension.descriptor.id, extensionName: extension.descriptor.name };
          const key = resultKey(item);
          if (!unique.has(key)) unique.set(key, item);
        }
        const items = [...unique.values()];
        attempts.push({ extensionId: extension.descriptor.id, status: items.length ? "matched" : "empty" });
        if (items.length) return { status: "matched", items, attempts };
      } catch (error) {
        const cancelled = options.signal?.aborted;
        attempts.push({
          extensionId: extension.descriptor.id,
          status: cancelled ? "cancelled" : error?.code === "EXTENSION_TIMEOUT" ? "timeout" : "error",
          ...(typeof error?.code === "string" ? { code: error.code } : {})
        });
        if (cancelled) return { status: "cancelled", items: [], attempts };
      }
    }
    const hadError = attempts.some(attempt => ["error", "timeout"].includes(attempt.status));
    return { status: hadError ? "failed" : "empty", items: [], attempts };
  }
}

export function parseYouTubeVideoUrl(input) {
  let url;
  try {
    url = new URL(input);
  } catch {
    return null;
  }
  const host = url.hostname.toLowerCase().replace(/^www\./, "");
  const allowedHosts = new Set(["youtube.com", "m.youtube.com", "youtu.be", "youtube-nocookie.com"]);
  if (!allowedHosts.has(host)) return null;

  let id = "";
  if (host === "youtu.be") id = url.pathname.split("/").filter(Boolean)[0] || "";
  else if (url.pathname === "/watch") id = url.searchParams.get("v") || "";
  else {
    const match = url.pathname.match(/^\/(?:embed|shorts|live)\/([^/]+)/);
    if (match) id = match[1];
  }
  return /^[A-Za-z0-9_-]{11}$/.test(id) ? id : null;
}

export function youtubeEmbedUrl(input) {
  const id = parseYouTubeVideoUrl(input);
  return id ? "https://www.youtube-nocookie.com/embed/" + id + "?controls=1&playsinline=1&rel=0" : null;
}


export function youtubeSearchUrl(query) {
  const text = typeof query === "string" ? query.trim() : "";
  if (!text || text.length > 500) return null;
  const url = new URL("https://www.googleapis.com/youtube/v3/search");
  url.searchParams.set("part", "snippet");
  url.searchParams.set("type", "video");
  url.searchParams.set("maxResults", "10");
  url.searchParams.set("videoEmbeddable", "true");
  url.searchParams.set("videoSyndicated", "true");
  url.searchParams.set("q", text);
  return url.toString();
}

export function normalizeYouTubeSearchPayload(payload) {
  if (!payload || !Array.isArray(payload.items)) return [];
  const seen = new Set();
  return payload.items.flatMap(item => {
    const id = item?.id?.videoId;
    const title = item?.snippet?.title;
    if (typeof id !== "string" || !/^[A-Za-z0-9_-]{11}$/.test(id) || seen.has(id) || typeof title !== "string" || !title.trim()) return [];
    seen.add(id);
    const rawThumbnail = item?.snippet?.thumbnails?.medium?.url || item?.snippet?.thumbnails?.default?.url || "";
    let thumbnail = "";
    try {
      const parsed = new URL(rawThumbnail);
      if (parsed.protocol === "https:" && ["i.ytimg.com", "img.youtube.com"].includes(parsed.hostname)) thumbnail = parsed.toString();
    } catch {}
    return [{
      id,
      title: title.trim(),
      channel: typeof item?.snippet?.channelTitle === "string" ? item.snippet.channelTitle : "",
      thumbnail,
      url: "https://www.youtube.com/watch?v=" + id
    }];
  });
}
