import { randomUUID } from "node:crypto";
import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { mkdir, readFile, readdir } from "node:fs/promises";
import { join } from "node:path";
import type { BaileyModuleDefinition, CommandContext, MessageEventContext } from "../core/module";
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

type HostSendText = (remoteJid: string, text: string) => void | Promise<void>;
type TriggerContext = Pick<CommandContext | MessageEventContext, "reply" | "react">;

export class ExternalModuleManager {
  private readonly modules = new Map<string, LoadedModule>();
  private readonly jobTimers = new Map<string, NodeJS.Timeout>();
  private readonly runningJobs = new Set<string>();
  private hostSendText?: HostSendText;
  private jobsStarted = false;

  constructor(
    private readonly modulesRoot: string,
    private readonly getEnvironment: (moduleId: string) => Record<string, string>,
    private readonly isModuleEnabled: (moduleId: string) => boolean = () => true,
  ) {}

  setHostSendText(handler: HostSendText): void {
    this.hostSendText = handler;
  }

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
    const listensForEvents = manifest.capabilities?.includes("events") ?? false;
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
      onMessage: listensForEvents
        ? async (context) => this.dispatchMessageEvent(manifest.id, context)
        : undefined,
    });
  }

  private ensureProcess(moduleId: string): ChildProcessWithoutNullStreams {
    const loaded = this.modules.get(moduleId);
    if (!loaded) throw new Error(`External module is not loaded: ${moduleId}`);
    if (loaded.process && !loaded.process.killed) return loaded.process;

    const useEmbeddedNode = loaded.manifest.runtime.command === "bailey-node";
    const executable = useEmbeddedNode ? process.execPath : loaded.manifest.runtime.command;
    const child = spawn(
      executable,
      loaded.manifest.runtime.args ?? [],
      {
        cwd: loaded.directory,
        env: {
          ...process.env,
          ...this.getEnvironment(moduleId),
          ...(useEmbeddedNode ? { ELECTRON_RUN_AS_NODE: "1" } : {}),
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

  private async applyActions(moduleId: string, actions: ExternalModuleAction[], context?: TriggerContext): Promise<void> {
    for (const action of actions) {
      switch (action.type) {
        case "reply":
          if (!context) throw new Error(`${moduleId} returned reply from a request without a triggering message.`);
          await context.reply(action.text);
          break;
        case "react":
          if (!context) throw new Error(`${moduleId} returned react from a request without a triggering message.`);
          await context.react(action.emoji);
          break;
        case "send":
          if (!this.hostSendText) throw new Error(`${moduleId} tried to send a message before Bailey's WhatsApp host was ready.`);
          await this.hostSendText(action.remoteJid, action.text);
          break;
        case "log":
          console[action.level === "error" ? "error" : action.level === "warn" ? "warn" : "log"](`[module:${moduleId}] ${action.message}`);
          break;
      }
    }
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
    await this.applyActions(moduleId, actions, context);
  }

  async dispatchMessageEvent(moduleId: string, context: MessageEventContext): Promise<void> {
    const loaded = this.modules.get(moduleId);
    if (!loaded) throw new Error(`External module is not loaded: ${moduleId}`);
    if (!(loaded.manifest.capabilities?.includes("events") ?? false)) return;

    const actions = await this.request(moduleId, {
      protocol: BAILEY_MODULE_PROTOCOL,
      id: randomUUID(),
      type: "event.dispatch",
      event: "message.received",
      context: {
        remoteJid: context.remoteJid,
        senderJid: context.senderJid,
        text: context.text,
        pushName: context.pushName,
        timestamp: context.timestamp,
      },
    });
    await this.applyActions(moduleId, actions, context);
  }

  async executeJob(moduleId: string, jobId: string, scheduledAt = Date.now()): Promise<void> {
    const loaded = this.modules.get(moduleId);
    if (!loaded) throw new Error(`External module is not loaded: ${moduleId}`);
    if (!this.isModuleEnabled(moduleId)) return;
    if (!(loaded.manifest.capabilities?.includes("jobs") ?? false)) throw new Error(`${moduleId} does not declare the jobs capability.`);
    if (!(loaded.manifest.jobs ?? []).some((job) => job.id === jobId)) throw new Error(`Unknown job ${jobId} in ${moduleId}.`);

    const key = `${moduleId}:${jobId}`;
    if (this.runningJobs.has(key)) {
      console.warn(`[module:${moduleId}] Skipped overlapping job ${jobId}.`);
      return;
    }

    this.runningJobs.add(key);
    try {
      const actions = await this.request(moduleId, {
        protocol: BAILEY_MODULE_PROTOCOL,
        id: randomUUID(),
        type: "job.execute",
        jobId,
        scheduledAt,
      });
      await this.applyActions(moduleId, actions);
    } finally {
      this.runningJobs.delete(key);
    }
  }

  startJobs(): void {
    if (this.jobsStarted) return;
    this.jobsStarted = true;

    for (const loaded of this.modules.values()) {
      if (!(loaded.manifest.capabilities?.includes("jobs") ?? false)) continue;
      for (const job of loaded.manifest.jobs ?? []) {
        const key = `${loaded.manifest.id}:${job.id}`;
        if (job.runOnStart) {
          void this.executeJob(loaded.manifest.id, job.id).catch((error) => {
            console.error(`[module:${loaded.manifest.id}] job ${job.id} failed`, error);
          });
        }
        const timer = setInterval(() => {
          void this.executeJob(loaded.manifest.id, job.id).catch((error) => {
            console.error(`[module:${loaded.manifest.id}] job ${job.id} failed`, error);
          });
        }, job.intervalSeconds * 1000);
        timer.unref?.();
        this.jobTimers.set(key, timer);
      }
    }
  }

  stopJobs(): void {
    for (const timer of this.jobTimers.values()) clearInterval(timer);
    this.jobTimers.clear();
    this.runningJobs.clear();
    this.jobsStarted = false;
  }

  async stopAll(): Promise<void> {
    this.stopJobs();
    for (const loaded of this.modules.values()) {
      const child = loaded.process;
      if (!child || child.killed) continue;
      child.stdin.end();
      child.kill();
      loaded.process = undefined;
    }
  }
}
