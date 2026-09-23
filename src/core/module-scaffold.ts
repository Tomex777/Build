import { BAILEY_MODULE_PROTOCOL, type ExternalModuleCapability, type ExternalModuleManifest } from "../external/protocol";

export type ModuleScaffoldRuntime = "python" | "javascript";
export type ModuleScaffoldFeature = "events" | "jobs" | "storage" | "services" | "media" | "lifecycle";

export interface ModuleScaffoldInput {
  id: string;
  name: string;
  description?: string;
  runtime: ModuleScaffoldRuntime;
  firstCommand?: string;
  firstSection?: string;
  features?: ModuleScaffoldFeature[];
}

export interface ModuleScaffold {
  manifest: ExternalModuleManifest;
  files: Record<string, string>;
  entryFile: string;
}

function normalizeId(value: unknown): string {
  const id = String(value ?? "").trim().toLowerCase();
  if (!/^[a-z0-9][a-z0-9.-]{0,63}$/.test(id)) {
    throw new Error("Module id must be 1–64 characters using lowercase letters, numbers, dots or hyphens.");
  }
  return id;
}

function normalizeName(value: unknown): string {
  const name = String(value ?? "").trim();
  if (!name || name.length > 80) throw new Error("Module name must be 1–80 characters.");
  return name;
}

function normalizeCommand(value: unknown): string {
  const command = String(value ?? "hello").trim().toLowerCase();
  if (!/^[a-z0-9][a-z0-9_-]{0,63}$/.test(command)) {
    throw new Error("First command must use letters, numbers, _ or -.");
  }
  return command;
}

const SCAFFOLD_FEATURES = new Set<ModuleScaffoldFeature>(["events", "jobs", "storage", "services", "media", "lifecycle"]);

function normalizeFeatures(value: unknown): ModuleScaffoldFeature[] {
  if (value === undefined) return [];
  if (!Array.isArray(value)) throw new Error("Module features must be an array.");
  const result = new Set<ModuleScaffoldFeature>();
  for (const feature of value) {
    if (typeof feature !== "string" || !SCAFFOLD_FEATURES.has(feature as ModuleScaffoldFeature)) {
      throw new Error("Unsupported module feature: " + String(feature));
    }
    result.add(feature as ModuleScaffoldFeature);
  }
  return [...result];
}

function pythonWorker(moduleName: string, commandName: string): string {
  return `import json\nimport sys\n\n\ndef send(payload):\n    sys.stdout.write(json.dumps(payload) + "\\n")\n    sys.stdout.flush()\n\nfor line in sys.stdin:\n    line = line.strip()\n    if not line:\n        continue\n    request = json.loads(line)\n    if request.get("type") in ("event.dispatch", "job.execute", "lifecycle.start", "lifecycle.stop"):\n        send({"protocol": 1, "replyTo": request["id"], "ok": True, "actions": []})\n        continue\n    if request.get("type") != "command.execute":\n        continue\n\n    reply = "Hello from ${moduleName}!"\n    if request.get("commandId") != "${commandName}":\n        reply = "Unknown command: " + str(request.get("commandId"))\n\n    send({\n        "protocol": 1,\n        "replyTo": request["id"],\n        "ok": True,\n        "actions": [{"type": "reply", "text": reply}],\n    })\n`;
}

function javascriptWorker(moduleName: string, commandName: string): string {
  return `import readline from "node:readline";\n\nconst input = readline.createInterface({ input: process.stdin });\n\ninput.on("line", (line) => {\n  if (!line.trim()) return;\n  const request = JSON.parse(line);\n  if (["event.dispatch", "job.execute", "lifecycle.start", "lifecycle.stop"].includes(request.type)) {\n    process.stdout.write(JSON.stringify({ protocol: 1, replyTo: request.id, ok: true, actions: [] }) + "\\n");\n    return;\n  }\n  if (request.type !== "command.execute") return;\n\n  const text = request.commandId === "${commandName}"\n    ? "Hello from ${moduleName}!"\n    : \`Unknown command: \${request.commandId}\`;\n\n  process.stdout.write(JSON.stringify({\n    protocol: 1,\n    replyTo: request.id,\n    ok: true,\n    actions: [{ type: "reply", text }],\n  }) + "\\n");\n});\n`;
}

export function createModuleScaffold(input: ModuleScaffoldInput): ModuleScaffold {
  const id = normalizeId(input.id);
  const name = normalizeName(input.name);
  const commandName = normalizeCommand(input.firstCommand);
  const section = String(input.firstSection ?? name).trim() || name;
  if (section.length > 80) throw new Error("Section must be 80 characters or fewer.");

  const description = String(input.description ?? "").trim();
  if (description.length > 240) throw new Error("Module description must be 240 characters or fewer.");

  const runtime = input.runtime;
  if (runtime !== "python" && runtime !== "javascript") throw new Error("Unsupported module runtime.");
  const features = normalizeFeatures(input.features);
  const capabilities: ExternalModuleCapability[] = ["commands", "settings", ...features];

  const entryFile = runtime === "python" ? "main.py" : "main.mjs";
  const runtimeCommand = runtime === "python" ? "python" : "bailey-node";
  const manifest: ExternalModuleManifest = {
    protocol: BAILEY_MODULE_PROTOCOL,
    id,
    name,
    version: "1.0.0",
    description: description || `${name} module for Bailey Host.`,
    runtime: { command: runtimeCommand, args: [entryFile], restart: "on-failure" },
    commands: [
      {
        id: commandName,
        name: commandName,
        section,
        description: `Starter command for ${name}.`,
      },
    ],
    settings: [],
    capabilities,
    ...(features.includes("events") ? { events: ["message.received" as const] } : {}),
    // The generated starter command replies to the triggering WhatsApp message.
    // This is a request only; Bailey waits for a user grant before sending.
    permissions: ["whatsapp.send"],
  };

  const files: Record<string, string> = {
    "bailey.module.json": `${JSON.stringify(manifest, null, 2)}\n`,
    [entryFile]: runtime === "python" ? pythonWorker(name, commandName) : javascriptWorker(name, commandName),
    "README.md": `# ${name}\n\nGenerated by Bailey Studio.\n\n- Module id: \`${id}\`\n- Runtime: ${runtime}\n- Starter command: \`${commandName}\`\n\nOptional host features: ${features.join(", ") || "none"}.\n\nEdit \`bailey.module.json\` to add commands, jobs, event subscriptions, settings and requested permissions. Settings declared there automatically appear in Bailey Configuration.\n`,
  };
  if (runtime === "python") {
    files["requirements.txt"] = "# Add Python packages here, one per line.\n";
  } else {
    files["package.json"] = `${JSON.stringify({ name: `bailey-module-${id}`, private: true, type: "module", dependencies: {} }, null, 2)}\n`;
  }

  return { manifest, files, entryFile };
}
