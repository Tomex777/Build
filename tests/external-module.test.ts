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
      capabilities: ["commands", "events", "jobs"],
      commands: [
        { id: "balance", name: "balance", section: "Economy", description: "Show a balance." },
      ],
      jobs: [
        { id: "interest", intervalSeconds: 3600, runOnStart: true },
      ],
    });

    expect(manifest.runtime.command).toBe("python");
    expect(manifest.commands?.[0]?.name).toBe("balance");
    expect(manifest.capabilities).toContain("events");
    expect(manifest.jobs?.[0]?.id).toBe("interest");
  });

  it("rejects unknown capabilities instead of silently accepting them", () => {
    expect(() => parseExternalModuleManifest({
      protocol: 1,
      id: "bad-capability",
      name: "Bad capability",
      version: "1.0.0",
      runtime: { command: "python", args: ["main.py"] },
      capabilities: ["telepathy"],
    })).toThrow("Unsupported module capability");
  });

  it("requires the jobs capability when jobs are declared", () => {
    expect(() => parseExternalModuleManifest({
      protocol: 1,
      id: "bad-jobs",
      name: "Bad jobs",
      version: "1.0.0",
      runtime: { command: "python", args: ["main.py"] },
      jobs: [{ id: "tick", intervalSeconds: 60 }],
    })).toThrow("must declare the jobs capability");
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

  it("delivers ordinary incoming-message events to modules that opt into events", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-external-events-"));
    tempDirs.push(root);
    const moduleDir = join(root, "listener");
    await mkdir(moduleDir, { recursive: true });

    await writeFile(join(moduleDir, "bailey.module.json"), JSON.stringify({
      protocol: 1,
      id: "listener",
      name: "Listener",
      version: "1.0.0",
      runtime: { command: process.execPath, args: ["worker.mjs"] },
      capabilities: ["events"],
    }, null, 2));

    await writeFile(join(moduleDir, "worker.mjs"), `
import readline from "node:readline";
const input = readline.createInterface({ input: process.stdin });
input.on("line", (line) => {
  const request = JSON.parse(line);
  if (request.type !== "event.dispatch" || request.event !== "message.received") return;
  process.stdout.write(JSON.stringify({
    protocol: 1,
    replyTo: request.id,
    ok: true,
    actions: [
      { type: "reply", text: "event:" + (request.context.text ?? "") },
      { type: "react", emoji: "👀" }
    ]
  }) + "\\n");
});
`);

    const manager = new ExternalModuleManager(root, () => ({}));
    const loaded = await manager.load();
    expect(loaded.errors).toEqual([]);
    expect(loaded.definitions[0].onMessage).toBeTypeOf("function");

    const replies: string[] = [];
    const reactions: string[] = [];
    await loaded.definitions[0].onMessage!({
      remoteJid: "group@g.us",
      senderJid: "123@s.whatsapp.net",
      text: "normal message",
      pushName: "Tester",
      timestamp: 123456,
      reply: async (text) => { replies.push(text); },
      react: async (emoji) => { reactions.push(emoji); },
    });

    expect(replies).toEqual(["event:normal message"]);
    expect(reactions).toEqual(["👀"]);
    await manager.stopAll();
  });

  it("executes module jobs and routes outbound sends through Bailey", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-external-jobs-"));
    tempDirs.push(root);
    const moduleDir = join(root, "scheduler");
    await mkdir(moduleDir, { recursive: true });

    await writeFile(join(moduleDir, "bailey.module.json"), JSON.stringify({
      protocol: 1,
      id: "scheduler",
      name: "Scheduler",
      version: "1.0.0",
      runtime: { command: process.execPath, args: ["worker.mjs"] },
      capabilities: ["jobs"],
      jobs: [{ id: "tick", intervalSeconds: 60 }],
    }, null, 2));

    await writeFile(join(moduleDir, "worker.mjs"), `
import readline from "node:readline";
const input = readline.createInterface({ input: process.stdin });
input.on("line", (line) => {
  const request = JSON.parse(line);
  if (request.type !== "job.execute") return;
  process.stdout.write(JSON.stringify({
    protocol: 1,
    replyTo: request.id,
    ok: true,
    actions: [
      { type: "send", remoteJid: "updates@g.us", text: request.jobId + ":" + request.scheduledAt }
    ]
  }) + "\\n");
});
`);

    const manager = new ExternalModuleManager(root, () => ({}));
    const loaded = await manager.load();
    expect(loaded.errors).toEqual([]);

    const sent: Array<{ remoteJid: string; text: string }> = [];
    manager.setHostSendText(async (remoteJid, text) => {
      sent.push({ remoteJid, text });
    });
    await manager.executeJob("scheduler", "tick", 999);

    expect(sent).toEqual([{ remoteJid: "updates@g.us", text: "tick:999" }]);
    await manager.stopAll();
  });

  it("does not execute jobs while their module is disabled", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-disabled-job-"));
    tempDirs.push(root);
    const moduleDir = join(root, "disabled");
    await mkdir(moduleDir, { recursive: true });

    await writeFile(join(moduleDir, "bailey.module.json"), JSON.stringify({
      protocol: 1,
      id: "disabled",
      name: "Disabled",
      version: "1.0.0",
      runtime: { command: process.execPath, args: ["worker.mjs"] },
      capabilities: ["jobs"],
      jobs: [{ id: "tick", intervalSeconds: 60 }],
    }, null, 2));
    await writeFile(join(moduleDir, "worker.mjs"), "throw new Error('should not start');\n");

    const manager = new ExternalModuleManager(root, () => ({}), () => false);
    await manager.load();
    await expect(manager.executeJob("disabled", "tick", 999)).resolves.toBeUndefined();
    await manager.stopAll();
  });
});
