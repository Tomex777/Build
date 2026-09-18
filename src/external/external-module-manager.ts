import { randomUUID } from "node:crypto";
import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import type { BaileyModuleDefinition, CommandContext, MessageEventContext } from "../core/module";
import { defineCommand, defineModule } from "../core/module";
import {
  BAILEY_MODULE_PROTOCOL,
  parseExternalHostCall,
  parseExternalModuleManifest,
  parseExternalModuleResponse,
  type ExternalHostResult,
  type ExternalMediaSend,
  type ExternalModuleAction,
  type ExternalModuleEventName,
  type ExternalModuleJobManifest,
  type ExternalModuleManifest,
  type ExternalModuleRequest,
} from "./protocol";

interface PendingRequest {
  resolve(actions: ExternalModuleAction[]): void;
  reject(error: Error): void;
  timer: NodeJS.Timeout;
}

interface ModuleDiagnostics {
  startedAt?: number;
  lastExitAt?: number;
  lastExitCode?: number | null;
  lastSignal?: NodeJS.Signals | null;
  crashCount: number;
  restartCount: number;
  lastError?: string;
  logs: string[];
}

interface LoadedModule {
  directory: string;
  dataDirectory?: string;
  manifest: ExternalModuleManifest;
  process?: ChildProcessWithoutNullStreams;
  stdoutBuffer: string;
  pending: Map<string, PendingRequest>;
  lifecycleStarted: boolean;
  intentionalStop: boolean;
  diagnostics: ModuleDiagnostics;
}

interface RegisteredService {
  handler: ExternalHostServiceHandler;
  permission?: string;
}

export interface ExternalModuleLoadResult {
  definitions: BaileyModuleDefinition[];
  errors: Array<{ folder: string; error: string }>;
}

export interface ExternalModuleRuntimeStatus {
  id: string;
  name: string;
  running: boolean;
  pid?: number;
  startedAt?: number;
  lastExitAt?: number;
  lastExitCode?: number | null;
  lastSignal?: NodeJS.Signals | null;
  crashCount: number;
  restartCount: number;
  lastError?: string;
  capabilities: readonly string[];
  permissions: readonly string[];
  grantedPermissions: readonly string[];
  logs: readonly string[];
}

export interface ExternalHostServiceContext {
  moduleId: string;
  moduleName: string;
  capabilities: readonly string[];
  permissions: readonly string[];
  dataDirectory?: string;
}

export type ExternalHostServiceHandler = (
  params: unknown,
  context: ExternalHostServiceContext,
) => unknown | Promise<unknown>;

type HostSendText = (remoteJid: string, text: string) => void | Promise<void>;
type HostSendMedia = (remoteJid: string, media: ExternalMediaSend) => void | Promise<void>;
type TriggerContext = Pick<CommandContext | MessageEventContext, "reply" | "react">;

const SERVICE_NAME = /^[a-z0-9][a-z0-9.-]{0,63}$/;
const SERVICE_METHOD = /^[a-z0-9][a-z0-9._-]{0,63}$/;
const MAX_LOG_LINES = 100;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function cronFieldMatches(field: string, value: number, min: number, max: number): boolean {
  const matchesToken = (token: string): boolean => {
    const [rangePart, stepPart] = token.split("/");
    const step = stepPart === undefined ? 1 : Number(stepPart);
    if (!Number.isInteger(step) || step < 1) return false;
    let start = min;
    let end = max;
    if (rangePart !== "*") {
      const [a, b] = rangePart.split("-");
      start = Number(a);
      end = b === undefined ? start : Number(b);
      if (!Number.isInteger(start) || !Number.isInteger(end) || start < min || end > max || start > end) return false;
    }
    return value >= start && value <= end && (value - start) % step === 0;
  };
  return field.split(",").some(matchesToken);
}

export function nextCronTime(expression: string, after = Date.now()): number {
  const fields = expression.trim().split(/\s+/);
  if (fields.length !== 5) throw new Error("Cron expression must contain five fields.");
  const [minute, hour, day, month, weekday] = fields;
  const date = new Date(after);
  date.setSeconds(0, 0);
  date.setMinutes(date.getMinutes() + 1);
  const max = after + 366 * 24 * 60 * 60 * 1000;

  for (let time = date.getTime(); time <= max; time += 60_000) {
    const cursor = new Date(time);
    if (
      cronFieldMatches(minute, cursor.getMinutes(), 0, 59)
      && cronFieldMatches(hour, cursor.getHours(), 0, 23)
      && cronFieldMatches(day, cursor.getDate(), 1, 31)
      && cronFieldMatches(month, cursor.getMonth() + 1, 1, 12)
      && cronFieldMatches(weekday, cursor.getDay(), 0, 6)
    ) return time;
  }
  throw new Error("Cron expression did not produce a run within one year.");
}

export class ExternalModuleManager {
  private readonly modules = new Map<string, LoadedModule>();
  private readonly jobTimers = new Map<string, NodeJS.Timeout>();
  private readonly runningJobs = new Set<string>();
  private readonly services = new Map<string, RegisteredService>();
  private readonly restartTimers = new Map<string, NodeJS.Timeout>();
  private schedulerState: { version: 1; jobs: Record<string, { lastScheduledAt?: number; lastCompletedAt?: number; nextRunAt?: number }> } = { version: 1, jobs: {} };
  private hostSendText?: HostSendText;
  private hostSendMedia?: HostSendMedia;
  private jobsStarted = false;
  private stopping = false;

  constructor(
    private readonly modulesRoot: string,
    private readonly getEnvironment: (moduleId: string) => Record<string, string>,
    private readonly isModuleEnabled: (moduleId: string) => boolean = () => true,
    private readonly dataRoot: string = join(modulesRoot, ".data"),
    private readonly getPermissionGrants?: (moduleId: string) => readonly string[],
  ) {
    this.registerService("host", "info", (_params, context) => ({
      protocol: BAILEY_MODULE_PROTOCOL,
      moduleId: context.moduleId,
      moduleName: context.moduleName,
      capabilities: context.capabilities,
      permissions: context.permissions,
      dataDirectory: context.dataDirectory,
    }), "host.info");
  }

  setHostSendText(handler: HostSendText): void {
    this.hostSendText = handler;
  }

  setHostSendMedia(handler: HostSendMedia): void {
    this.hostSendMedia = handler;
  }

  registerService(service: string, method: string, handler: ExternalHostServiceHandler, permission?: string): void {
    if (!SERVICE_NAME.test(service)) throw new Error(`Invalid host service name: ${service}`);
    if (!SERVICE_METHOD.test(method)) throw new Error(`Invalid host service method: ${method}`);
    const key = `${service}:${method}`;
    if (this.services.has(key)) throw new Error(`Host service already registered: ${service}.${method}`);
    this.services.set(key, { handler, permission });
  }

  statuses(): ExternalModuleRuntimeStatus[] {
    return [...this.modules.values()].map((loaded) => ({
      id: loaded.manifest.id,
      name: loaded.manifest.name,
      running: Boolean(loaded.process && loaded.process.exitCode === null && !loaded.process.killed),
      pid: loaded.process?.pid,
      startedAt: loaded.diagnostics.startedAt,
      lastExitAt: loaded.diagnostics.lastExitAt,
      lastExitCode: loaded.diagnostics.lastExitCode,
      lastSignal: loaded.diagnostics.lastSignal,
      crashCount: loaded.diagnostics.crashCount,
      restartCount: loaded.diagnostics.restartCount,
      lastError: loaded.diagnostics.lastError,
      capabilities: loaded.manifest.capabilities ?? [],
      permissions: loaded.manifest.permissions ?? [],
      grantedPermissions: this.getPermissionGrants ? [...this.getPermissionGrants(loaded.manifest.id)] : [...(loaded.manifest.permissions ?? [])],
      logs: loaded.diagnostics.logs,
    }));
  }

  private log(loaded: LoadedModule, message: string): void {
    loaded.diagnostics.logs.push(`${new Date().toISOString()} ${message}`);
    if (loaded.diagnostics.logs.length > MAX_LOG_LINES) loaded.diagnostics.logs.splice(0, loaded.diagnostics.logs.length - MAX_LOG_LINES);
  }

  private permissionMatches(values: readonly string[], permission: string): boolean {
    if (values.includes("*") || values.includes(permission)) return true;
    const parts = permission.split(".");
    for (let i = parts.length - 1; i >= 1; i -= 1) {
      if (values.includes(`${parts.slice(0, i).join(".")}.*`)) return true;
    }
    return false;
  }

  private hasPermission(loaded: LoadedModule, permission: string): boolean {
    if (permission === "host.info") return true;
    const requested = loaded.manifest.permissions ?? [];
    if (!this.permissionMatches(requested, permission)) return false;
    const granted = this.getPermissionGrants ? this.getPermissionGrants(loaded.manifest.id) : requested;
    return this.permissionMatches(granted, permission);
  }

  async load(): Promise<ExternalModuleLoadResult> {
    this.stopping = false;
    await mkdir(this.modulesRoot, { recursive: true });
    await mkdir(this.dataRoot, { recursive: true });
    try {
      const parsed = JSON.parse(await readFile(join(this.dataRoot, ".scheduler.json"), "utf8")) as typeof this.schedulerState;
      if (parsed.version === 1 && parsed.jobs && typeof parsed.jobs === "object") this.schedulerState = parsed;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") console.warn("[scheduler] Could not load scheduler state", error);
    }
    const entries = await readdir(this.modulesRoot, { withFileTypes: true });
    const definitions: BaileyModuleDefinition[] = [];
    const errors: Array<{ folder: string; error: string }> = [];

    for (const entry of entries) {
      if (!entry.isDirectory() || entry.name === ".data") continue;
      const directory = join(this.modulesRoot, entry.name);
      try {
        const raw = await readFile(join(directory, "bailey.module.json"), "utf8");
        const manifest = parseExternalModuleManifest(JSON.parse(raw));
        if (this.modules.has(manifest.id)) throw new Error(`Duplicate external module id: ${manifest.id}`);
        const dataDirectory = manifest.capabilities?.includes("storage") ? join(this.dataRoot, manifest.id) : undefined;
        if (dataDirectory) await mkdir(dataDirectory, { recursive: true });
        const loaded: LoadedModule = {
          directory, dataDirectory, manifest, stdoutBuffer: "", pending: new Map(),
          lifecycleStarted: false, intentionalStop: false,
          diagnostics: { crashCount: 0, restartCount: 0, logs: [] },
        };
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
      onMessage: this.subscribesTo(loaded, "message.received")
        ? async (context) => this.dispatchMessageEvent(manifest.id, context)
        : undefined,
    });
  }

  private subscribesTo(loaded: LoadedModule, event: ExternalModuleEventName): boolean {
    if (!(loaded.manifest.capabilities?.includes("events") ?? false)) return false;
    const subscriptions: ExternalModuleEventName[] = loaded.manifest.events?.length ? loaded.manifest.events : ["message.received"];
    return subscriptions.includes(event);
  }

  private scheduleRestart(loaded: LoadedModule): void {
    if (this.stopping || loaded.intentionalStop || !this.isModuleEnabled(loaded.manifest.id)) return;
    if ((loaded.manifest.runtime.restart ?? "on-failure") === "never" || this.restartTimers.has(loaded.manifest.id)) return;
    const delay = Math.min(30_000, Math.max(1000, loaded.diagnostics.crashCount * 1000));
    const timer = setTimeout(() => {
      this.restartTimers.delete(loaded.manifest.id);
      if (this.stopping || !this.isModuleEnabled(loaded.manifest.id)) return;
      try {
        loaded.diagnostics.restartCount += 1;
        this.ensureProcess(loaded.manifest.id);
      } catch (error) {
        loaded.diagnostics.lastError = error instanceof Error ? error.message : String(error);
        this.log(loaded, `restart failed: ${loaded.diagnostics.lastError}`);
      }
    }, delay);
    timer.unref?.();
    this.restartTimers.set(loaded.manifest.id, timer);
  }

  private ensureProcess(moduleId: string): ChildProcessWithoutNullStreams {
    const loaded = this.modules.get(moduleId);
    if (!loaded) throw new Error(`External module is not loaded: ${moduleId}`);
    if (loaded.process && loaded.process.exitCode === null && !loaded.process.killed) return loaded.process;

    const runtimeCommand = loaded.manifest.runtime.command;
    const useEmbeddedNode = runtimeCommand === "bailey-node";
    const pythonAlias = ["python", "python3", "py", "bailey-python"].includes(runtimeCommand);
    const managedPython = process.platform === "win32"
      ? join(loaded.directory, ".bailey-venv", "Scripts", "python.exe")
      : join(loaded.directory, ".bailey-venv", "bin", "python");
    const useManagedPython = pythonAlias && existsSync(managedPython);
    const executable = useEmbeddedNode
      ? process.execPath
      : useManagedPython
        ? managedPython
        : runtimeCommand === "bailey-python"
          ? (process.platform === "win32" ? "python" : "python3")
          : runtimeCommand;
    const child = spawn(executable, loaded.manifest.runtime.args ?? [], {
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
    });

    loaded.process = child;
    loaded.intentionalStop = false;
    loaded.lifecycleStarted = false;
    loaded.stdoutBuffer = "";
    loaded.diagnostics.startedAt = Date.now();
    loaded.diagnostics.lastError = undefined;
    this.log(loaded, `worker started pid=${String(child.pid ?? "unknown")}`);

    child.stdout.setEncoding("utf8");
    child.stdout.on("data", (chunk: string) => {
      loaded.stdoutBuffer += chunk;
      while (true) {
        const newline = loaded.stdoutBuffer.indexOf("\n");
        if (newline < 0) break;
        const line = loaded.stdoutBuffer.slice(0, newline).trim();
        loaded.stdoutBuffer = loaded.stdoutBuffer.slice(newline + 1);
        if (line) void this.handleOutput(loaded, child, line);
      }
    });

    child.stderr.setEncoding("utf8");
    child.stderr.on("data", (chunk: string) => {
      const message = chunk.trim();
      if (message) {
        this.log(loaded, `stderr: ${message}`);
        console.warn(`[module:${moduleId}] ${message}`);
      }
    });

    const failPending = (reason: Error) => {
      for (const pending of loaded.pending.values()) {
        clearTimeout(pending.timer);
        pending.reject(reason);
      }
      loaded.pending.clear();
    };

    child.on("error", (error) => {
      loaded.diagnostics.lastError = error.message;
      this.log(loaded, `worker error: ${error.message}`);
      failPending(new Error(`Could not start ${loaded.manifest.name}: ${error.message}`));
    });

    child.on("exit", (code, signal) => {
      if (loaded.process !== child) return;
      failPending(new Error(`${loaded.manifest.name} stopped before replying (code ${String(code)}, signal ${String(signal)}).`));
      loaded.process = undefined;
      loaded.lifecycleStarted = false;
      loaded.diagnostics.lastExitAt = Date.now();
      loaded.diagnostics.lastExitCode = code;
      loaded.diagnostics.lastSignal = signal;
      this.log(loaded, `worker exited code=${String(code)} signal=${String(signal)}`);
      if (!loaded.intentionalStop && (code ?? 1) !== 0) {
        loaded.diagnostics.crashCount += 1;
        this.scheduleRestart(loaded);
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
      this.log(loaded, `invalid stdout: ${error instanceof Error ? error.message : String(error)}`);
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
        protocol: BAILEY_MODULE_PROTOCOL, type: "host.result", replyTo: callId, ok: false,
        error: `${loaded.manifest.id} must declare the services capability before calling host services.`,
      });
      return;
    }

    const registered = this.services.get(`${service}:${method}`);
    if (!registered) {
      await this.writeHostResult(child, {
        protocol: BAILEY_MODULE_PROTOCOL, type: "host.result", replyTo: callId, ok: false,
        error: `Unknown host service: ${service}.${method}`,
      });
      return;
    }

    const permission = registered.permission ?? `${service}.${method}`;
    if (!this.hasPermission(loaded, permission)) {
      await this.writeHostResult(child, {
        protocol: BAILEY_MODULE_PROTOCOL, type: "host.result", replyTo: callId, ok: false,
        error: `Permission denied: ${permission}`,
      });
      return;
    }

    try {
      const result = await registered.handler(params, {
        moduleId: loaded.manifest.id,
        moduleName: loaded.manifest.name,
        capabilities: loaded.manifest.capabilities ?? [],
        permissions: loaded.manifest.permissions ?? [],
        dataDirectory: loaded.dataDirectory,
      });
      await this.writeHostResult(child, { protocol: BAILEY_MODULE_PROTOCOL, type: "host.result", replyTo: callId, ok: true, result });
    } catch (error) {
      await this.writeHostResult(child, {
        protocol: BAILEY_MODULE_PROTOCOL, type: "host.result", replyTo: callId, ok: false,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  private async writeHostResult(child: ChildProcessWithoutNullStreams, result: ExternalHostResult): Promise<void> {
    await new Promise<void>((resolve, reject) => {
      child.stdin.write(`${JSON.stringify(result)}\n`, "utf8", (error) => error ? reject(error) : resolve());
    });
  }

  private requestRaw(moduleId: string, request: ExternalModuleRequest, timeoutMs = 30_000): Promise<ExternalModuleAction[]> {
    const loaded = this.modules.get(moduleId);
    if (!loaded) return Promise.reject(new Error(`External module is not loaded: ${moduleId}`));
    const child = this.ensureProcess(moduleId);
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        loaded.pending.delete(request.id);
        reject(new Error(`${loaded.manifest.name} did not respond within ${Math.ceil(timeoutMs / 1000)} seconds.`));
      }, timeoutMs);
      loaded.pending.set(request.id, { resolve, reject, timer });
      child.stdin.write(`${JSON.stringify(request)}\n`, "utf8", (error) => {
        if (!error) return;
        clearTimeout(timer);
        loaded.pending.delete(request.id);
        reject(error);
      });
    });
  }

  private async ensureLifecycle(moduleId: string): Promise<void> {
    const loaded = this.modules.get(moduleId);
    if (!loaded || loaded.lifecycleStarted || !(loaded.manifest.capabilities?.includes("lifecycle") ?? false)) return;
    const actions = await this.requestRaw(moduleId, {
      protocol: BAILEY_MODULE_PROTOCOL, id: randomUUID(), type: "lifecycle.start",
    }, 5_000);
    loaded.lifecycleStarted = true;
    await this.applyActions(moduleId, actions);
  }

  private async request(moduleId: string, request: ExternalModuleRequest): Promise<ExternalModuleAction[]> {
    if (request.type !== "lifecycle.start" && request.type !== "lifecycle.stop") await this.ensureLifecycle(moduleId);
    return this.requestRaw(moduleId, request);
  }

  private async applyActions(moduleId: string, actions: ExternalModuleAction[], context?: TriggerContext): Promise<void> {
    const loaded = this.modules.get(moduleId);
    if (!loaded) throw new Error(`External module is not loaded: ${moduleId}`);
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
          if (!this.hasPermission(loaded, "whatsapp.send")) throw new Error("Permission denied: whatsapp.send");
          if (!this.hostSendText) throw new Error(`${moduleId} tried to send a message before Bailey's WhatsApp host was ready.`);
          await this.hostSendText(action.remoteJid, action.text);
          break;
        case "send-media":
          if (!this.hasPermission(loaded, "whatsapp.send-media")) throw new Error("Permission denied: whatsapp.send-media");
          if (!this.hostSendMedia) throw new Error(`${moduleId} tried to send media before Bailey's WhatsApp host was ready.`);
          await this.hostSendMedia(action.remoteJid, action.media);
          break;
        case "log":
          this.log(loaded, `${action.level ?? "info"}: ${action.message}`);
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
      context: { remoteJid: context.remoteJid, senderJid: context.senderJid, text: context.text, args: context.args },
    });
    await this.applyActions(moduleId, actions, context);
  }

  async dispatchMessageEvent(moduleId: string, context: MessageEventContext & { id?: string; media?: unknown }): Promise<void> {
    const loaded = this.modules.get(moduleId);
    if (!loaded) throw new Error(`External module is not loaded: ${moduleId}`);
    if (!this.subscribesTo(loaded, "message.received")) return;
    const actions = await this.request(moduleId, {
      protocol: BAILEY_MODULE_PROTOCOL,
      id: randomUUID(),
      type: "event.dispatch",
      event: "message.received",
      context: {
        id: context.id,
        remoteJid: context.remoteJid,
        senderJid: context.senderJid,
        text: context.text,
        pushName: context.pushName,
        timestamp: context.timestamp,
        media: context.media as any,
      },
    });
    await this.applyActions(moduleId, actions, context);
  }

  async dispatchEventToModules(event: ExternalModuleEventName, context: Record<string, unknown>): Promise<void> {
    const targets = [...this.modules.values()].filter((loaded) => this.isModuleEnabled(loaded.manifest.id) && this.subscribesTo(loaded, event));
    const results = await Promise.allSettled(targets.map(async (loaded) => {
      const actions = await this.request(loaded.manifest.id, {
        protocol: BAILEY_MODULE_PROTOCOL, id: randomUUID(), type: "event.dispatch", event, context,
      });
      await this.applyActions(loaded.manifest.id, actions);
    }));
    results.forEach((result, index) => {
      if (result.status === "rejected") {
        const loaded = targets[index];
        loaded.diagnostics.lastError = result.reason instanceof Error ? result.reason.message : String(result.reason);
        this.log(loaded, `event ${event} failed: ${loaded.diagnostics.lastError}`);
      }
    });
  }

  async executeJob(moduleId: string, jobId: string, scheduledAt = Date.now()): Promise<void> {
    const loaded = this.modules.get(moduleId);
    if (!loaded) throw new Error(`External module is not loaded: ${moduleId}`);
    if (!this.isModuleEnabled(moduleId)) return;
    if (!(loaded.manifest.capabilities?.includes("jobs") ?? false)) throw new Error(`${moduleId} does not declare the jobs capability.`);
    if (!(loaded.manifest.jobs ?? []).some((job) => job.id === jobId)) throw new Error(`Unknown job ${jobId} in ${moduleId}.`);
    const key = `${moduleId}:${jobId}`;
    if (this.runningJobs.has(key)) {
      this.log(loaded, `skipped overlapping job ${jobId}`);
      return;
    }
    this.runningJobs.add(key);
    try {
      const actions = await this.request(moduleId, {
        protocol: BAILEY_MODULE_PROTOCOL, id: randomUUID(), type: "job.execute", jobId, scheduledAt,
      });
      await this.applyActions(moduleId, actions);
    } finally {
      this.runningJobs.delete(key);
    }
  }

  private async persistSchedulerState(): Promise<void> {
    await mkdir(this.dataRoot, { recursive: true });
    await writeFile(join(this.dataRoot, ".scheduler.json"), `${JSON.stringify(this.schedulerState, null, 2)}\n`, "utf8");
  }

  private jobState(moduleId: string, jobId: string) {
    const key = `${moduleId}:${jobId}`;
    return this.schedulerState.jobs[key] ??= {};
  }

  private async runJobWithRetry(loaded: LoadedModule, job: ExternalModuleJobManifest, scheduledAt: number): Promise<void> {
    const attempts = job.retry?.maxAttempts ?? 1;
    const backoff = (job.retry?.backoffSeconds ?? 5) * 1000;
    let lastError: unknown;
    for (let attempt = 1; attempt <= attempts; attempt += 1) {
      try {
        await this.executeJob(loaded.manifest.id, job.id, scheduledAt);
        const state = this.jobState(loaded.manifest.id, job.id);
        state.lastScheduledAt = scheduledAt;
        state.lastCompletedAt = Date.now();
        await this.persistSchedulerState();
        return;
      } catch (error) {
        lastError = error;
        loaded.diagnostics.lastError = error instanceof Error ? error.message : String(error);
        this.log(loaded, `job ${job.id} attempt ${attempt}/${attempts} failed: ${loaded.diagnostics.lastError}`);
        if (attempt < attempts) await sleep(backoff * attempt);
      }
    }
    throw lastError;
  }

  private scheduleJob(loaded: LoadedModule, job: ExternalModuleJobManifest): void {
    const key = `${loaded.manifest.id}:${job.id}`;
    const now = Date.now();
    const state = this.jobState(loaded.manifest.id, job.id);
    let next: number | undefined;

    if (job.runAt !== undefined) {
      if (state.lastCompletedAt) return;
      next = state.nextRunAt ?? job.runAt;
      if (next <= now) next = now;
    } else if (state.nextRunAt !== undefined) {
      next = state.nextRunAt <= now ? now : state.nextRunAt;
    } else if (job.intervalSeconds !== undefined) {
      next = now + job.intervalSeconds * 1000;
    } else if (job.cron !== undefined) {
      next = nextCronTime(job.cron, now);
    }
    if (next === undefined) return;
    state.nextRunAt = next;
    void this.persistSchedulerState().catch((error) => console.warn("[scheduler] Could not save scheduler state", error));

    const timer = setTimeout(() => {
      this.jobTimers.delete(key);
      const state = this.jobState(loaded.manifest.id, job.id);
      state.nextRunAt = undefined;
      state.lastScheduledAt = next;
      void this.persistSchedulerState().catch((error) => console.warn("[scheduler] Could not save scheduler state", error));
      void this.runJobWithRetry(loaded, job, next!).catch((error) => {
        console.error(`[module:${loaded.manifest.id}] job ${job.id} failed`, error);
      }).finally(() => {
        if (this.jobsStarted && !this.stopping && (job.intervalSeconds !== undefined || job.cron !== undefined)) this.scheduleJob(loaded, job);
      });
    }, Math.min(2_147_483_647, Math.max(0, next - now)));
    timer.unref?.();
    this.jobTimers.set(key, timer);
  }

  startJobs(): void {
    if (this.jobsStarted) return;
    this.jobsStarted = true;
    for (const loaded of this.modules.values()) {
      if (!(loaded.manifest.capabilities?.includes("jobs") ?? false)) continue;
      for (const job of loaded.manifest.jobs ?? []) {
        if (job.runOnStart) {
          void this.runJobWithRetry(loaded, job, Date.now()).catch((error) => console.error(`[module:${loaded.manifest.id}] job ${job.id} failed`, error));
        }
        this.scheduleJob(loaded, job);
      }
    }
  }

  stopJobs(): void {
    for (const timer of this.jobTimers.values()) clearTimeout(timer);
    this.jobTimers.clear();
    this.runningJobs.clear();
    this.jobsStarted = false;
  }

  private async stopLoaded(loaded: LoadedModule): Promise<void> {
    const child = loaded.process;
    if (!child) return;
    loaded.intentionalStop = true;
    const restartTimer = this.restartTimers.get(loaded.manifest.id);
    if (restartTimer) {
      clearTimeout(restartTimer);
      this.restartTimers.delete(loaded.manifest.id);
    }

    if (loaded.lifecycleStarted && child.exitCode === null) {
      try {
        const actions = await this.requestRaw(loaded.manifest.id, {
          protocol: BAILEY_MODULE_PROTOCOL, id: randomUUID(), type: "lifecycle.stop",
        }, 3_000);
        await this.applyActions(loaded.manifest.id, actions);
      } catch (error) {
        this.log(loaded, `lifecycle.stop failed: ${error instanceof Error ? error.message : String(error)}`);
      }
    }

    if (child.exitCode !== null || child.signalCode !== null) {
      loaded.process = undefined;
      loaded.lifecycleStarted = false;
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
      const timer = setTimeout(() => {
        if (!child.killed) child.kill();
        finish();
      }, 2500);
      child.once("exit", finish);
      child.once("error", finish);
      child.stdin.end();
      if (!child.killed) child.kill();
    });
    loaded.process = undefined;
    loaded.lifecycleStarted = false;
  }

  async restartModule(moduleId: string): Promise<ExternalModuleRuntimeStatus> {
    const loaded = this.modules.get(moduleId);
    if (!loaded) throw new Error(`External module is not loaded: ${moduleId}`);
    await this.stopLoaded(loaded);
    loaded.intentionalStop = false;
    loaded.diagnostics.restartCount += 1;
    this.ensureProcess(moduleId);
    return this.statuses().find((status) => status.id === moduleId)!;
  }

  async stopAll(): Promise<void> {
    this.stopping = true;
    this.stopJobs();
    for (const timer of this.restartTimers.values()) clearTimeout(timer);
    this.restartTimers.clear();
    await Promise.all([...this.modules.values()].map((loaded) => this.stopLoaded(loaded)));
  }
}
