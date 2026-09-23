import { spawn } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

export type NetworkMode = "normal" | "metered" | "bailey-only" | "temporary-unlock";
export type FirewallAction = "Allow" | "Block" | "NotConfigured";
export type FirewallProfile = "Domain" | "Private" | "Public";

export interface FirewallSnapshot {
  outbound: Partial<Record<FirewallProfile, FirewallAction>>;
  appExecutable: string;
}

export interface NetworkLockAdapter {
  capture(appExecutable: string): Promise<FirewallSnapshot>;
  applyBaileyOnly(snapshot: FirewallSnapshot): Promise<void>;
  restore(snapshot: FirewallSnapshot, keepWatchdog?: boolean): Promise<void>;
  cleanupRules?(): Promise<void>;
  withTemporaryProgramAccess?<T>(program: string, operation: () => Promise<T>): Promise<T>;
}

export interface NetworkLockEvent {
  at: number;
  action: string;
  detail?: string;
}

export interface NetworkLockStatus {
  mode: NetworkMode;
  baileyInternetAvailable: boolean;
  unlockUntil?: number;
  events: NetworkLockEvent[];
  dataUsage: {
    whatsappBytes: null;
    moduleServicesBytes: number;
    storageBytes: number;
    dependencyAndUpdateBytes: null;
    totalBytes: null;
    note: string;
  };
}

interface PersistedState {
  version: 1;
  mode: NetworkMode;
  snapshot?: FirewallSnapshot;
  unlockUntil?: number;
  heartbeatAt?: number;
  events: NetworkLockEvent[];
}

const PROFILE_NAMES: readonly FirewallProfile[] = ["Domain", "Private", "Public"];
const UNLOCK_MINUTES = new Set([5, 15, 30, 60]);

function psQuote(value: string): string {
  return `'${value.replace(/'/g, "''")}'`;
}

export function createFirewallRules(appExecutable: string): string[] {
  const program = psQuote(appExecutable);
  const group = psQuote("Bailey Host Network Lock");
  const profiles = "Domain,Private,Public";
  const rules = [
    `$rule = Get-NetFirewallRule -Name 'BaileyHostNetworkLock-App' -ErrorAction SilentlyContinue; if (-not $rule) { New-NetFirewallRule -Name 'BaileyHostNetworkLock-App' -DisplayName 'Bailey Host Network Lock - Bailey Host' -Group ${group} -Direction Outbound -Action Allow -Program ${program} -Profile ${profiles} | Out-Null }`,
    `$rule = Get-NetFirewallRule -Name 'BaileyHostNetworkLock-DnsUdp' -ErrorAction SilentlyContinue; if (-not $rule) { New-NetFirewallRule -Name 'BaileyHostNetworkLock-DnsUdp' -DisplayName 'Bailey Host Network Lock - DNS UDP' -Group ${group} -Direction Outbound -Action Allow -Program 'System' -Protocol UDP -RemotePort 53 -Profile ${profiles} | Out-Null }`,
    `$rule = Get-NetFirewallRule -Name 'BaileyHostNetworkLock-DnsTcp' -ErrorAction SilentlyContinue; if (-not $rule) { New-NetFirewallRule -Name 'BaileyHostNetworkLock-DnsTcp' -DisplayName 'Bailey Host Network Lock - DNS TCP' -Group ${group} -Direction Outbound -Action Allow -Program 'System' -Protocol TCP -RemotePort 53 -Profile ${profiles} | Out-Null }`,
    `$rule = Get-NetFirewallRule -Name 'BaileyHostNetworkLock-Dhcp' -ErrorAction SilentlyContinue; if (-not $rule) { New-NetFirewallRule -Name 'BaileyHostNetworkLock-Dhcp' -DisplayName 'Bailey Host Network Lock - DHCP' -Group ${group} -Direction Outbound -Action Allow -Program 'System' -Protocol UDP -LocalPort 68,546 -RemotePort 67,547 -Profile ${profiles} | Out-Null }`,
  ];
  return rules;
}

export function buildBaileyOnlyScript(snapshot: FirewallSnapshot): string {
  const ruleLines = createFirewallRules(snapshot.appExecutable).join("\n");
  const prior = JSON.stringify(snapshot.outbound).replace(/'/g, "''");
  return `
$ErrorActionPreference = 'Stop'
$prior = ConvertFrom-Json '${prior}'
$created = @()
try {
  ${ruleLines}
  foreach ($name in @('Domain','Private','Public')) {
    Set-NetFirewallProfile -Profile $name -DefaultOutboundAction Block
  }
  Get-NetFirewallRule -Name 'BaileyHostNetworkLock-*' -ErrorAction SilentlyContinue | Out-Null
} catch {
  foreach ($name in @('Domain','Private','Public')) {
    $old = $prior.$name
    if ($null -ne $old -and $old -ne 'Block') { Set-NetFirewallProfile -Profile $name -DefaultOutboundAction $old -ErrorAction SilentlyContinue }
  }
  Get-NetFirewallRule -Group 'Bailey Host Network Lock' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue
  throw
}`;
}

export function buildRestoreScript(snapshot: FirewallSnapshot, keepWatchdog = false): string {
  const prior = JSON.stringify(snapshot.outbound).replace(/'/g, "''");
  const unregister = keepWatchdog ? "" : "\nUnregister-ScheduledTask -TaskName 'BaileyHostNetworkLockWatchdog' -Confirm:$false -ErrorAction SilentlyContinue";
  return `
$ErrorActionPreference = 'Stop'
$prior = ConvertFrom-Json '${prior}'
foreach ($name in @('Domain','Private','Public')) {
  $old = $prior.$name
  if ($null -eq $old) { continue }
  $current = (Get-NetFirewallProfile -Profile $name).DefaultOutboundAction.ToString()
  if ($current -eq 'Block') { Set-NetFirewallProfile -Profile $name -DefaultOutboundAction $old }
}
Get-NetFirewallRule -Group 'Bailey Host Network Lock' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue${unregister}`;
}

export function buildTemporaryProgramAllowScript(program: string): { name: string; add: string; remove: string } {
  const name = `BaileyHostNetworkLock-Scoped-${createHash("sha256").update(program.toLowerCase()).digest("hex").slice(0, 16)}`;
  return {
    name,
    add: `New-NetFirewallRule -Name '${name}' -DisplayName 'Bailey Host Network Lock - Approved Runtime' -Group 'Bailey Host Network Lock' -Direction Outbound -Action Allow -Program ${psQuote(program)} -Profile Domain,Private,Public | Out-Null`,
    remove: `Get-NetFirewallRule -Name '${name}' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue`,
  };
}

export function buildStaleCleanupScript(): string {
  return `
$ErrorActionPreference = 'Stop'
$rules = @(Get-NetFirewallRule -Group 'Bailey Host Network Lock' -ErrorAction SilentlyContinue)
$task = Get-ScheduledTask -TaskName 'BaileyHostNetworkLockWatchdog' -ErrorAction SilentlyContinue
if ($rules.Count -eq 0 -and $null -eq $task) { return }
if ($null -eq $task) { throw 'Bailey Network Lock rules exist without their recovery task; leaving Windows firewall state unchanged.' }
Start-ScheduledTask -TaskName 'BaileyHostNetworkLockWatchdog'
$deadline = (Get-Date).AddSeconds(20)
do {
  Start-Sleep -Milliseconds 250
  $task = Get-ScheduledTask -TaskName 'BaileyHostNetworkLockWatchdog' -ErrorAction SilentlyContinue
} while ($null -ne $task -and (Get-Date) -lt $deadline)
if ($null -ne $task) { throw 'The Bailey Network Lock recovery task did not finish; firewall state was left for the recovery task.' }
`;
}

export function buildWatchdogScript(statePath: string, fallbackSnapshot?: FirewallSnapshot): string {
  const fallback = fallbackSnapshot
    ? `ConvertFrom-Json '${JSON.stringify(fallbackSnapshot.outbound).replace(/'/g, "''")}'`
    : "$null";
  return `
$ErrorActionPreference = 'Stop'
$statePath = ${psQuote(statePath)}
$fallbackOutbound = ${fallback}
if (-not (Test-Path -LiteralPath $statePath)) {
  if ($null -ne $fallbackOutbound) {
    foreach ($name in @('Domain','Private','Public')) {
      $old = $fallbackOutbound.$name
      $current = (Get-NetFirewallProfile -Profile $name).DefaultOutboundAction.ToString()
      if ($null -ne $old -and $current -eq 'Block') { Set-NetFirewallProfile -Profile $name -DefaultOutboundAction $old }
    }
  }
  Get-NetFirewallRule -Group 'Bailey Host Network Lock' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue
  Unregister-ScheduledTask -TaskName 'BaileyHostNetworkLockWatchdog' -Confirm:$false -ErrorAction SilentlyContinue
  return
}
try { $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json }
catch { $state = $null }
if ($null -eq $state) {
  if ($null -ne $fallbackOutbound) {
    foreach ($name in @('Domain','Private','Public')) {
      $old = $fallbackOutbound.$name
      $current = (Get-NetFirewallProfile -Profile $name).DefaultOutboundAction.ToString()
      if ($null -ne $old -and $current -eq 'Block') { Set-NetFirewallProfile -Profile $name -DefaultOutboundAction $old }
    }
  }
  Get-NetFirewallRule -Group 'Bailey Host Network Lock' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue
  Unregister-ScheduledTask -TaskName 'BaileyHostNetworkLockWatchdog' -Confirm:$false -ErrorAction SilentlyContinue
  return
}
$hasRules = @(Get-NetFirewallRule -Group 'Bailey Host Network Lock' -ErrorAction SilentlyContinue).Count -gt 0
if ($state.mode -eq 'normal' -or $state.mode -eq 'metered') {
  if ($hasRules) { Get-NetFirewallRule -Group 'Bailey Host Network Lock' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue }
  Unregister-ScheduledTask -TaskName 'BaileyHostNetworkLockWatchdog' -Confirm:$false -ErrorAction SilentlyContinue
  return
}
$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$stale = $null -eq $state.heartbeatAt -or ($now - [double]$state.heartbeatAt) -gt 900000
$expired = $state.mode -eq 'temporary-unlock' -and $null -ne $state.unlockUntil -and $now -ge [double]$state.unlockUntil
$recover = $state.mode -eq 'bailey-only' -and $stale
$relock = $state.mode -eq 'temporary-unlock' -and $expired
if (-not $recover -and -not $relock) { return }
if ($recover) {
  $snapshot = $state.snapshot
  foreach ($name in @('Domain','Private','Public')) {
    $old = $snapshot.outbound.$name
    if ($null -eq $old) { continue }
    $current = (Get-NetFirewallProfile -Profile $name).DefaultOutboundAction.ToString()
    if ($current -eq 'Block') { Set-NetFirewallProfile -Profile $name -DefaultOutboundAction $old }
  }
  Get-NetFirewallRule -Group 'Bailey Host Network Lock' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue
  $state.mode = 'normal'
  $state.snapshot = $null
  $state.unlockUntil = $null
  $action = 'Safety watchdog restored Windows networking after Bailey stopped responding.'
} else {
  $snapshot = $state.snapshot
  $program = [string]$snapshot.appExecutable
  $group = 'Bailey Host Network Lock'
  $profiles = 'Domain,Private,Public'
  if (-not (Get-NetFirewallRule -Name 'BaileyHostNetworkLock-App' -ErrorAction SilentlyContinue)) { New-NetFirewallRule -Name 'BaileyHostNetworkLock-App' -DisplayName 'Bailey Host Network Lock - Bailey Host' -Group $group -Direction Outbound -Action Allow -Program $program -Profile $profiles | Out-Null }
  if (-not (Get-NetFirewallRule -Name 'BaileyHostNetworkLock-DnsUdp' -ErrorAction SilentlyContinue)) { New-NetFirewallRule -Name 'BaileyHostNetworkLock-DnsUdp' -DisplayName 'Bailey Host Network Lock - DNS UDP' -Group $group -Direction Outbound -Action Allow -Program 'System' -Protocol UDP -RemotePort 53 -Profile $profiles | Out-Null }
  if (-not (Get-NetFirewallRule -Name 'BaileyHostNetworkLock-DnsTcp' -ErrorAction SilentlyContinue)) { New-NetFirewallRule -Name 'BaileyHostNetworkLock-DnsTcp' -DisplayName 'Bailey Host Network Lock - DNS TCP' -Group $group -Direction Outbound -Action Allow -Program 'System' -Protocol TCP -RemotePort 53 -Profile $profiles | Out-Null }
  if (-not (Get-NetFirewallRule -Name 'BaileyHostNetworkLock-Dhcp' -ErrorAction SilentlyContinue)) { New-NetFirewallRule -Name 'BaileyHostNetworkLock-Dhcp' -DisplayName 'Bailey Host Network Lock - DHCP' -Group $group -Direction Outbound -Action Allow -Program 'System' -Protocol UDP -LocalPort 68,546 -RemotePort 67,547 -Profile $profiles | Out-Null }
  foreach ($name in @('Domain','Private','Public')) { Set-NetFirewallProfile -Profile $name -DefaultOutboundAction Block }
  $state.mode = 'bailey-only'
  $state.unlockUntil = $null
  $action = 'Safety watchdog returned Windows networking to Bailey Only after the temporary unlock expired.'
}
$state.events = @($state.events) + @(@{ at = $now; action = $action })
if ($state.events.Count -gt 20) { $state.events = @($state.events | Select-Object -Last 20) }
$state | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $statePath -Encoding UTF8
if ($recover) { Unregister-ScheduledTask -TaskName 'BaileyHostNetworkLockWatchdog' -Confirm:$false -ErrorAction SilentlyContinue }
`;
}

export class PowerShellNetworkLockAdapter implements NetworkLockAdapter {
  constructor(private readonly statePath?: string, private readonly platform: NodeJS.Platform = process.platform) {}

  private runOnce(script: string): Promise<string> {
    if (this.platform !== "win32") return Promise.reject(new Error("Windows Network Lock is available on Windows only."));
    return new Promise((resolve, reject) => {
      const child = spawn("powershell.exe", ["-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script], {
        windowsHide: true,
        stdio: ["ignore", "pipe", "pipe"],
      });
      let stdout = "";
      let stderr = "";
      child.stdout.setEncoding("utf8").on("data", (chunk) => { stdout += String(chunk); });
      child.stderr.setEncoding("utf8").on("data", (chunk) => { stderr += String(chunk); });
      child.once("error", reject);
      child.once("exit", (code) => code === 0
        ? resolve(stdout.trim())
        : reject(new Error(stderr.trim() || `Windows firewall operation failed (${String(code)}).`)));
    });
  }

  private async run(script: string): Promise<string> {
    try {
      return await this.runOnce(script);
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      if (!this.statePath || !/(access is denied|access denied|administrator|0x80070005)/i.test(reason)) throw error;
      const id = randomUUID();
      const helperPath = `${this.statePath}.elevated-${id}.ps1`;
      const resultPath = `${this.statePath}.elevated-${id}.txt`;
      const helper = `param([string]$OutputPath)\n$ErrorActionPreference='Stop'\ntry {\n  $result = & {\n${script}\n  } 2>&1\n  [System.IO.File]::WriteAllText($OutputPath, ($result | Out-String))\n} catch {\n  [System.IO.File]::WriteAllText($OutputPath, $_.Exception.Message)\n  exit 1\n}`;
      await mkdir(dirname(helperPath), { recursive: true });
      await writeFile(helperPath, helper, "utf8");
      try {
        const args = `-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "${helperPath}" -OutputPath "${resultPath}"`;
        const launch = `$args = ${psQuote(args)}; $child = Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList $args -Wait -PassThru; if ($child.ExitCode -ne 0) { throw (Get-Content -LiteralPath ${psQuote(resultPath)} -Raw -ErrorAction SilentlyContinue) }; if (Test-Path -LiteralPath ${psQuote(resultPath)}) { Get-Content -LiteralPath ${psQuote(resultPath)} -Raw }`;
        return await this.runOnce(launch);
      } catch (elevatedError) {
        let detail = elevatedError instanceof Error ? elevatedError.message : String(elevatedError);
        try { detail = (await readFile(resultPath, "utf8")).trim() || detail; } catch { /* no elevated output file */ }
        throw new Error(detail || reason);
      } finally {
        await Promise.all([
          rm(helperPath, { force: true }).catch(() => {}),
          rm(resultPath, { force: true }).catch(() => {}),
        ]);
      }
    }
  }

  async capture(appExecutable: string): Promise<FirewallSnapshot> {
    const script = `Get-NetFirewallProfile | Select-Object Name,DefaultOutboundAction | ConvertTo-Json -Compress`;
    const raw = await this.run(script);
    let rows: Array<{ Name: string; DefaultOutboundAction: string }>;
    try {
      const parsed = JSON.parse(raw) as Array<{ Name: string; DefaultOutboundAction: string }> | { Name: string; DefaultOutboundAction: string };
      rows = Array.isArray(parsed) ? parsed : [parsed];
    } catch {
      throw new Error("Could not read the current Windows Firewall profile settings.");
    }
    const outbound: FirewallSnapshot["outbound"] = {};
    for (const row of rows) {
      if ((PROFILE_NAMES as readonly string[]).includes(row.Name)
        && (["Allow", "Block", "NotConfigured"] as string[]).includes(row.DefaultOutboundAction)) {
        outbound[row.Name as FirewallProfile] = row.DefaultOutboundAction as FirewallAction;
      }
    }
    if (Object.keys(outbound).length !== PROFILE_NAMES.length) throw new Error("Windows did not return all firewall profile states.");
    return { outbound, appExecutable };
  }

  async applyBaileyOnly(snapshot: FirewallSnapshot): Promise<void> {
    if (this.statePath) {
      const encoded = Buffer.from(buildWatchdogScript(this.statePath, snapshot), "utf16le").toString("base64");
      const taskArgs = psQuote(`-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand ${encoded}`);
      await this.run(`$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ${taskArgs}; $logon = New-ScheduledTaskTrigger -AtLogOn; $repeat = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 1) -RepetitionDuration (New-TimeSpan -Days 3650); $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries; Register-ScheduledTask -TaskName 'BaileyHostNetworkLockWatchdog' -Action $action -Trigger @($logon,$repeat) -Settings $settings -RunLevel Highest -Force | Out-Null`);
    }
    await this.run(buildBaileyOnlyScript(snapshot));
  }

  async restore(snapshot: FirewallSnapshot, keepWatchdog = false): Promise<void> {
    await this.run(buildRestoreScript(snapshot, keepWatchdog));
  }

  async cleanupRules(): Promise<void> {
    if (this.platform !== "win32") return;
    await this.run(buildStaleCleanupScript());
  }

  async withTemporaryProgramAccess<T>(program: string, operation: () => Promise<T>): Promise<T> {
    if (this.platform !== "win32") return operation();
    const path = program.trim();
    if (!path) throw new Error("A runtime executable path is required for scoped network access.");
    const rule = buildTemporaryProgramAllowScript(path);
    await this.run(rule.add);
    try {
      return await operation();
    } finally {
      await this.run(rule.remove);
    }
  }
}

export class NetworkLockManager {
  private state: PersistedState = { version: 1, mode: "normal", events: [] };
  private unlockTimer?: NodeJS.Timeout;
  private relockTask?: Promise<void>;
  private heartbeatTimer?: NodeJS.Timeout;
  private flushTail: Promise<void> = Promise.resolve();

  constructor(
    private readonly filePath: string,
    private readonly adapter: NetworkLockAdapter,
    private readonly appExecutable: string,
    private readonly getTrackedUsage: () => { moduleServicesBytes: number; storageBytes: number } = () => ({ moduleServicesBytes: 0, storageBytes: 0 }),
  ) {}

  async initialize(): Promise<void> {
    try {
      const parsed = JSON.parse(await readFile(this.filePath, "utf8")) as PersistedState;
      if (parsed.version === 1 && ["normal", "metered", "bailey-only", "temporary-unlock"].includes(parsed.mode)) {
        this.state = { ...parsed, events: Array.isArray(parsed.events) ? parsed.events.slice(-20) : [] };
      }
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
    }

    if (this.state.mode === "temporary-unlock" && this.state.snapshot) {
      const remaining = (this.state.unlockUntil ?? 0) - Date.now();
      if (remaining <= 0) await this.enableBaileyOnly(this.state.snapshot, "Timed unlock expired during shutdown; Bailey Only restored.");
      else this.scheduleReLock(remaining);
    } else if (this.state.mode === "bailey-only" && this.state.snapshot) {
      try { await this.adapter.applyBaileyOnly(this.state.snapshot); }
      catch (error) { this.record("Startup reconciliation failed", error instanceof Error ? error.message : String(error)); }
    } else if (this.state.mode === "normal" || this.state.mode === "metered") {
      try { await this.adapter.cleanupRules?.(); }
      catch (error) { this.record("Stale Network Lock cleanup failed", error instanceof Error ? error.message : String(error)); }
    }
    if (this.state.mode === "bailey-only" || this.state.mode === "temporary-unlock") this.startHeartbeat();
    await this.flush();
  }

  status(): NetworkLockStatus {
    return {
      mode: this.state.mode,
      baileyInternetAvailable: this.state.mode !== "bailey-only",
      unlockUntil: this.state.unlockUntil,
      events: this.state.events.map((event) => ({ ...event })),
      dataUsage: {
        whatsappBytes: null,
        ...this.getTrackedUsage(),
        dependencyAndUpdateBytes: null,
        totalBytes: null,
        note: "Storage and host-mediated module service payload bytes are counted. WhatsApp, TLS overhead, dependency/update traffic and system traffic are not measured, so Bailey leaves those totals blank.",
      },
    };
  }

  async withProgramNetworkAccess<T>(program: string, operation: () => Promise<T>): Promise<T> {
    if (this.state.mode !== "bailey-only") return operation();
    if (!this.adapter.withTemporaryProgramAccess) throw new Error("Scoped runtime network access is unavailable for this Windows Network Lock adapter.");
    this.record("Scoped network access started", program);
    await this.flush();
    try {
      const result = await this.adapter.withTemporaryProgramAccess(program, operation);
      this.record("Scoped network access ended", program);
      return result;
    } catch (error) {
      this.record("Scoped network operation failed", error instanceof Error ? error.message : String(error));
      throw error;
    } finally {
      await this.flush();
    }
  }

  private record(action: string, detail?: string): void {
    this.state.events.push({ at: Date.now(), action, detail });
    if (this.state.events.length > 20) this.state.events.splice(0, this.state.events.length - 20);
  }

  private flush(): Promise<void> {
    const serialized = `${JSON.stringify(this.state, null, 2)}\n`;
    this.flushTail = this.flushTail.catch(() => {}).then(async () => {
      await mkdir(dirname(this.filePath), { recursive: true });
      const temporaryPath = `${this.filePath}.${randomUUID()}.tmp`;
      try {
        await writeFile(temporaryPath, serialized, "utf8");
        await rename(temporaryPath, this.filePath);
      } finally {
        await rm(temporaryPath, { force: true }).catch(() => {});
      }
    });
    return this.flushTail;
  }

  private async enableBaileyOnly(snapshot?: FirewallSnapshot, detail?: string): Promise<void> {
    const captured = snapshot ?? await this.adapter.capture(this.appExecutable);
    this.state = { ...this.state, mode: "bailey-only", snapshot: captured, unlockUntil: undefined, heartbeatAt: Date.now() };
    await this.flush();
    try {
      await this.adapter.applyBaileyOnly(captured);
    } catch (error) {
      try { await this.adapter.restore(captured); } catch { /* retain the original operation error */ }
      this.state.mode = "normal";
      this.state.snapshot = undefined;
      this.state.unlockUntil = undefined;
      this.stopHeartbeat();
      this.record("Network Lock setup failed", error instanceof Error ? error.message : String(error));
      await this.flush();
      throw error;
    }
    this.record("Bailey Only enabled", detail);
    await this.flush();
    this.startHeartbeat();
  }

  async setMode(mode: "normal" | "metered" | "bailey-only"): Promise<NetworkLockStatus> {
    if (this.unlockTimer) clearTimeout(this.unlockTimer);
    this.unlockTimer = undefined;
    if (mode !== "bailey-only") this.stopHeartbeat();
    if (mode === "bailey-only") {
      await this.enableBaileyOnly(this.state.snapshot);
    } else {
      const snapshot = this.state.snapshot;
      if (snapshot && (this.state.mode === "bailey-only" || this.state.mode === "temporary-unlock")) await this.adapter.restore(snapshot);
      this.state = { version: 1, mode, events: this.state.events };
      this.record(mode === "normal" ? "Network Lock disabled" : "Metered / Data Saver enabled", mode === "metered" ? "Windows connection unchanged; enable Metered connection in Windows Settings for the active Wi-Fi/mobile profile." : undefined);
      await this.flush();
    }
    return this.status();
  }

  async temporaryUnlock(minutesValue: unknown): Promise<NetworkLockStatus> {
    const minutes = Number(minutesValue);
    if (!UNLOCK_MINUTES.has(minutes)) throw new Error("Temporary unlock must be 5, 15, 30 or 60 minutes.");
    if (this.state.mode !== "bailey-only" || !this.state.snapshot) throw new Error("Enable Bailey Only before using Temporary Unlock.");
    await this.adapter.restore(this.state.snapshot, true);
    this.state.mode = "temporary-unlock";
    this.state.unlockUntil = Date.now() + minutes * 60_000;
    this.record("Temporary Unlock started", `${minutes} minutes`);
    await this.flush();
    this.startHeartbeat();
    this.scheduleReLock(minutes * 60_000);
    return this.status();
  }

  private scheduleReLock(delayMs: number): void {
    if (this.unlockTimer) clearTimeout(this.unlockTimer);
    this.unlockTimer = setTimeout(() => {
      const snapshot = this.state.snapshot;
      if (this.state.mode !== "temporary-unlock" || !snapshot) return;
      this.relockTask = this.enableBaileyOnly(snapshot, "Temporary unlock expired.").catch((error) => {
        this.record("Automatic relock failed", error instanceof Error ? error.message : String(error));
        void this.flush();
      });
    }, Math.min(delayMs, 2_147_483_647));
    this.unlockTimer.unref?.();
  }

  async waitForScheduledRelock(): Promise<void> {
    await this.relockTask;
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    const beat = () => {
      if (this.state.mode !== "bailey-only" && this.state.mode !== "temporary-unlock") return;
      this.state.heartbeatAt = Date.now();
      void this.flush().catch((error) => {
        this.record("Network Lock heartbeat failed", error instanceof Error ? error.message : String(error));
      });
    };
    this.heartbeatTimer = setInterval(beat, 60_000);
    this.heartbeatTimer.unref?.();
    beat();
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer);
    this.heartbeatTimer = undefined;
  }

  async dispose(): Promise<void> {
    if (this.unlockTimer) clearTimeout(this.unlockTimer);
    this.unlockTimer = undefined;
    this.stopHeartbeat();
    // The mode deliberately survives tray close / normal app shutdown. Explicitly
    // disabling Network Lock restores the saved profile state.
  }
}
