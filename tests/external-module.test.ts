import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { ExternalModuleManager } from "../src/external/external-module-manager";
import { parseExternalModuleManifest } from "../src/external/protocol";

const tempDirs: string[] = [];

afterEach(async () => {
  await Promise.all(tempDirs.splice(0).map((dir) => rm(dir, { recursive: true, force: true })));
});

describe("Bailey external module protocol", () => {
  it("validates language-neutral manifests", () => {
    const manifest = parseExternalModuleManifest({
      protocol: 1,
      id: "economy",
      name: "Economy",
      version: "1.0.0",
      runtime: { command: "python", args: ["main.py"] },
      commands: [
        { id: "balance", name: "balance", section: "Economy", description: "Show a balance." },
      ],
    });

    expect(manifest.runtime.command).toBe("python");
    expect(manifest.commands?.[0]?.name).toBe("balance");
  });

  it("runs a module as a child process and applies returned Bailey actions", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-external-module-"));
    tempDirs.push(root);
    const moduleDir = join(root, "echo");
    await mkdir(moduleDir, { recursive: true });

    await writeFile(join(moduleDir, "bailey.module.json"), JSON.stringify({
      protocol: 1,
      id: "echo",
      name: "Echo module",
      version: "1.0.0",
      runtime: { command: process.execPath, args: ["worker.mjs"] },
      commands: [
        { id: "echo", name: "echo", section: "Utility", description: "Echo command arguments." },
      ],
      settings: [
        { key: "label", label: "Label", type: "text", defaultValue: "Echo", env: "ECHO_LABEL" },
      ],
    }, null, 2));

    await writeFile(join(moduleDir, "worker.mjs"), `
import readline from "node:readline";
const input = readline.createInterface({ input: process.stdin });
input.on("line", (line) => {
  const request = JSON.parse(line);
  process.stdout.write(JSON.stringify({
    protocol: 1,
    replyTo: request.id,
    ok: true,
    actions: [
      { type: "reply", text: process.env.ECHO_LABEL + ":" + request.context.args.join("|") },
      { type: "react", emoji: "✅" }
    ]
  }) + "\\n");
});
`);

    const manager = new ExternalModuleManager(root, () => ({ ECHO_LABEL: "Worker" }));
    const loaded = await manager.load();
    expect(loaded.errors).toEqual([]);
    expect(loaded.definitions).toHaveLength(1);

    const command = loaded.definitions[0].commands?.[0];
    expect(command?.execute).toBeTypeOf("function");

    const replies: string[] = [];
    const reactions: string[] = [];
    await command!.execute!({
      remoteJid: "123@s.whatsapp.net",
      senderJid: "123@s.whatsapp.net",
      text: ".echo one two",
      args: ["one", "two"],
      reply: async (text) => { replies.push(text); },
      react: async (emoji) => { reactions.push(emoji); },
      showMenu: async () => {},
    });

    expect(replies).toEqual(["Worker:one|two"]);
    expect(reactions).toEqual(["✅"]);
    await manager.stopAll();
  });
});
