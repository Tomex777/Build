import type { ModuleSettingDefinition } from "../shared/config-schema";

export const BAILEY_MODULE_PROTOCOL = 1 as const;

export type ExternalModuleCapability = "commands" | "settings" | "events" | "jobs" | "storage" | "services";

export interface ExternalModuleCommandManifest {
  id: string;
  name: string;
  section?: string;
  description: string;
  aliases?: string[];
}

export interface ExternalModuleJobManifest {
  id: string;
  /** Fixed interval used by Bailey's local scheduler. Minimum: 5 seconds. */
  intervalSeconds: number;
  /** Run once immediately when the module scheduler starts. */
  runOnStart?: boolean;
}

export interface ExternalModuleRuntimeManifest {
  /** Executable available on the host, e.g. python, node, java, or a compiled binary. */
  command: string;
  /** Arguments passed without a shell. Relative file arguments resolve from the module folder. */
  args?: string[];
}

export interface ExternalModuleManifest {
  protocol: typeof BAILEY_MODULE_PROTOCOL;
  id: string;
  name: string;
  version: string;
  description?: string;
  runtime: ExternalModuleRuntimeManifest;
  commands?: ExternalModuleCommandManifest[];
  jobs?: ExternalModuleJobManifest[];
  settings?: ModuleSettingDefinition[];
  capabilities?: ExternalModuleCapability[];
}

export interface ExternalCommandContext {
  remoteJid: string;
  senderJid?: string;
  text: string;
  args: string[];
}

export interface ExternalMessageEventContext {
  remoteJid: string;
  senderJid?: string;
  text?: string;
  pushName?: string;
  timestamp?: number;
}

export interface ExternalCommandRequest {
  protocol: typeof BAILEY_MODULE_PROTOCOL;
  id: string;
  type: "command.execute";
  commandId: string;
  context: ExternalCommandContext;
}

export interface ExternalEventRequest {
  protocol: typeof BAILEY_MODULE_PROTOCOL;
  id: string;
  type: "event.dispatch";
  event: "message.received";
  context: ExternalMessageEventContext;
}

export interface ExternalJobRequest {
  protocol: typeof BAILEY_MODULE_PROTOCOL;
  id: string;
  type: "job.execute";
  jobId: string;
  scheduledAt: number;
}

export interface ExternalLifecycleRequest {
  protocol: typeof BAILEY_MODULE_PROTOCOL;
  id: string;
  type: "lifecycle.start" | "lifecycle.stop";
}

export type ExternalModuleRequest = ExternalCommandRequest | ExternalEventRequest | ExternalJobRequest | ExternalLifecycleRequest;

export type ExternalModuleAction =
  | { type: "reply"; text: string }
  | { type: "react"; emoji: string }
  | { type: "send"; remoteJid: string; text: string }
  | { type: "log"; level?: "debug" | "info" | "warn" | "error"; message: string };

export interface ExternalModuleResponse {
  protocol: typeof BAILEY_MODULE_PROTOCOL;
  replyTo: string;
  ok: boolean;
  actions?: ExternalModuleAction[];
  error?: string;
}

const MODULE_ID = /^[a-z0-9][a-z0-9.-]*$/;
const COMMAND_NAME = /^[a-z0-9][a-z0-9_-]{0,63}$/;
const CAPABILITIES = new Set<ExternalModuleCapability>(["commands", "settings", "events", "jobs", "storage", "services"]);
const MIN_JOB_INTERVAL_SECONDS = 5;
const MAX_JOB_INTERVAL_SECONDS = 30 * 24 * 60 * 60;

export function parseExternalModuleManifest(raw: unknown): ExternalModuleManifest {
  if (!raw || typeof raw !== "object") throw new Error("Module manifest must be a JSON object.");
  const input = raw as Partial<ExternalModuleManifest>;
  if (input.protocol !== BAILEY_MODULE_PROTOCOL) throw new Error(`Unsupported Bailey module protocol: ${String(input.protocol)}.`);
  if (typeof input.id !== "string" || !MODULE_ID.test(input.id)) throw new Error("Module id must use lowercase letters, numbers, dots or hyphens.");
  if (typeof input.name !== "string" || !input.name.trim()) throw new Error("Module name is required.");
  if (typeof input.version !== "string" || !input.version.trim()) throw new Error("Module version is required.");
  if (!input.runtime || typeof input.runtime.command !== "string" || !input.runtime.command.trim()) throw new Error("Module runtime.command is required.");
  if (input.runtime.args !== undefined && (!Array.isArray(input.runtime.args) || input.runtime.args.some((arg) => typeof arg !== "string"))) {
    throw new Error("Module runtime.args must be an array of strings.");
  }

  if (input.capabilities !== undefined) {
    if (!Array.isArray(input.capabilities)) throw new Error("Module capabilities must be an array.");
    const seenCapabilities = new Set<string>();
    for (const capability of input.capabilities) {
      if (typeof capability !== "string" || !CAPABILITIES.has(capability as ExternalModuleCapability)) {
        throw new Error(`Unsupported module capability: ${String(capability)}.`);
      }
      if (seenCapabilities.has(capability)) throw new Error(`Duplicate module capability: ${capability}.`);
      seenCapabilities.add(capability);
    }
  }

  const seen = new Set<string>();
  for (const command of input.commands ?? []) {
    if (!command || typeof command !== "object") throw new Error("Module commands must be objects.");
    if (typeof command.id !== "string" || !COMMAND_NAME.test(command.id)) throw new Error("Command id is invalid.");
    if (typeof command.name !== "string" || !COMMAND_NAME.test(command.name)) throw new Error(`Command ${command.id} has an invalid trigger.`);
    if (typeof command.description !== "string" || !command.description.trim()) throw new Error(`Command ${command.id} needs a description.`);
    const names = [command.name, ...(command.aliases ?? [])];
    for (const name of names) {
      if (typeof name !== "string" || !COMMAND_NAME.test(name)) throw new Error(`Command ${command.id} has an invalid alias.`);
      const normalized = name.toLowerCase();
      if (seen.has(normalized)) throw new Error(`Duplicate external command trigger: ${name}.`);
      seen.add(normalized);
    }
  }

  const seenJobs = new Set<string>();
  for (const job of input.jobs ?? []) {
    if (!job || typeof job !== "object") throw new Error("Module jobs must be objects.");
    if (typeof job.id !== "string" || !COMMAND_NAME.test(job.id)) throw new Error("Job id is invalid.");
    if (seenJobs.has(job.id.toLowerCase())) throw new Error(`Duplicate job id: ${job.id}.`);
    seenJobs.add(job.id.toLowerCase());
    if (!Number.isInteger(job.intervalSeconds) || job.intervalSeconds < MIN_JOB_INTERVAL_SECONDS || job.intervalSeconds > MAX_JOB_INTERVAL_SECONDS) {
      throw new Error(`Job ${job.id} intervalSeconds must be an integer between ${MIN_JOB_INTERVAL_SECONDS} and ${MAX_JOB_INTERVAL_SECONDS}.`);
    }
    if (job.runOnStart !== undefined && typeof job.runOnStart !== "boolean") throw new Error(`Job ${job.id} runOnStart must be a boolean.`);
  }

  if ((input.jobs?.length ?? 0) > 0 && !(input.capabilities ?? []).includes("jobs")) {
    throw new Error("A module with jobs must declare the jobs capability.");
  }

  return input as ExternalModuleManifest;
}

export function parseExternalModuleResponse(raw: unknown): ExternalModuleResponse {
  if (!raw || typeof raw !== "object") throw new Error("Module response must be a JSON object.");
  const response = raw as Partial<ExternalModuleResponse>;
  if (response.protocol !== BAILEY_MODULE_PROTOCOL) throw new Error("Module response uses an unsupported protocol version.");
  if (typeof response.replyTo !== "string" || !response.replyTo) throw new Error("Module response is missing replyTo.");
  if (typeof response.ok !== "boolean") throw new Error("Module response is missing ok.");
  if (response.actions !== undefined && !Array.isArray(response.actions)) throw new Error("Module response actions must be an array.");
  return response as ExternalModuleResponse;
}
