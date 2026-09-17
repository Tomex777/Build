import { randomUUID } from "node:crypto";
import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { mkdir, readFile, readdir } from "node:fs/promises";
import { join } from "node:path";
import type { BaileyModuleDefinition, CommandContext, MessageEventContext } from "../core/module";
import { defineCommand, defineModule } from "../core/module";
import {
  BAILEY_MODULE_PROTOCOL,
  parseExternalHostCall,
  parseExternalModuleManifest,
  parseExternalModuleResponse,
  type ExternalHostResult,
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
  dataDirectory?: string;
  manifest: ExternalModuleManifest;
  process?: ChildProcessWithoutNullStreams;
  stdoutBuffer: string;
  pending: Map<string, PendingRequest>;
}

export interface ExternalModuleLoadResult {
  definitions: BaileyModuleDefinition[];
  errors: Array<{ folder: string; error: string }>;
}

export interface ExternalHostServiceContext {
  moduleId: string;
  moduleName: string;
  capabilities: readonly string[];
  dataDirectory?: string;
}

export type ExternalHostServiceHandler = (
  params: unknown,
  context: ExternalHostServiceContext,
) => unknown | Promise<unknown>;

type HostSendText = (remoteJid: string, text: string) => void | Promise<void>;
type TriggerContext = Pick<CommandContext | MessageEventContext, "reply" | "react">;

const SERVICE_NAME = /^[a-z0-9][a-z0-9.-]{0,63}$/;
const SERVICE_METHOD = /^[a-z0-9][a-z0-9._-]{0,63}$/;

export class ExternalModuleManager {
  private readonly modules = new Map<string, LoadedModule>();
  private readonly jobTimers = new Map<string, NodeJS.Timeout>();
  private readonly runningJobs = new Set<string>();
  private readonly services = new Map<string, ExternalHostServiceHandler>();
  private hostSendText?: HostSendText;
  private jobsStarted = false;

  constructor(
    private readonly modulesRoot: string,
    private readonly getEnvironment: (moduleId: string) => Record<string, string>,
    private readonly isModuleEnabled: (moduleId: string) => boolean = () => true,
    private readonly dataRoot: string = join(modulesRoot, ".data"),
  ) {
    this.registerService("host", "info", (_params, context) => ({
      protocol: BAILEY_MODULE_PROTOCOL,
      moduleId: context.moduleId,
      moduleName: context.moduleName,
      capabilities: context.capabilities,
      dataDirectory: context.dataDirectory,
    }));
  }

  setHostSendText(handler: HostSendText): void {
    this.hostSendText = handler;
  }

  registerService(service: string, method: string, handler: ExternalHostServiceHandler): void {
    if (!SERVICE_NAME.test(service)) throw new Error(`Invalid host service name: ${service}`);
    if (!SERVICE_METHOD.test(method)) throw new Error(`Invalid host service method: ${method}`);
    const key = `${service}:${method}`;
    if (this.services.has(key)) throw new Error(`Host service already registered: ${service}.${method}`);
    this.services.set(key, handler);
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
        const dataDirectory = manifest.capabilities?.includes("storage") ? join(this.dataRoot, manifest.id) : undefined;
        if (dataDirectory) await mkdir(dataDirectory, { recursive: true });
        const loaded: LoadedModule = { directory, dataDirectory, manifest, stdoutBuffer: "", pending: new Map() };
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
          ...(loaded.dataDirectory ? { BAILEY_MODULE_DATA_DIR: loaded.dataDirectory } : {}),
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
        void this.handleOutput(loaded, child, line);
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

  private async handleOutput(loaded: LoadedModule, child: ChildProcessWithoutNullStreams, line: string): Promise<void> {
    try {
      const raw = JSON.parse(line) as unknown;
      if (raw && typeof raw === "object" && (raw as { type?: unknown }).type === "host.call") {
        const call = parseExternalHostCall(raw);
        await this.handleHostCall(loaded, child, call.id, call.service, call.method, call.params);
        return;
      }

      const response = parseExternalModuleResponse(raw);
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

  private async handleHostCall(
    loaded: LoadedModule,
    child: ChildProcessWithoutNullStreams,
    callId: string,
    service: string,
    method: string,
    params: unknown,
  ): Promise<void> {
    if (!(loaded.manifest.capabilities?.includes("services") ?? false)) {
      await this.writeHostResult(child, {
        protocol: BAILEY_MODULE_PROTOCOL,
        type: "host.result",
        replyTo: callId,
        ok: false,
        error: `${loaded.manifest.id} must declare the services capability before calling host services.`,
      });
      return;
    }

    const handler = this.services.get(`${service}:${method}`);
    if (!handler) {
      await this.writeHostResult(child, {
        protocol: BAILEY_MODULE_PROTOCOL,
        type: "host.result",
        replyTo: callId,
        ok: false,
        error: `Unknown host service: ${service}.${method}`,
      });
      return;
    }

    try {
      const result = await handler(params, {
        moduleId: loaded.manifest.id,
        moduleName: loaded.manifest.name,
        capabilities: loaded.manifest.capabilities ?? [],
        dataDirectory: loaded.dataDirectory,
      });
      await this.writeHostResult(child, {
        protocol: BAILEY_MODULE_PROTOCOL,
        type: "host.result",
        replyTo: callId,
        ok: true,
        result,
      });
    } catch (error) {
      await this.writeHostResult(child, {
        protocol: BAILEY_MODULE_PROTOCOL,
        type: "host.result",
        replyTo: callId,
        ok: false,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  private async writeHostResult(child: ChildProcessWithoutNullStreams, result: ExternalHostResult): Promise<void> {
    await new Promise<void>((resolve, reject) => {
      child.stdin.write(`${JSON.stringify(result)}\n`, "utf8", (error) => {
        if (error) reject(error);
        else resolve();
      });
    });
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
    await Promise.all([...this.modules.values()].map(async (loaded) => {
      const child = loaded.process;
      if (!child) return;
      if (child.exitCode !== null || child.signalCode !== null) {
        loaded.process = undefined;
        return;
      }

      await new Promise<void>((resolve) => {
        let settled = false;
        const finish = () => {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          resolve();
        };
        const timer = setTimeout(finish, 2500);
        child.once("exit", finish);
        child.once("error", finish);
        child.stdin.end();
        if (!child.killed) child.kill();
      });
      loaded.process = undefined;
    }));
  }
}
