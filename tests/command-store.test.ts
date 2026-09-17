import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { JsonCommandStore } from "../src/core/command-store";
import { defineCommand, defineModule } from "../src/core/module";

const tempDirs: string[] = [];

async function store() {
  const dir = await mkdtemp(join(tmpdir(), "bailey-command-store-"));
  tempDirs.push(dir);
  const commandStore = new JsonCommandStore(join(dir, "commands.json"));
  await commandStore.load();
  return commandStore;
}

const moduleDefinition = defineModule({
  id: "core",
  name: "Core",
  version: "1.0.0",
  commands: [
    defineCommand({
      id: "ping",
      name: "ping",
      section: "Runtime",
      description: "Ping the runtime.",
      aliases: ["p"],
      actions: [{ type: "reply", text: "Pong." }],
    }),
  ],
});

afterEach(async () => {
  await Promise.all(tempDirs.splice(0).map((dir) => rm(dir, { recursive: true, force: true })));
});

describe("JsonCommandStore", () => {
  it("edits a visual command without changing its stable id", async () => {
    const commandStore = await store();
    const command = moduleDefinition.commands![0];

    const edited = await commandStore.update(moduleDefinition, command, {
      name: "alive",
      section: "Health",
      description: "Check whether Bailey is alive.",
      aliases: ["ping"],
      replyText: "Still here.",
    });

    expect(edited.id).toBe("ping");
    expect(edited.name).toBe("alive");
    expect(edited.section).toBe("Health");
    expect(edited.replyText).toBe("Still here.");
    expect(commandStore.resolve([moduleDefinition], "alive")?.command.id).toBe("ping");
    expect(commandStore.resolve([moduleDefinition], "ping")?.command.id).toBe("ping");
  });

  it("resets a command to its shipped defaults", async () => {
    const commandStore = await store();
    const command = moduleDefinition.commands![0];
    await commandStore.update(moduleDefinition, command, {
      name: "alive",
      section: "Health",
      description: "Check whether Bailey is alive.",
      aliases: [],
      replyText: "Still here.",
    });

    const reset = await commandStore.reset(moduleDefinition, command);
    expect(reset.name).toBe("ping");
    expect(reset.section).toBe("Runtime");
    expect(reset.replyText).toBe("Pong.");
  });
});
