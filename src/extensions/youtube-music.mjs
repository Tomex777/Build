import { normalizeYouTubeSearchPayload, youtubeSearchUrl } from "../extension-host.mjs";

function taggedError(code, message) {
  return Object.assign(new Error(message), { code });
}

export function createYouTubeMusicExtension({ getApiKey, fetchImpl = globalThis.fetch } = {}) {
  return {
    descriptor: Object.freeze({
      id: "youtube-music",
      name: "YouTube",
      mediaTypes: ["music"],
      enabled: true,
      priority: 10
    }),
    async search({ query, signal }) {
      const url = youtubeSearchUrl(query);
      if (!url) throw taggedError("QUERY_INVALID", "Enter a shorter search query.");
      const apiKey = typeof getApiKey === "function" ? String(getApiKey() || "").trim() : "";
      if (!apiKey) throw taggedError("YOUTUBE_KEY_REQUIRED", "Add a YouTube Data API key in Extensions.");
      if (typeof fetchImpl !== "function") throw taggedError("NETWORK_UNAVAILABLE", "Fetch is unavailable in this browser.");

      let response;
      try {
        response = await fetchImpl(url, {
          method: "GET",
          headers: { "X-Goog-Api-Key": apiKey },
          signal,
          cache: "no-store"
        });
      } catch (error) {
        if (signal?.aborted) throw error;
        throw taggedError("NETWORK_UNAVAILABLE", "Could not reach YouTube.");
      }

      let payload = {};
      try { payload = await response.json(); } catch {
        throw taggedError("INVALID_RESPONSE", "YouTube returned an unreadable response.");
      }
      if (!response.ok) {
        const reason = payload?.error?.errors?.[0]?.reason || "";
        if (reason === "quotaExceeded") throw taggedError("YOUTUBE_QUOTA", "YouTube search quota is exhausted.");
        if (response.status === 400 || response.status === 403) {
          throw taggedError("YOUTUBE_KEY_REJECTED", "YouTube rejected the key or request. Check API enablement and key restrictions.");
        }
        throw taggedError("YOUTUBE_HTTP_ERROR", "YouTube search failed with HTTP " + response.status + ".");
      }
      if (!Array.isArray(payload?.items)) throw taggedError("INVALID_RESPONSE", "YouTube returned an unexpected result.");
      return normalizeYouTubeSearchPayload(payload);
    }
  };
}
