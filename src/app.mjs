import { ExtensionHost, youtubeEmbedUrl } from "./extension-host.mjs";

const el = selector => document.querySelector(selector);
const chat = el("#chat");
const input = el("#composer");
const form = el("#composer-form");
const statusByProvider = [
  { id: "weeb-central", name: "Weeb Central", media: "Manga", state: "Unavailable", note: "Needs an authorized content API or license." },
  { id: "tfpdl", name: "TFPDL", media: "Movies & series", state: "Unavailable", note: "Needs an authorized content API or license." },
  { id: "subsplease", name: "SubsPlease", media: "Anime", state: "Unavailable", note: "Needs an authorized content API or license." },
  { id: "youtube", name: "YouTube", media: "Music", state: "Link playback", note: "Official embedded player. Search needs a YouTube API key." }
];

const host = new ExtensionHost([]);

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
    '<section class="extension-card"><div class="extension-head"><strong>' + escapeHtml(provider.name) +
    '</strong><span class="extension-state ' + (provider.id === "youtube" ? "state-ready" : "state-pending") +
    '">' + escapeHtml(provider.state) + '</span></div><div class="extension-media">' + escapeHtml(provider.media) +
    '</div><p>' + escapeHtml(provider.note) + '</p></section>'
  ).join("");
  appendMessage("annie",
    '<div class="menu-title">Extensions</div><p class="muted">Providers stay independent. A missing or failed extension never blocks the others.</p>' +
    '<div class="extension-list">' + cards + '</div>',
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
  if (command === "/music") {
    if (/^https?:\/\//i.test(query)) return showYouTube(query);
    return appendMessage("annie", "Send a YouTube video link after /music for playback in the official player. Search and synced lyrics need authorized provider APIs.");
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
  const button = event.target.closest("[data-command]");
  if (!button) return;
  input.value = button.dataset.command;
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
