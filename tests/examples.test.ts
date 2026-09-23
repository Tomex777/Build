import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { ExternalModuleManager } from "../src/external/external-module-manager";
import { NetworkAccessPolicyStore, registerNetworkHttpService } from "../src/services/network-access";
import { LocalStorageProvider, StorageHostService } from "../src/services/storage-service";

const roots: string[] = [];
afterEach(async () => Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true }))));

describe("Protocol 1 JavaScript example", () => {
  it("runs commands, events, lifecycle hooks and host-service calls without a WhatsApp socket", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-js-example-"));
    roots.push(root);
    const moduleRoot = join(root, "modules");
    const moduleDir = join(moduleRoot, "javascript-example");
    const dataRoot = join(root, "data");
    await mkdir(moduleDir, { recursive: true });
    const manifest = JSON.parse(await readFile(join(process.cwd(), "examples", "javascript-module", "bailey.module.json"), "utf8"));
    manifest.runtime = { command: process.execPath, args: ["main.mjs"], restart: "never" };
    await writeFile(join(moduleDir, "bailey.module.json"), JSON.stringify(manifest));
    await writeFile(join(moduleDir, "main.mjs"), await readFile(join(process.cwd(), "examples", "javascript-module", "main.mjs")));

    const granted = ["whatsapp.send", "whatsapp.react", "storage.write"];
    const manager = new ExternalModuleManager(moduleRoot, () => ({}), () => true, dataRoot, () => granted);
    const storage = new StorageHostService();
    storage.addProfile("default", new LocalStorageProvider(join(root, "objects")), true);
    storage.register(manager);
    const policies = new NetworkAccessPolicyStore(join(root, "network-policies.json"));
    await policies.load();
    registerNetworkHttpService(manager, policies);

    const loaded = await manager.load();
    expect(loaded.errors).toEqual([]);
    expect(manager.statuses()[0].grantedPermissions).toEqual(granted);
    const replies: string[] = [];
    const reactions: string[] = [];
    const command = loaded.definitions[0].commands?.find((item) => item.id === "jshello");
    await command!.execute!({ remoteJid: "user@s.whatsapp.net", senderJid: "user@s.whatsapp.net", text: ".jshello", args: [], reply: async (text) => { replies.push(text); }, react: async (emoji) => { reactions.push(emoji); }, showMenu: async () => {} });
    expect(replies).toEqual(["Hello from JavaScript. Bailey stored 21 bytes."]);
    expect(reactions).toEqual(["👋"]);

    await loaded.definitions[0].onMessage!({ remoteJid: "user@s.whatsapp.net", senderJid: "user@s.whatsapp.net", text: "hello javascript", reply: async (text) => { replies.push(text); }, react: async (emoji) => { reactions.push(emoji); } });
    expect(replies.at(-1)).toBe("JavaScript received a passive Protocol 1 event.");
    expect((await storageProviderText(root)).trim()).toBe("Hello from JavaScript");
    await manager.stopAll();
  });
});

async function storageProviderText(root: string): Promise<string> {
  return readFile(join(root, "objects", "example", "last-greeting.txt"), "utf8");
}
