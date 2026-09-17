import { describe, expect, it } from "vitest";
import { defineCommand, defineModule } from "../src/core/module";
import { ModuleRegistry } from "../src/core/registry";

function external(version: string, trigger = "hello") {
  return defineModule({
    id: "external.demo",
    name: "External Demo",
    version,
    commands: [defineCommand({
      id: "hello",
      name: trigger,
      description: "Demo command.",
      execute: async () => {},
    })],
  });
}

describe("ModuleRegistry external reload support", () => {
  it("can unregister and replace a module without disturbing core modules", () => {
    const registry = new ModuleRegistry();
    registry.register(defineModule({ id: "core", name: "Core", version: "1.0.0", commands: [] }));
    registry.register(external("1.0.0", "hello"));

    expect(registry.findModule("external.demo")?.version).toBe("1.0.0");
    expect(registry.unregister("external.demo")).toBe(true);
    expect(registry.findModule("core")?.name).toBe("Core");

    registry.register(external("2.0.0", "greet"));
    expect(registry.findModule("external.demo")?.version).toBe("2.0.0");
    expect(registry.resolveCommand("greet")?.module.id).toBe("external.demo");
    expect(registry.resolveCommand("hello")).toBeUndefined();
  });
});
