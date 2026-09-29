function taggedError(code, message) {
  return Object.assign(new Error(message), { code });
}

export const descriptor = Object.freeze({
  id: "anilist-catalog",
  name: "AniList",
  mediaTypes: ["anime", "manga"],
  enabled: true,
  priority: 20
});

const SEARCH_QUERY = `
  query AnnieCatalogSearch($search: String!, $type: MediaType!) {
    Page(page: 1, perPage: 10) {
      media(search: $search, type: $type, isAdult: false, sort: SEARCH_MATCH) {
        id
        type
        title { romaji english native }
        startDate { year }
        episodes
        chapters
        status
        coverImage { medium }
      }
    }
  }
`;

function safeCoverUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && (url.hostname === "anilist.co" || url.hostname.endsWith(".anilist.co"))
      ? url.toString()
      : "";
  } catch {
    return "";
  }
}

export function normalizeAniListPayload(payload) {
  const media = payload?.data?.Page?.media;
  if (!Array.isArray(media)) return [];
  const seen = new Set();
  return media.flatMap(item => {
    const id = item?.id;
    const type = item?.type;
    const title = item?.title?.english || item?.title?.romaji || item?.title?.native;
    if (!Number.isSafeInteger(id) || id <= 0 || !["ANIME", "MANGA"].includes(type) || typeof title !== "string" || !title.trim() || seen.has(id)) return [];
    seen.add(id);
    const mediaType = type === "ANIME" ? "anime" : "manga";
    return [{
      id,
      title: title.trim(),
      mediaType,
      year: Number.isSafeInteger(item?.startDate?.year) ? item.startDate.year : undefined,
      episodes: Number.isSafeInteger(item?.episodes) ? item.episodes : undefined,
      chapters: Number.isSafeInteger(item?.chapters) ? item.chapters : undefined,
      status: typeof item?.status === "string" ? item.status : "",
      thumbnail: safeCoverUrl(item?.coverImage?.medium),
      url: "https://anilist.co/" + mediaType + "/" + id
    }];
  });
}

export function createAniListCatalogExtension({ fetchImpl = globalThis.fetch } = {}) {
  return {
    descriptor,
    async search({ query, mediaType, signal }) {
      const text = typeof query === "string" ? query.trim() : "";
      if (!text || text.length > 200) throw taggedError("ANILIST_QUERY_INVALID", "Keep the catalog search under 200 characters.");
      if (!["anime", "manga"].includes(mediaType)) throw taggedError("ANILIST_MEDIA_UNSUPPORTED", "AniList supports anime and manga metadata.");
      if (typeof fetchImpl !== "function") throw taggedError("NETWORK_UNAVAILABLE", "Fetch is unavailable in this browser.");

      let response;
      try {
        response = await fetchImpl("https://graphql.anilist.co", {
          method: "POST",
          headers: { "Content-Type": "application/json", "Accept": "application/json" },
          body: JSON.stringify({
            query: SEARCH_QUERY,
            variables: { search: text, type: mediaType.toUpperCase() }
          }),
          signal,
          cache: "no-store"
        });
      } catch (error) {
        if (signal?.aborted) throw error;
        throw taggedError("NETWORK_UNAVAILABLE", "Could not reach AniList.");
      }

      let payload;
      try { payload = await response.json(); } catch {
        throw taggedError("INVALID_RESPONSE", "AniList returned an unreadable response.");
      }
      if (!response.ok) {
        if (response.status === 429) throw taggedError("ANILIST_RATE_LIMIT", "AniList is rate limiting requests.");
        throw taggedError("ANILIST_HTTP_ERROR", "AniList search failed with HTTP " + response.status + ".");
      }
      if (Array.isArray(payload?.errors) && payload.errors.length) {
        throw taggedError("ANILIST_API_ERROR", "AniList rejected the catalog query.");
      }
      if (!Array.isArray(payload?.data?.Page?.media)) {
        throw taggedError("INVALID_RESPONSE", "AniList returned an unexpected result.");
      }
      return normalizeAniListPayload(payload);
    }
  };
}
