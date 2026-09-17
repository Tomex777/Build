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

const myCommandsModule = defineModule({
  id: "my-commands",
  name: "My Commands",
  version: "1.0.0",
  commands: [],
});

const modules = [moduleDefinition, myCommandsModule];

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

  it("resets a shipped command to its defaults", async () => {
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

  it("creates, reopens, edits and deletes a Studio command", async () => {
    const commandStore = await store();
    const created = await commandStore.create(modules, "my-commands", {
      name: "hello",
      section: "Utility",
      description: "Say hello.",
      aliases: ["hi"],
      replyText: "Hello!",
    });

    expect(created.origin).toBe("custom");
    expect(created.editable).toBe(true);
    expect(commandStore.getById(modules, "my-commands", created.id)?.name).toBe("hello");
    expect(commandStore.resolve(modules, "hi")?.command.id).toBe(created.id);

    const edited = await commandStore.updateById(modules, "my-commands", created.id, {
      name: "greet",
      section: "Social",
      description: "Send a greeting.",
      aliases: ["hello"],
      replyText: "Hey there!",
    });

    expect(edited.id).toBe(created.id);
    expect(edited.name).toBe("greet");
    expect(edited.section).toBe("Social");
    expect(edited.replyText).toBe("Hey there!");
    expect(commandStore.resolve(modules, "greet")?.command.id).toBe(created.id);

    await commandStore.deleteById("my-commands", created.id);
    expect(commandStore.getById(modules, "my-commands", created.id)).toBeUndefined();
  });

  it("does not let a Studio-created command pretend it has shipped defaults", async () => {
    const commandStore = await store();
    const created = await commandStore.create(modules, "my-commands", {
      name: "hello",
      section: "Utility",
      description: "Say hello.",
      aliases: [],
      replyText: "Hello!",
    });

    await expect(commandStore.resetById(modules, "my-commands", created.id)).rejects.toThrow(/Delete the command instead/);
  });
});
