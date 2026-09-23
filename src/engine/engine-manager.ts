import { EventEmitter } from "node:events";
import { spawn, type ChildProcess } from "node:child_process";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import type { EngineHostEvent, EngineInstallState, EngineManifest, EngineProvider, EngineSendMedia, EngineStatus, EngineWorkerCommand, EngineWorkerEvent, IncomingEngineMessage, WhatsAppConnectionState } from "./contracts";

const PACKAGE_NAMES: Record<EngineProvider, string> = { lia: "@itsliaaa/baileys", baileys: "@whiskeysockets/baileys" };
const LIA_DEFAULT_VERSION = "0.3.18-final";

function emptyInstallState(): EngineInstallState {
  return { installedVersions: [] };
}

function defaultManifest(): EngineManifest {
  return {
    provider: "lia",
    packageName: PACKAGE_NAMES.lia,
    apiVersion: 1,
    installedVersions: [],
    autoUpdate: false,
    channel: "stable",
    providers: { lia: emptyInstallState(), baileys: emptyInstallState() },
  };
}

function validVersion(version: string): boolean {
  return /^[0-9A-Za-z][0-9A-Za-z.+_-]*$/.test(version);
}

export class EngineManager extends EventEmitter {
  private manifest: EngineManifest = defaultManifest();
  private latestVersion?: string;
  private runtime: EngineStatus["runtime"] = "stopped";
  private whatsapp: WhatsAppConnectionState = "not-connected";
  private pairingCode?: string;
  private lastError?: string;
  private child?: ChildProcess;
  private readonly pendingMedia = new Map<string, { resolve: (value: { path: string; size: number }) => void; reject: (error: Error) => void; timer: NodeJS.Timeout }>();

  constructor(
    private readonly rootDir: string,
    private readonly workerPath: string,
    private readonly appExecutable: string,
  ) {
    super();
  }

  private get manifestPath(): string {
    return join(this.rootDir, "engine.json");
  }

  private engineDir(version: string): string {
    return join(this.rootDir, this.manifest.provider, version);
  }

  private get sessionDir(): string {
    return this.manifest.provider === "lia"
      ? join(this.rootDir, "..", "sessions", "default")
      : join(this.rootDir, "..", "sessions", this.manifest.provider, "default");
  }

  private selectedState(): EngineInstallState {
    return this.manifest.providers?.[this.manifest.provider] ?? emptyInstallState();
  }

  private syncSelectedState(): void {
    const selected = this.selectedState();
    this.manifest.activeVersion = selected.activeVersion;
    this.manifest.previousVersion = selected.previousVersion;
    this.manifest.installedVersions = [...selected.installedVersions];
    this.manifest.packageName = PACKAGE_NAMES[this.manifest.provider];
  }

  private saveSelectedState(): void {
    this.manifest.providers ??= {};
    this.manifest.providers[this.manifest.provider] = {
      activeVersion: this.manifest.activeVersion,
      previousVersion: this.manifest.previousVersion,
      installedVersions: [...new Set(this.manifest.installedVersions)],
    };
  }

  async initialize(): Promise<void> {
    await Promise.all((Object.keys(PACKAGE_NAMES) as EngineProvider[]).map((provider) => mkdir(join(this.rootDir, provider), { recursive: true })));
    try {
      const parsed = JSON.parse(await readFile(this.manifestPath, "utf8")) as EngineManifest;
      if ((parsed.provider === "lia" || parsed.provider === "baileys") && parsed.packageName === PACKAGE_NAMES[parsed.provider] && parsed.apiVersion === 1) {
        const providers = { ...defaultManifest().providers, ...(parsed.providers ?? {}) };
        if (!parsed.providers) providers.lia = {
          activeVersion: parsed.activeVersion,
          previousVersion: parsed.previousVersion,
          installedVersions: [...new Set(parsed.installedVersions ?? [])],
        };
        for (const provider of Object.keys(PACKAGE_NAMES) as EngineProvider[]) {
          const state = providers[provider] ?? emptyInstallState();
          providers[provider] = { ...emptyInstallState(), ...state, installedVersions: [...new Set(state.installedVersions ?? [])] };
        }
        this.manifest = { ...defaultManifest(), ...parsed, providers };
        this.syncSelectedState();
      }
    } catch {
      // A missing or invalid manifest starts with the safe Lia default.
    }
    await mkdir(this.sessionDir, { recursive: true });
    await this.saveManifest();
  }

  status(): EngineStatus {
    return {
      provider: this.manifest.provider,
      packageName: this.manifest.packageName,
      apiVersion: 1,
      activeVersion: this.manifest.activeVersion,
      previousVersion: this.manifest.previousVersion,
      installedVersions: [...this.manifest.installedVersions],
      latestVersion: this.latestVersion,
      updateAvailable: Boolean(this.latestVersion && this.manifest.activeVersion && this.latestVersion !== this.manifest.activeVersion),
      runtime: this.runtime,
      whatsapp: this.whatsapp,
      pairingCode: this.pairingCode,
      lastError: this.lastError,
    };
  }

  async checkLatest(): Promise<EngineStatus> {
    const packageName = encodeURIComponent(this.manifest.packageName);
    const response = await fetch(`https://registry.npmjs.org/${packageName}/latest`, {
      headers: { accept: "application/json" },
    });
    if (!response.ok) throw new Error(`Could not check ${this.manifest.packageName}: HTTP ${response.status}`);
    const body = await response.json() as { version?: string };
    if (!body.version || !validVersion(body.version)) throw new Error(`Registry returned an invalid version for ${this.manifest.packageName}.`);
    this.latestVersion = body.version;
    this.emitStatus();
    return this.status();
  }

  async installDefault(): Promise<EngineStatus> {
    if (this.manifest.provider === "lia") return this.installVersion(LIA_DEFAULT_VERSION);
    const status = await this.checkLatest();
    if (!status.latestVersion) throw new Error("Latest engine version is unknown.");
    return this.installVersion(status.latestVersion);
  }

  async setProvider(providerValue: unknown): Promise<EngineStatus> {
    const provider = String(providerValue ?? "") as EngineProvider;
    if (!(provider in PACKAGE_NAMES)) throw new Error("Unsupported WhatsApp engine provider.");
    if (provider === this.manifest.provider) return this.status();
    const wasRunning = Boolean(this.child);
    if (wasRunning) await this.stop();
    this.saveSelectedState();
    this.manifest.provider = provider;
    this.syncSelectedState();
    this.latestVersion = undefined;
    await mkdir(join(this.rootDir, provider), { recursive: true });
    await mkdir(this.sessionDir, { recursive: true });
    await this.saveManifest();
    this.emitStatus();
    return this.status();
  }

  async installVersion(version: string): Promise<EngineStatus> {
    if (!validVersion(version)) throw new Error("Invalid engine version.");
    const wasRunning = Boolean(this.child);
    if (wasRunning) await this.stop();
    this.runtime = "installing";
    this.lastError = undefined;
    this.emitStatus();

    const target = this.engineDir(version);
    const temp = `${target}.installing`;
    await rm(temp, { recursive: true, force: true });
    await mkdir(temp, { recursive: true });
    await writeFile(join(temp, "package.json"), JSON.stringify({ private: true, dependencies: { [this.manifest.packageName]: version } }, null, 2));

    try {
      await this.runBundledNpm(temp, ["install", "--omit=optional", "--no-audit", "--no-fund", "--save-exact"]);
      await rm(target, { recursive: true, force: true });
      await rename(temp, target);

      const oldActive = this.manifest.activeVersion;
      if (oldActive && oldActive !== version) this.manifest.previousVersion = oldActive;
      this.manifest.activeVersion = version;
      this.manifest.installedVersions = [...new Set([...this.manifest.installedVersions, version])];
      this.saveSelectedState();
      await this.saveManifest();
      this.runtime = "stopped";
      this.emitStatus();
      if (wasRunning) await this.start();
      return this.status();
    } catch (error) {
      await rm(temp, { recursive: true, force: true });
      this.runtime = "error";
      this.lastError = error instanceof Error ? error.message : String(error);
      this.emitStatus();
      throw error;
    }
  }

  async update(): Promise<EngineStatus> {
    await this.checkLatest();
    if (!this.latestVersion) throw new Error("Latest version is unknown.");
    if (this.latestVersion === this.manifest.activeVersion) return this.status();
    return this.installVersion(this.latestVersion);
  }

  async rollback(): Promise<EngineStatus> {
    const previous = this.manifest.previousVersion;
    const current = this.manifest.activeVersion;
    if (!previous) throw new Error("No previous engine version is available.");
    if (!this.manifest.installedVersions.includes(previous)) throw new Error("Previous engine files are missing.");
    const wasRunning = Boolean(this.child);
    if (wasRunning) await this.stop();
    this.manifest.activeVersion = previous;
    this.manifest.previousVersion = current;
    this.saveSelectedState();
    await this.saveManifest();
    this.emitStatus();
    if (wasRunning) await this.start();
    return this.status();
  }

  async start(): Promise<EngineStatus> {
    if (this.child) return this.status();
    const version = this.manifest.activeVersion;
    if (!version) throw new Error("Install a WhatsApp engine before starting Bailey.");
    this.runtime = "starting";
    this.whatsapp = "connecting";
    this.lastError = undefined;
    this.pairingCode = undefined;
    this.emitStatus();

    const child = spawn(this.appExecutable, [
      this.workerPath,
      "--engine-dir", this.engineDir(version),
      "--session-dir", this.sessionDir,
      "--engine-version", version,
      "--provider", this.manifest.provider,
      "--package-name", this.manifest.packageName,
    ], {
      env: { ...process.env, ELECTRON_RUN_AS_NODE: "1" },
      stdio: ["ignore", "pipe", "pipe", "ipc"],
      windowsHide: true,
    });
    this.child = child;

    child.on("message", (raw: EngineWorkerEvent) => this.onWorkerEvent(raw));
    child.stderr?.on("data", (chunk) => {
      const text = String(chunk).trim();
      if (text) this.emit("log", text);
    });
    child.on("exit", (code, signal) => {
      if (this.child !== child) return;
      this.child = undefined;
      this.runtime = code === 0 || signal === "SIGTERM" ? "stopped" : "error";
      if (this.whatsapp === "connected") this.whatsapp = "disconnected";
      if (code && code !== 0) this.lastError = `Engine exited with code ${code}.`;
      for (const pending of this.pendingMedia.values()) {
        clearTimeout(pending.timer);
        pending.reject(new Error("WhatsApp engine stopped during media download."));
      }
      this.pendingMedia.clear();
      this.emitStatus();
    });
    return this.status();
  }

  async stop(): Promise<EngineStatus> {
    const child = this.child;
    if (!child) {
      this.runtime = "stopped";
      this.whatsapp = "not-connected";
      this.emitStatus();
      return this.status();
    }
    this.send({ type: "shutdown" });
    await new Promise<void>((resolve) => {
      const timer = setTimeout(() => {
        child.kill();
        resolve();
      }, 2500);
      child.once("exit", () => {
        clearTimeout(timer);
        resolve();
      });
    });
    if (this.child === child) this.child = undefined;
    this.runtime = "stopped";
    this.whatsapp = "not-connected";
    this.emitStatus();
    return this.status();
  }

  requestPairingCode(phoneNumber: string): void {
    const normalized = phoneNumber.replace(/\D/g, "");
    if (normalized.length < 7) throw new Error("Enter the phone number with country code.");
    this.send({ type: "pair", phoneNumber: normalized });
  }

  sendText(remoteJid: string, text: string): void {
    this.send({ type: "send-text", remoteJid, text });
  }

  sendMedia(remoteJid: string, media: EngineSendMedia): void {
    this.send({ type: "send-media", remoteJid, media });
  }

  downloadMedia(messageId: string, destinationPath: string): Promise<{ path: string; size: number }> {
    const requestId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingMedia.delete(requestId);
        reject(new Error("Media download timed out."));
      }, 30_000);
      this.pendingMedia.set(requestId, { resolve, reject, timer });
      try {
        this.send({ type: "download-media", requestId, messageId, destinationPath });
      } catch (error) {
        clearTimeout(timer);
        this.pendingMedia.delete(requestId);
        reject(error instanceof Error ? error : new Error(String(error)));
      }
    });
  }

  react(remoteJid: string, key: unknown, emoji: string): void {
    this.send({ type: "react", remoteJid, key, emoji });
  }

  private send(command: EngineWorkerCommand): void {
    if (!this.child?.connected) throw new Error("WhatsApp engine is not running.");
    this.child.send(command);
  }

  private onWorkerEvent(event: EngineWorkerEvent): void {
    if (!event || typeof event !== "object") return;
    if (event.type === "ready") {
      this.runtime = "running";
      this.emitStatus();
    } else if (event.type === "connection") {
      this.whatsapp = event.state;
      if (event.state === "connected") this.runtime = "running";
      this.emitStatus();
    } else if (event.type === "pairing-code") {
      this.pairingCode = event.code;
      this.emitStatus();
    } else if (event.type === "message") {
      this.emit("message", event.message satisfies IncomingEngineMessage);
    } else if (event.type === "host-event") {
      this.emit("host-event", { event: event.event, context: event.context } satisfies EngineHostEvent);
    } else if (event.type === "media-downloaded") {
      const pending = this.pendingMedia.get(event.requestId);
      if (pending) {
        clearTimeout(pending.timer);
        this.pendingMedia.delete(event.requestId);
        if (event.ok && event.path && typeof event.size === "number") pending.resolve({ path: event.path, size: event.size });
        else pending.reject(new Error(event.error || "Media download failed."));
      }
    } else if (event.type === "error") {
      this.lastError = event.message;
      this.emitStatus();
    }
  }

  private async saveManifest(): Promise<void> {
    await mkdir(this.rootDir, { recursive: true });
    this.saveSelectedState();
    await writeFile(this.manifestPath, JSON.stringify(this.manifest, null, 2), "utf8");
  }

  private emitStatus(): void {
    this.emit("status", this.status());
  }

  private async runBundledNpm(cwd: string, args: string[]): Promise<void> {
    const localRequire = createRequire(__filename);
    let npmPackagePath = localRequire.resolve("npm/package.json");
    if (npmPackagePath.includes("app.asar")) npmPackagePath = npmPackagePath.replace("app.asar", "app.asar.unpacked");
    const npmCli = join(dirname(npmPackagePath), "bin", "npm-cli.js");
    const shimDir = join(this.rootDir, ".runtime");
    await mkdir(shimDir, { recursive: true });
    const nodeShim = `@echo off\r\nset ELECTRON_RUN_AS_NODE=1\r\n"${this.appExecutable.replace(/"/g, '""')}" %*\r\n`;
    await writeFile(join(shimDir, "node.cmd"), nodeShim, "utf8");

    await new Promise<void>((resolve, reject) => {
      const child = spawn(this.appExecutable, [npmCli, ...args], {
        cwd,
        env: {
          ...process.env,
          ELECTRON_RUN_AS_NODE: "1",
          PATH: `${shimDir};${process.env.PATH ?? ""}`,
          npm_config_update_notifier: "false",
        },
        windowsHide: true,
      });
      let stderr = "";
      child.stderr?.on("data", (chunk) => { stderr += String(chunk); });
      child.on("error", reject);
      child.on("exit", (code) => {
        if (code === 0) resolve();
        else reject(new Error(stderr.trim() || `Engine package install failed with code ${code}.`));
      });
    });
  }
}
