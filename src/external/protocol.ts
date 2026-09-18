import type { ModuleSettingDefinition } from "../shared/config-schema";

export const BAILEY_MODULE_PROTOCOL = 1 as const;

export type ExternalModuleCapability =
  | "commands"
  | "settings"
  | "events"
  | "jobs"
  | "storage"
  | "services"
  | "media"
  | "lifecycle";

export type ExternalModuleEventName =
  | "message.received"
  | "message.updated"
  | "reaction.received"
  | "group.participant"
  | "call.received";

export interface ExternalModuleCommandManifest {
  id: string;
  name: string;
  section?: string;
  description: string;
  aliases?: string[];
}

export interface ExternalModuleJobRetry {
  maxAttempts?: number;
  backoffSeconds?: number;
}

export interface ExternalModuleJobManifest {
  id: string;
  /** Fixed interval used by Bailey's local scheduler. Minimum: 5 seconds. */
  intervalSeconds?: number;
  /** Standard 5-field cron expression: minute hour day month weekday. */
  cron?: string;
  /** One-time Unix timestamp in milliseconds. */
  runAt?: number;
  /** Run once immediately when the module scheduler starts. */
  runOnStart?: boolean;
  retry?: ExternalModuleJobRetry;
}

export interface ExternalModuleRuntimeManifest {
  /** Executable available on the host, e.g. python, node, java, or a compiled binary. */
  command: string;
  /** Arguments passed without a shell. Relative file arguments resolve from the module folder. */
  args?: string[];
  /** Restart a worker after an unexpected non-zero exit. Defaults to on-failure. */
  restart?: "on-failure" | "never";
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
  /** Explicit host permissions such as storage.read, media.download or whatsapp.send. */
  permissions?: string[];
  /** Events delivered to this module. Defaults to message.received when events is enabled. */
  events?: ExternalModuleEventName[];
}

export interface ExternalIncomingMedia {
  kind: "image" | "video" | "audio" | "document" | "sticker";
  mimetype?: string;
  fileName?: string;
  caption?: string;
  seconds?: number;
  viewOnce?: boolean;
}

export interface ExternalCommandContext {
  remoteJid: string;
  senderJid?: string;
  text: string;
  args: string[];
}

export interface ExternalMessageEventContext {
  id?: string;
  remoteJid: string;
  senderJid?: string;
  text?: string;
  pushName?: string;
  timestamp?: number;
  media?: ExternalIncomingMedia;
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
  event: ExternalModuleEventName;
  context: ExternalMessageEventContext | Record<string, unknown>;
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

export type ExternalModuleRequest =
  | ExternalCommandRequest
  | ExternalEventRequest
  | ExternalJobRequest
  | ExternalLifecycleRequest;

export interface ExternalHostCall {
  protocol: typeof BAILEY_MODULE_PROTOCOL;
  id: string;
  type: "host.call";
  service: string;
  method: string;
  params?: unknown;
}

export interface ExternalHostResult {
  protocol: typeof BAILEY_MODULE_PROTOCOL;
  type: "host.result";
  replyTo: string;
  ok: boolean;
  result?: unknown;
  error?: string;
}

export type ExternalModuleInput = ExternalModuleRequest | ExternalHostResult;

export interface ExternalMediaSource {
  path?: string;
  url?: string;
}

export interface ExternalMediaSend {
  kind: "image" | "video" | "audio" | "document" | "sticker";
  source: ExternalMediaSource;
  mimetype?: string;
  fileName?: string;
  caption?: string;
  ptt?: boolean;
}

export type ExternalModuleAction =
  | { type: "reply"; text: string }
  | { type: "react"; emoji: string }
  | { type: "send"; remoteJid: string; text: string }
  | { type: "send-media"; remoteJid: string; media: ExternalMediaSend }
  | { type: "log"; level?: "debug" | "info" | "warn" | "error"; message: string };

export interface ExternalModuleResponse {
  protocol: typeof BAILEY_MODULE_PROTOCOL;
  replyTo: string;
  ok: boolean;
  actions?: ExternalModuleAction[];
  error?: string;
}

export type ExternalModuleOutput = ExternalModuleResponse | ExternalHostCall;

const MODULE_ID = /^[a-z0-9][a-z0-9.-]*$/;
const COMMAND_NAME = /^[a-z0-9][a-z0-9_-]{0,63}$/;
const SERVICE_NAME = /^[a-z0-9][a-z0-9.-]{0,63}$/;
const SERVICE_METHOD = /^[a-z0-9][a-z0-9._-]{0,63}$/;
const PERMISSION_NAME = /^[a-z0-9*][a-z0-9._*-]{0,95}$/;
const CAPABILITIES = new Set<ExternalModuleCapability>([
  "commands", "settings", "events", "jobs", "storage", "services", "media", "lifecycle",
]);
const EVENTS = new Set<ExternalModuleEventName>([
  "message.received", "message.updated", "reaction.received", "group.participant", "call.received",
]);
const MIN_JOB_INTERVAL_SECONDS = 5;
const MAX_JOB_INTERVAL_SECONDS = 30 * 24 * 60 * 60;

function validateJob(job: ExternalModuleJobManifest): void {
  if (!job || typeof job !== "object") throw new Error("Module jobs must be objects.");
  if (typeof job.id !== "string" || !COMMAND_NAME.test(job.id)) throw new Error("Job id is invalid.");
  const schedules = [job.intervalSeconds !== undefined, job.cron !== undefined, job.runAt !== undefined].filter(Boolean).length;
  if (schedules !== 1) throw new Error(`Job ${job.id} must declare exactly one of intervalSeconds, cron or runAt.`);

  if (job.intervalSeconds !== undefined) {
    if (!Number.isInteger(job.intervalSeconds) || job.intervalSeconds < MIN_JOB_INTERVAL_SECONDS || job.intervalSeconds > MAX_JOB_INTERVAL_SECONDS) {
      throw new Error(`Job ${job.id} intervalSeconds must be an integer between ${MIN_JOB_INTERVAL_SECONDS} and ${MAX_JOB_INTERVAL_SECONDS}.`);
    }
  }
  if (job.cron !== undefined && (typeof job.cron !== "string" || job.cron.trim().split(/\s+/).length !== 5)) {
    throw new Error(`Job ${job.id} cron must be a standard five-field expression.`);
  }
  if (job.runAt !== undefined && (!Number.isFinite(job.runAt) || job.runAt <= 0)) {
    throw new Error(`Job ${job.id} runAt must be a Unix timestamp in milliseconds.`);
  }
  if (job.runOnStart !== undefined && typeof job.runOnStart !== "boolean") {
    throw new Error(`Job ${job.id} runOnStart must be a boolean.`);
  }
  if (job.retry !== undefined) {
    const attempts = job.retry.maxAttempts ?? 1;
    const backoff = job.retry.backoffSeconds ?? 5;
    if (!Number.isInteger(attempts) || attempts < 1 || attempts > 5) throw new Error(`Job ${job.id} retry.maxAttempts must be 1–5.`);
    if (!Number.isInteger(backoff) || backoff < 1 || backoff > 3600) throw new Error(`Job ${job.id} retry.backoffSeconds must be 1–3600.`);
  }
}

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
  if (input.runtime.restart !== undefined && input.runtime.restart !== "on-failure" && input.runtime.restart !== "never") {
    throw new Error("Module runtime.restart must be on-failure or never.");
  }

  if (input.capabilities !== undefined) {
    if (!Array.isArray(input.capabilities)) throw new Error("Module capabilities must be an array.");
    const seen = new Set<string>();
    for (const capability of input.capabilities) {
      if (typeof capability !== "string" || !CAPABILITIES.has(capability as ExternalModuleCapability)) {
        throw new Error(`Unsupported module capability: ${String(capability)}.`);
      }
      if (seen.has(capability)) throw new Error(`Duplicate module capability: ${capability}.`);
      seen.add(capability);
    }
  }

  if (input.permissions !== undefined) {
    if (!Array.isArray(input.permissions)) throw new Error("Module permissions must be an array.");
    const seen = new Set<string>();
    for (const permission of input.permissions) {
      if (typeof permission !== "string" || !PERMISSION_NAME.test(permission)) throw new Error(`Invalid module permission: ${String(permission)}.`);
      if (seen.has(permission)) throw new Error(`Duplicate module permission: ${permission}.`);
      seen.add(permission);
    }
  }

  if (input.events !== undefined) {
    if (!Array.isArray(input.events)) throw new Error("Module events must be an array.");
    if (!(input.capabilities ?? []).includes("events")) throw new Error("A module with event subscriptions must declare the events capability.");
    const seen = new Set<string>();
    for (const event of input.events) {
      if (typeof event !== "string" || !EVENTS.has(event as ExternalModuleEventName)) throw new Error(`Unsupported module event: ${String(event)}.`);
      if (seen.has(event)) throw new Error(`Duplicate module event subscription: ${event}.`);
      seen.add(event);
    }
  }

  const seenCommands = new Set<string>();
  for (const command of input.commands ?? []) {
    if (!command || typeof command !== "object") throw new Error("Module commands must be objects.");
    if (typeof command.id !== "string" || !COMMAND_NAME.test(command.id)) throw new Error("Command id is invalid.");
    if (typeof command.name !== "string" || !COMMAND_NAME.test(command.name)) throw new Error(`Command ${command.id} has an invalid trigger.`);
    if (typeof command.description !== "string" || !command.description.trim()) throw new Error(`Command ${command.id} needs a description.`);
    for (const name of [command.name, ...(command.aliases ?? [])]) {
      if (typeof name !== "string" || !COMMAND_NAME.test(name)) throw new Error(`Command ${command.id} has an invalid alias.`);
      const normalized = name.toLowerCase();
      if (seenCommands.has(normalized)) throw new Error(`Duplicate external command trigger: ${name}.`);
      seenCommands.add(normalized);
    }
  }

  const seenJobs = new Set<string>();
  for (const job of input.jobs ?? []) {
    validateJob(job);
    if (seenJobs.has(job.id.toLowerCase())) throw new Error(`Duplicate job id: ${job.id}.`);
    seenJobs.add(job.id.toLowerCase());
  }
  if ((input.jobs?.length ?? 0) > 0 && !(input.capabilities ?? []).includes("jobs")) {
    throw new Error("A module with jobs must declare the jobs capability.");
  }
  return input as ExternalModuleManifest;
}

export function parseExternalHostCall(raw: unknown): ExternalHostCall {
  if (!raw || typeof raw !== "object") throw new Error("Host service call must be a JSON object.");
  const call = raw as Partial<ExternalHostCall>;
  if (call.protocol !== BAILEY_MODULE_PROTOCOL) throw new Error("Host service call uses an unsupported protocol version.");
  if (call.type !== "host.call") throw new Error("Output is not a host service call.");
  if (typeof call.id !== "string" || !call.id.trim()) throw new Error("Host service call is missing id.");
  if (typeof call.service !== "string" || !SERVICE_NAME.test(call.service)) throw new Error("Host service name is invalid.");
  if (typeof call.method !== "string" || !SERVICE_METHOD.test(call.method)) throw new Error("Host service method is invalid.");
  return call as ExternalHostCall;
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
