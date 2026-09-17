import { describe, expect, it } from "vitest";
import { ModuleRegistry } from "../src/core/registry";
import { defineCommand, defineModule } from "../src/core/module";
import { normalizeValue, setting, toEnvironmentValue } from "../src/shared/config-schema";

const demo = defineModule({
  id: "demo",
  name: "Demo",
  version: "1.0.0",
  enabledByDefault: true,
  settings: [
    setting.toggle("enabled", "Feature enabled", true, { env: "DEMO_ENABLED" }),
    setting.text("name", "Name", "Bailey", { env: "DEMO_NAME" }),
    setting.number("limit", "Limit", 3, { env: "DEMO_LIMIT", min: 1, max: 10 }),
    setting.secret("token", "API token", { env: "DEMO_TOKEN" }),
    setting.select("mode", "Mode", "safe", [
      { label: "Safe", value: "safe" },
      { label: "Fast", value: "fast" },
    ]),
  ],
  commands: [defineCommand({
    name: "hello",
    description: "Say hello",
    actions: [{ type: "reply", text: "Hello." }],
  })],
});

describe("Bailey config schema", () => {
  it("materializes module settings and command toggles", () => {
    const registry = new ModuleRegistry();
    registry.register(demo);
    const keys = registry.getConfigDefinitions().map((definition) => definition.key);

    expect(keys).toContain("modules.demo.enabled");
    expect(keys).toContain("modules.demo.settings.token");
    expect(keys).toContain("modules.demo.commands.hello.enabled");
  });

  it("preserves ENV mappings without making ENV the UI model", () => {
    const registry = new ModuleRegistry();
    registry.register(demo);
    const definition = registry.findConfigDefinition("modules.demo.settings.limit")!;
    expect(definition.env).toBe("DEMO_LIMIT");
    expect(definition.label).toBe("Limit");
  });

  it("validates typed values", () => {
    const registry = new ModuleRegistry();
    registry.register(demo);
    const limit = registry.findConfigDefinition("modules.demo.settings.limit")!;
    const mode = registry.findConfigDefinition("modules.demo.settings.mode")!;

    expect(normalizeValue(limit, "7")).toBe(7);
    expect(() => normalizeValue(limit, 20)).toThrow();
    expect(() => normalizeValue(mode, "unknown")).toThrow();
    expect(toEnvironmentValue(true)).toBe("true");
  });

  it("rejects duplicate module ids", () => {
    const registry = new ModuleRegistry();
    registry.register(demo);
    expect(() => registry.register(demo)).toThrow(/already registered/);
  });
});
