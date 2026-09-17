import { mkdtemp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { JsonConfigStore } from "../src/core/config-store";
import type { ConfigDefinition } from "../src/shared/config-schema";

const secretDefinition: ConfigDefinition = {
  key: "modules.demo.settings.apiKey",
  moduleId: "demo",
  section: "Demo",
  label: "API key",
  type: "secret",
  defaultValue: "",
  env: "DEMO_API_KEY",
};

const toggleDefinition: ConfigDefinition = {
  key: "modules.demo.enabled",
  moduleId: "demo",
  section: "Demo",
  label: "Enabled",
  type: "toggle",
  defaultValue: true,
  env: "DEMO_ENABLED",
};

const codec = {
  encode: (value: string) => `encoded:${Buffer.from(value).toString("base64")}`,
  decode: (value: string) => Buffer.from(value.replace("encoded:", ""), "base64").toString("utf8"),
};

describe("JsonConfigStore", () => {
  it("does not expose secret values to the renderer payload", async () => {
    const dir = await mkdtemp(join(tmpdir(), "bailey-test-"));
    const file = join(dir, "config.json");
    const store = new JsonConfigStore(file, codec);
    await store.set(secretDefinition, "super-secret");

    const ui = store.listForUi([secretDefinition]);
    expect(ui[0]).toEqual({ key: secretDefinition.key, value: "", secretConfigured: true });

    const disk = await readFile(file, "utf8");
    expect(disk).not.toContain("super-secret");
  });

  it("generates environment values for legacy bot code", async () => {
    const dir = await mkdtemp(join(tmpdir(), "bailey-test-"));
    const store = new JsonConfigStore(join(dir, "config.json"), codec);
    await store.set(secretDefinition, "abc123");
    await store.set(toggleDefinition, false);

    expect(store.toEnvironment([secretDefinition, toggleDefinition])).toEqual({
      DEMO_API_KEY: "abc123",
      DEMO_ENABLED: "false",
    });
  });
});
