import { randomUUID } from "node:crypto";
import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { mkdir, readFile, readdir } from "node:fs/promises";
import { join } from "node:path";
import type { BaileyModuleDefinition, CommandContext } from "../core/module";
import { defineCommand, defineModule } from "../core/module";
import {
  BAILEY_MODULE_PROTOCOL,
  parseExternalModuleManifest,
  parseExternalModuleResponse,
  type ExternalModuleAction,
  type ExternalModuleManifest,
  type ExternalModuleRequest,
} from "./protocol";

interface PendingRequest {
  resolve(actions: ExternalModuleAction[]): void;
  reject(error: Error): void;
  timer: NodeJS.Timeout;
}

interface LoadedModule {
  directory: string;
  manifest: ExternalModuleManifest;
  process?: ChildProcessWithoutNullStreams;
  stdoutBuffer: string;
  pending: Map<string, PendingRequest>;
}

export interface ExternalModuleLoadResult {
  definitions: BaileyModuleDefinition[];
  errors: Array<{ folder: string; error: string }>;
}

export class ExternalModuleManager {
  private readonly modules = new Map<string, LoadedModule>();

  constructor(
    private readonly modulesRoot: string,
    private readonly getEnvironment: (moduleId: string) => Record<string, string>,
  ) {}

  async load(): Promise<ExternalModuleLoadResult> {
    await mkdir(this.modulesRoot, { recursive: true });
    const entries = await readdir(this.modulesRoot, { withFileTypes: true });
    const definitions: BaileyModuleDefinition[] = [];
    const errors: Array<{ folder: string; error: string }> = [];

    for (const entry of entries) {
      if (!entry.isDirectory()) continue;
      const directory = join(this.modulesRoot, entry.name);
      try {
        const raw = await readFile(join(directory, "bailey.module.json"), "utf8");
        const manifest = parseExternalModuleManifest(JSON.parse(raw));
        if (this.modules.has(manifest.id)) throw new Error(`Duplicate external module id: ${manifest.id}`);
        const loaded: LoadedModule = { directory, manifest, stdoutBuffer: "", pending: new Map() };
        this.modules.set(manifest.id, loaded);
        definitions.push(this.toDefinition(loaded));
      } catch (error) {
        const code = (error as NodeJS.ErrnoException).code;
        if (code === "ENOENT") continue;
        errors.push({ folder: entry.name, error: error instanceof Error ? error.message : String(error) });
      }
    }

    return { definitions, errors };
  }

  private toDefinition(loaded: LoadedModule): BaileyModuleDefinition {
    const { manifest } = loaded;
    return defineModule({
      id: manifest.id,
      name: manifest.name,
      version: manifest.version,
      description: manifest.description,
      settings: manifest.settings ?? [],
      commands: (manifest.commands ?? []).map((command) => defineCommand({
        id: command.id,
        name: command.name,
        section: command.section ?? manifest.name,
        description: command.description,
        aliases: command.aliases ?? [],
        execute: async (context) => this.executeCommand(manifest.id, command.id, context),
      })),
    });
  }

  private ensureProcess(moduleId: string): ChildProcessWithoutNullStreams {
    const loaded = this.modules.get(moduleId);
    if (!loaded) throw new Error(`External module is not loaded: ${moduleId}`);
    if (loaded.process && !loaded.process.killed) return loaded.process;

    const child = spawn(
      loaded.manifest.runtime.command,
      loaded.manifest.runtime.args ?? [],
      {
        cwd: loaded.directory,
        env: {
          ...process.env,
          ...this.getEnvironment(moduleId),
          BAILEY_MODULE_ID: moduleId,
          BAILEY_MODULE_PROTOCOL: String(BAILEY_MODULE_PROTOCOL),
        },
        shell: false,
        windowsHide: true,
        stdio: ["pipe", "pipe", "pipe"],
      },
    );

    loaded.process = child;
    loaded.stdoutBuffer = "";

    child.stdout.setEncoding("utf8");
    child.stdout.on("data", (chunk: string) => {
      loaded.stdoutBuffer += chunk;
      while (true) {
        const newline = loaded.stdoutBuffer.indexOf("\n");
        if (newline < 0) break;
        const line = loaded.stdoutBuffer.slice(0, newline).trim();
        loaded.stdoutBuffer = loaded.stdoutBuffer.slice(newline + 1);
        if (!line) continue;
        this.handleResponse(loaded, line);
      }
    });

    child.stderr.setEncoding("utf8");
    child.stderr.on("data", (chunk: string) => {
      const text = chunk.trim();
      if (text) console.warn(`[module:${moduleId}] ${text}`);
    });

    const failPending = (reason: Error) => {
      for (const pending of loaded.pending.values()) {
        clearTimeout(pending.timer);
        pending.reject(reason);
      }
      loaded.pending.clear();
      loaded.process = undefined;
    };

    child.on("error", (error) => failPending(new Error(`Could not start ${loaded.manifest.name}: ${error.message}`)));
    child.on("exit", (code, signal) => {
      if (loaded.pending.size) {
        failPending(new Error(`${loaded.manifest.name} stopped before replying (code ${String(code)}, signal ${String(signal)}).`));
      } else {
        loaded.process = undefined;
      }
    });

    return child;
  }

  private handleResponse(loaded: LoadedModule, line: string): void {
    try {
      const response = parseExternalModuleResponse(JSON.parse(line));
      const pending = loaded.pending.get(response.replyTo);
      if (!pending) return;
      clearTimeout(pending.timer);
      loaded.pending.delete(response.replyTo);
      if (!response.ok) {
        pending.reject(new Error(response.error || `${loaded.manifest.name} returned an error.`));
        return;
      }
      pending.resolve(response.actions ?? []);
    } catch (error) {
      console.warn(`[module:${loaded.manifest.id}] Ignored invalid protocol output: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  private request(moduleId: string, request: ExternalModuleRequest): Promise<ExternalModuleAction[]> {
    const loaded = this.modules.get(moduleId);
    if (!loaded) return Promise.reject(new Error(`External module is not loaded: ${moduleId}`));
    const child = this.ensureProcess(moduleId);

    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        loaded.pending.delete(request.id);
        reject(new Error(`${loaded.manifest.name} did not respond within 30 seconds.`));
      }, 30_000);
      loaded.pending.set(request.id, { resolve, reject, timer });
      child.stdin.write(`${JSON.stringify(request)}\n`, "utf8", (error) => {
        if (!error) return;
        clearTimeout(timer);
        loaded.pending.delete(request.id);
        reject(error);
      });
    });
  }

  async executeCommand(moduleId: string, commandId: string, context: CommandContext): Promise<void> {
    const actions = await this.request(moduleId, {
      protocol: BAILEY_MODULE_PROTOCOL,
      id: randomUUID(),
      type: "command.execute",
      commandId,
      context: {
        remoteJid: context.remoteJid,
        senderJid: context.senderJid,
        text: context.text,
        args: context.args,
      },
    });

    for (const action of actions) {
      switch (action.type) {
        case "reply":
          await context.reply(action.text);
          break;
        case "react":
          await context.react(action.emoji);
          break;
        case "log":
          console[action.level === "error" ? "error" : action.level === "warn" ? "warn" : "log"](`[module:${moduleId}] ${action.message}`);
          break;
      }
    }
  }

  async stopAll(): Promise<void> {
    for (const loaded of this.modules.values()) {
      const child = loaded.process;
      if (!child || child.killed) continue;
      child.stdin.end();
      child.kill();
      loaded.process = undefined;
    }
  }
}
