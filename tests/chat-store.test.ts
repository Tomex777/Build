import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { JsonChatStore } from "../src/core/chat-store";

const dirs: string[] = [];

async function makeStore() {
  const dir = await mkdtemp(join(tmpdir(), "bailey-chats-"));
  dirs.push(dir);
  const file = join(dir, "chats.json");
  const store = new JsonChatStore(file);
  await store.load();
  return { store, file };
}

afterEach(async () => {
  await Promise.all(dirs.splice(0).map((dir) => rm(dir, { recursive: true, force: true })));
});

describe("JsonChatStore", () => {
  it("persists and restores messages in timestamp order", async () => {
    const { store, file } = await makeStore();
    await store.ingest({ id: "b", remoteJid: "123@s.whatsapp.net", fromMe: false, text: "second", key: {}, timestamp: 2000, pushName: "Ada" });
    await store.ingest({ id: "a", remoteJid: "123@s.whatsapp.net", fromMe: true, text: "first", key: {}, timestamp: 1000 });

    expect(store.messages("123@s.whatsapp.net").map((message) => message.id)).toEqual(["a", "b"]);
    expect(store.listChats()[0]).toMatchObject({ title: "Ada", lastMessage: "second", messageCount: 2 });

    const restored = new JsonChatStore(file);
    await restored.load();
    expect(restored.messages("123@s.whatsapp.net").map((message) => message.text)).toEqual(["first", "second"]);
  });

  it("deduplicates repeated engine events by WhatsApp message id", async () => {
    const { store } = await makeStore();
    const message = { id: "same-id", remoteJid: "123@s.whatsapp.net", fromMe: false, text: "hello", key: {}, timestamp: 1000 };
    await store.ingest(message);
    await store.ingest(message);
    expect(store.messages("123@s.whatsapp.net")).toHaveLength(1);
  });

  it("searches cached chat names, jids and message text", async () => {
    const { store } = await makeStore();
    await store.ingest({ id: "1", remoteJid: "234801@s.whatsapp.net", fromMe: false, text: "anime night", key: {}, timestamp: 1000, pushName: "Mira" });
    expect(store.listChats("mira")).toHaveLength(1);
    expect(store.listChats("anime")).toHaveLength(1);
    expect(store.listChats("nothing")).toHaveLength(0);
  });
});
