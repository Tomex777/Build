import { ExtensionHost, youtubeEmbedUrl } from "./extension-host.mjs";
import { descriptor as weebDescriptor, search as searchWeebCentral } from "./extensions/weeb-central.mjs";
import { descriptor as tfpdlDescriptor, search as searchTfpdl } from "./extensions/tfpdl.mjs";
import { descriptor as subspleaseDescriptor, search as searchSubsPlease } from "./extensions/subsplease.mjs";
import { createYouTubeMusicExtension } from "./extensions/youtube-music.mjs";
import { createAniListCatalogExtension } from "./extensions/anilist-catalog.mjs";

const el = selector => document.querySelector(selector);
const chat = el("#chat");
const input = el("#composer");
const form = el("#composer-form");
let youtubeApiKey = "";
try { youtubeApiKey = sessionStorage.getItem("annie.youtube.apiKey") || ""; } catch {}

const statusByProvider = [
  { id: "anilist-catalog", name: "AniList", media: "Anime & manga metadata", state: "Catalog ready", ready: true, note: "Official catalog metadata only; no episodes, chapters, streams, or downloads." },
  { id: "weeb-central", name: "Weeb Central", media: "Manga", state: "Unavailable", note: "Needs an authorized content API or license." },
  { id: "tfpdl", name: "TFPDL", media: "Movies & series", state: "Unavailable", note: "Needs an authorized content API or license." },
  { id: "subsplease", name: "SubsPlease", media: "Anime", state: "Unavailable", note: "Needs an authorized content API or license." },
  { id: "youtube", name: "YouTube", media: "Music", state: youtubeApiKey ? "Connected" : "Needs API key", note: "Official API search and embedded playback; no extracted audio or downloads." }
];

const host = new ExtensionHost([
  { descriptor: weebDescriptor, search: searchWeebCentral },
  { descriptor: tfpdlDescriptor, search: searchTfpdl },
  { descriptor: subspleaseDescriptor, search: searchSubsPlease },
  createYouTubeMusicExtension({ getApiKey: () => youtubeApiKey }),
  createAniListCatalogExtension()
], { anime: "anilist-catalog", manga: "anilist-catalog", music: "youtube-music" });

function escapeHtml(value) {
  return String(value).replace(/[&<>"]/g, character => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[character]);
}

function appendMessage(who, content, className = "") {
  const row = document.createElement("article");
  row.className = "message " + (who === "user" ? "message-user" : "message-annie");
  const label = who === "user" ? "You" : "Annie";
  row.innerHTML =
    '<div class="message-avatar ' + (who === "user" ? "avatar-user" : "avatar-annie") + '">' +
    (who === "user" ? "Y" : "A") + '</div>' +
    '<div class="message-body"><div class="message-label">' + label + '</div>' +
    '<div class="bubble ' + className + '">' + content + '</div></div>';
  chat.append(row);
  chat.scrollTop = chat.scrollHeight;
  return row;
}

function menuCard(title, description, actions) {
  const buttons = actions.map(action =>
    '<button class="action-chip" data-command="' + escapeHtml(action.command) + '">' +
    '<span class="chip-icon">' + escapeHtml(action.icon) + '</span>' +
    '<span>' + escapeHtml(action.label) + '</span></button>'
  ).join("");
  appendMessage("annie",
    '<div class="menu-title">' + escapeHtml(title) + '</div>' +
    '<p class="muted">' + escapeHtml(description) + '</p><div class="action-grid">' + buttons + '</div>',
    "menu-bubble");
}

function renderExtensions() {
  const cards = statusByProvider.map(provider =>
    '<section class="extension-card" data-provider="' + escapeHtml(provider.id) + '"><div class="extension-head"><strong>' + escapeHtml(provider.name) +
    '</strong><span class="extension-state ' + (provider.ready || (provider.id === "youtube" && youtubeApiKey) ? "state-ready" : "state-pending") +
    '">' + escapeHtml(provider.state) + '</span></div><div class="extension-media">' + escapeHtml(provider.media) +
    '</div><p>' + escapeHtml(provider.note) + '</p></section>'
  ).join("");
  const keyPanel =
    '<section class="youtube-key-config"><label for="youtube-api-key">YouTube Data API key</label>' +
    '<div class="key-row"><input id="youtube-api-key" type="password" autocomplete="new-password" placeholder="Paste a referrer-restricted key">' +
    '<button type="button" class="key-save" data-save-youtube-key>Save</button><button type="button" class="key-clear" data-clear-youtube-key>Clear</button></div>' +
    '<small>Stored for this browser tab only. Browser keys are visible in DevTools. Restrict this key to the app origin and YouTube Data API; proxy it for production. Search calls YouTube directly; Annie does not cache results.</small></section>';
  appendMessage("annie",
    '<div class="menu-title">Extensions</div><p class="muted">Providers stay independent. A missing or failed extension never blocks the others.</p>' +
    '<div class="extension-list">' + cards + '</div>' + keyPanel,
    "menu-bubble");
}

function showYouTube(url) {
  const embed = youtubeEmbedUrl(url);
  if (!embed) {
    appendMessage("annie", "That does not look like a valid YouTube video link. Send a watch, shorts, live, or youtu.be link.");
    return;
  }
  appendMessage("annie",
    '<div class="track-heading"><div><span class="eyebrow">YOUTUBE PLAYER</span><h3>Play from YouTube</h3></div><span class="youtube-mark">YouTube</span></div>' +
    '<div class="video-frame"><iframe src="' + embed + '" title="YouTube player" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe></div>' +
    '<p class="player-note">Playback stays in YouTube’s official player. Annie does not extract or download the audio.</p>',
    "player-bubble");
}

function renderYouTubeResults(items) {
  if (!items.length) {
    appendMessage("annie", "YouTube returned no embeddable videos for that search.");
    return;
  }
  const rows = items.map(item =>
    '<button class="youtube-result" type="button" data-youtube-video="' + item.id + '">' +
    (item.thumbnail ? '<img src="' + escapeHtml(item.thumbnail) + '" alt="" loading="lazy">' : '<span class="thumb-fallback">▶</span>') +
    '<span class="result-copy"><strong>' + escapeHtml(item.title) + '</strong><small>' + escapeHtml(item.channel || "YouTube") + '</small></span>' +
    '<span class="result-play" aria-label="Play">▶</span></button>'
  ).join("");
  appendMessage("annie",
    '<div class="menu-title">YouTube results</div><p class="muted">Choose a video to play it in YouTube’s official player.</p>' +
    '<div class="youtube-results">' + rows + '</div>',
    "menu-bubble");
}

async function searchYouTube(query) {
  const result = await host.search("music", query, { defaultExtensionId: "youtube-music", timeoutMs: 15000 });
  if (result.status === "matched") return renderYouTubeResults(result.items);

  const lastAttempt = result.attempts[result.attempts.length - 1];
  const code = lastAttempt?.code;
  const messages = {
    YOUTUBE_KEY_REQUIRED: 'Add a YouTube Data API key in <button class="inline-command" data-command="/extensions">/extensions</button>, then search again.',
    YOUTUBE_QUOTA: "YouTube search quota is exhausted. Try again after its quota resets.",
    YOUTUBE_KEY_REJECTED: "YouTube rejected the API key. Check that the Data API is enabled and the key is restricted to this app’s domain.",
    NETWORK_UNAVAILABLE: "Could not reach YouTube. Check the connection and try again.",
    INVALID_RESPONSE: "YouTube returned a response Annie could not read.",
    QUERY_INVALID: "Keep the search under 500 characters.",
    YOUTUBE_HTTP_ERROR: "YouTube search failed. Try again."
  };
  if (result.status === "empty") return appendMessage("annie", "YouTube returned no embeddable videos for that search.");
  if (result.status === "unavailable") return appendMessage("annie", "The YouTube music extension is unavailable.");
  if (lastAttempt?.status === "timeout") return appendMessage("annie", "YouTube search timed out. Check the connection and try again.");
  if (lastAttempt?.status === "cancelled") return appendMessage("annie", "Search cancelled.");
  appendMessage("annie", messages[code] || "YouTube search failed. Try again.");
}
function normalizedCommand(value) {
  return String(value || "").toLocaleLowerCase().replace(/\s+/g, " ").trim();
}

async function handleCommand(raw) {
  const text = raw.trim();
  const [first, ...tail] = text.split(/\s+/);
  const command = first.toLowerCase();
  const query = tail.join(" ");
  const normalized = normalizedCommand(query);

  if (command === "/anime" && !query) return menuCard("Anime", "Choose an action or type a title to search.", [
    { icon: "⌕", label: "Search anime", command: "/anime " },
    { icon: "◷", label: "Recently aired", command: "/anime recently aired" },
    { icon: "▶", label: "Continue watching", command: "/anime continue" },
    { icon: "↓", label: "Downloads", command: "/downloads anime" }
  ]);
  if ((command === "/movie" || command === "/movies") && !query) return menuCard("Movies & series", "Choose an action or type a title to search.", [
    { icon: "⌕", label: "Search movies", command: "/movie " },
    { icon: "◷", label: "Recently released", command: "/movie recently released" },
    { icon: "▶", label: "Continue watching", command: "/movie continue" },
    { icon: "↓", label: "Downloads", command: "/downloads movies" }
  ]);
  if (command === "/manga" && !query) return menuCard("Manga", "Choose an action or type a title to search.", [
    { icon: "⌕", label: "Search manga", command: "/manga " },
    { icon: "◷", label: "Recently updated", command: "/manga recently updated" },
    { icon: "▤", label: "Continue reading", command: "/manga continue" },
    { icon: "↓", label: "Downloads", command: "/downloads manga" }
  ]);
  if (command === "/music" && !query) return menuCard("Music", "Search YouTube or paste a YouTube link. Lyrics require an authorized lyrics provider.", [
    { icon: "⌕", label: "Search music", command: "/music search " },
    { icon: "▶", label: "Paste YouTube link", command: "/music " },
    { icon: "⌘", label: "Set up YouTube", command: "/extensions" }
  ]);
  if (command === "/music") {
    if (/^https?:\/\//i.test(query)) return showYouTube(query);
    if (normalized.startsWith("search ")) return searchYouTube(query.slice(7).trim());
    return searchYouTube(query);
  }
  if (command === "/extensions") return renderExtensions();
  if (command === "/help") return menuCard("Annie commands", "Annie works command-first. AI can be added as an optional helper later.", [
    { icon: "A", label: "Anime", command: "/anime" },
    { icon: "F", label: "Movies & series", command: "/movie" },
    { icon: "M", label: "Manga", command: "/manga" },
    { icon: "♫", label: "Music", command: "/music" },
    { icon: "⌘", label: "Extensions", command: "/extensions" }
  ]);
  if (command === "/downloads") {
    const filter = query || "all media";
    return appendMessage("annie", "No " + escapeHtml(filter) + " downloads yet. Downloads will appear here when an authorized extension is connected.");
  }
  if (command === "/anime" && normalized === "recently aired") {
    return appendMessage("annie", "The recently aired feed needs an authorized anime catalog extension before it can show verified episodes.");
  }
  if ((command === "/movie" || command === "/movies") && normalized === "recently released") {
    return appendMessage("annie", "The release feed needs an authorized movies and series catalog extension.");
  }
  if (command === "/manga" && normalized === "recently updated") {
    return appendMessage("annie", "The chapter update feed needs an authorized manga catalog extension.");
  }
  if ((command === "/anime" || command === "/movie" || command === "/movies") && normalized === "continue") {
    return appendMessage("annie", "Nothing to continue watching yet.");
  }
  if (command === "/manga" && normalized === "continue") {
    return appendMessage("annie", "Nothing to continue reading yet.");
  }

  const mediaType = command === "/anime" ? "anime" :
    command === "/movie" || command === "/movies" ? "movie" :
    command === "/manga" ? "manga" : null;
  if (mediaType && query) {
    const result = await host.search(mediaType, query);
    if (result.status === "matched") {
      appendMessage("annie", "Found " + result.items.length + " result(s) from " + escapeHtml(result.items[0].extensionName) + ".");
    } else {
      appendMessage("annie", "No authorized " + escapeHtml(mediaType) + " extension is connected yet. Use /extensions to see provider status.");
    }
    return;
  }
  appendMessage("annie", 'I use slash commands. Try <button class="inline-command" data-command="/help">/help</button> or <button class="inline-command" data-command="/extensions">/extensions</button>.');
}

form.addEventListener("submit", event => {
  event.preventDefault();
  const text = input.value.trim();
  if (!text) return;
  appendMessage("user", escapeHtml(text), "user-bubble");
  input.value = "";
  input.focus();
  handleCommand(text).catch(() => appendMessage("annie", "That action failed. Try again, or use /extensions to check provider status."));
});

chat.addEventListener("click", event => {
  const clearButton = event.target.closest("[data-clear-youtube-key]");
  if (clearButton) {
    youtubeApiKey = "";
    try { sessionStorage.removeItem("annie.youtube.apiKey"); } catch {}
    statusByProvider.find(provider => provider.id === "youtube").state = "Needs API key";
    const badge = chat.querySelector('[data-provider="youtube"] .extension-state');
    if (badge) { badge.textContent = "Needs API key"; badge.classList.remove("state-ready"); badge.classList.add("state-pending"); }
    appendMessage("annie", "YouTube key cleared from this tab.");
    return;
  }
  const saveButton = event.target.closest("[data-save-youtube-key]");
  if (saveButton) {
    const keyField = el("#youtube-api-key");
    const key = keyField?.value.trim() || "";
    if (!key) return appendMessage("annie", "Paste a YouTube Data API key first.");
    youtubeApiKey = key;
    try { sessionStorage.setItem("annie.youtube.apiKey", key); } catch {}
    statusByProvider.find(provider => provider.id === "youtube").state = "Connected";
    const badge = chat.querySelector('[data-provider="youtube"] .extension-state');
    if (badge) { badge.textContent = "Connected"; badge.classList.remove("state-pending"); badge.classList.add("state-ready"); }
    appendMessage("annie", "YouTube key saved for this tab. Search uses the official API; playback stays in the official player.");
    return;
  }
  const videoButton = event.target.closest("[data-youtube-video]");
  if (videoButton) return showYouTube("https://www.youtube.com/watch?v=" + videoButton.dataset.youtubeVideo);
  const commandButton = event.target.closest("[data-command]");
  if (!commandButton) return;
  input.value = commandButton.dataset.command;
  input.focus();
  if (input.value.endsWith(" ")) return;
  form.requestSubmit();
});

document.querySelectorAll("[data-quick-command]").forEach(button => {
  button.addEventListener("click", () => {
    input.value = button.dataset.quickCommand;
    input.focus();
    form.requestSubmit();
  });
});

appendMessage("annie", "Hi, I’m Annie. Type a command to start. Providers stay separate, and I’ll show clearly when one is unavailable.", "welcome-bubble");
