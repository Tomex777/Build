interface ChatSummary {
  remoteJid: string;
  title: string;
  lastMessage: string;
  lastTimestamp: number;
  lastFromMe: boolean;
  messageCount: number;
}

interface ChatMessage {
  id: string;
  remoteJid: string;
  participant?: string;
  pushName?: string;
  text: string;
  fromMe: boolean;
  timestamp: number;
}

interface ChatBaileyApi {
  listChats(query?: string): Promise<ChatSummary[]>;
  getChatMessages(remoteJid: string, limit?: number): Promise<ChatMessage[]>;
  sendChatMessage(remoteJid: string, text: string): Promise<{ ok: boolean }>;
  onChatMessage(listener: (message: ChatMessage) => void): () => void;
}

const bailey = (window as unknown as { bailey: ChatBaileyApi }).bailey;
const list = document.querySelector<HTMLElement>("#chat-list")!;
const search = document.querySelector<HTMLInputElement>("#chat-search")!;
const conversation = document.querySelector<HTMLElement>("#chat-messages")!;
const title = document.querySelector<HTMLElement>("#chat-title")!;
const subtitle = document.querySelector<HTMLElement>("#chat-subtitle")!;
const composer = document.querySelector<HTMLFormElement>("#chat-composer")!;
const input = document.querySelector<HTMLTextAreaElement>("#chat-input")!;
const send = document.querySelector<HTMLButtonElement>("#chat-send")!;
const status = document.querySelector<HTMLElement>("#chat-send-status")!;
let selectedJid: string | null = null;
let searchTimer: number | undefined;

function formatTime(timestamp: number): string {
  if (!timestamp) return "";
  const date = new Date(timestamp);
  const today = new Date();
  const sameDay = date.toDateString() === today.toDateString();
  return sameDay
    ? date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString([], { month: "short", day: "numeric" });
}

function initials(value: string): string {
  const parts = value.trim().split(/\s+/).filter(Boolean);
  return (parts.length > 1 ? `${parts[0][0]}${parts[1][0]}` : value.slice(0, 2)).toUpperCase();
}

function emptyList(copy: string): void {
  list.innerHTML = "";
  const empty = document.createElement("div");
  empty.className = "chat-list-empty";
  empty.textContent = copy;
  list.append(empty);
}

async function renderChats(): Promise<void> {
  const chats = await bailey.listChats(search.value);
  list.innerHTML = "";
  if (!chats.length) {
    emptyList(search.value.trim() ? "No cached chats match your search." : "No cached chats yet. New WhatsApp messages will appear here while Bailey is running.");
    return;
  }

  for (const chat of chats) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `chat-item${chat.remoteJid === selectedJid ? " active" : ""}`;
    const avatar = document.createElement("span");
    avatar.className = "chat-avatar";
    avatar.textContent = initials(chat.title);
    const body = document.createElement("span");
    body.className = "chat-item-body";
    const heading = document.createElement("span");
    heading.className = "chat-item-heading";
    const name = document.createElement("strong");
    name.textContent = chat.title;
    const time = document.createElement("time");
    time.textContent = formatTime(chat.lastTimestamp);
    heading.append(name, time);
    const preview = document.createElement("span");
    preview.className = "chat-preview";
    preview.textContent = `${chat.lastFromMe ? "You: " : ""}${chat.lastMessage}`;
    body.append(heading, preview);
    button.append(avatar, body);
    button.addEventListener("click", () => void openChat(chat));
    list.append(button);
  }
}

function renderMessages(messages: ChatMessage[]): void {
  conversation.innerHTML = "";
  if (!messages.length) {
    const empty = document.createElement("div");
    empty.className = "conversation-empty";
    empty.textContent = "No cached messages in this conversation yet.";
    conversation.append(empty);
    return;
  }
  for (const message of messages) {
    const row = document.createElement("div");
    row.className = `message-row ${message.fromMe ? "outgoing" : "incoming"}`;
    const bubble = document.createElement("div");
    bubble.className = "message-bubble";
    if (!message.fromMe && message.pushName) {
      const sender = document.createElement("span");
      sender.className = "message-sender";
      sender.textContent = message.pushName;
      bubble.append(sender);
    }
    const text = document.createElement("div");
    text.className = "message-text";
    text.textContent = message.text;
    const meta = document.createElement("time");
    meta.textContent = formatTime(message.timestamp);
    bubble.append(text, meta);
    row.append(bubble);
    conversation.append(row);
  }
  conversation.scrollTop = conversation.scrollHeight;
}

async function openChat(chat: ChatSummary): Promise<void> {
  selectedJid = chat.remoteJid;
  title.textContent = chat.title;
  subtitle.textContent = chat.remoteJid;
  input.disabled = false;
  send.disabled = false;
  const messages = await bailey.getChatMessages(chat.remoteJid, 300);
  renderMessages(messages);
  await renderChats();
  input.focus();
}

search.addEventListener("input", () => {
  if (searchTimer) window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(() => void renderChats(), 140);
});

composer.addEventListener("submit", (event) => {
  event.preventDefault();
  if (!selectedJid) return;
  const text = input.value.trim();
  if (!text) return;
  send.disabled = true;
  status.textContent = "Sending…";
  void bailey.sendChatMessage(selectedJid, text).then(() => {
    input.value = "";
    status.textContent = "Sent";
  }).catch((error) => {
    status.textContent = error instanceof Error ? error.message : String(error);
  }).finally(() => {
    send.disabled = false;
    input.focus();
  });
});

input.addEventListener("keydown", (event) => {
  if (event.key !== "Enter" || event.shiftKey) return;
  event.preventDefault();
  composer.requestSubmit();
});

bailey.onChatMessage((message) => {
  void renderChats();
  if (message.remoteJid === selectedJid) {
    void bailey.getChatMessages(message.remoteJid, 300).then(renderMessages);
  }
});

void renderChats();
