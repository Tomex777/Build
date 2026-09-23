import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  buildBaileyOnlyScript,
  buildRestoreScript,
  buildTemporaryProgramAllowScript,
  buildStaleCleanupScript,
  buildWatchdogScript,
  createFirewallRules,
  NetworkLockManager,
  type FirewallSnapshot,
  type NetworkLockAdapter,
} from "../src/services/network-lock";

const roots: string[] = [];
afterEach(async () => {
  vi.useRealTimers();
  await Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true })));
});

const snapshot: FirewallSnapshot = {
  appExecutable: "C:\\Program Files\\Bailey\\Bailey Host.exe",
  outbound: { Domain: "Allow", Private: "Allow", Public: "NotConfigured" },
};

class FakeAdapter implements NetworkLockAdapter {
  actions: string[] = [];
  failApply = false;
  async capture(executable: string) { this.actions.push("capture"); return { ...snapshot, appExecutable: executable }; }
  async applyBaileyOnly() { this.actions.push("apply"); if (this.failApply) throw new Error("mock firewall failure"); }
  async restore() { this.actions.push("restore"); }
  async withTemporaryProgramAccess<T>(program: string, operation: () => Promise<T>) {
    this.actions.push(`allow:${program}`);
    try { return await operation(); }
    finally { this.actions.push(`remove:${program}`); }
  }
}

async function manager(adapter = new FakeAdapter()) {
  const root = await mkdtemp(join(tmpdir(), "bailey-network-lock-"));
  roots.push(root);
  const filePath = join(root, "network-lock.json");
  const instance = new NetworkLockManager(filePath, adapter, snapshot.appExecutable);
  await instance.initialize();
  return { instance, adapter, filePath };
}

describe("Windows Network Lock policy", () => {
  it("namespaces rules and allows Bailey, DNS and DHCP without touching unrelated rules", () => {
    const rules = createFirewallRules(snapshot.appExecutable).join("\n");
    expect(rules).toContain("BaileyHostNetworkLock-App");
    expect(rules).toContain("Bailey Host Network Lock - DHCP");
    expect(rules).toContain("-RemotePort 53");
    expect(rules).toContain("C:\\Program Files\\Bailey");
    expect(buildBaileyOnlyScript(snapshot)).toContain("Set-NetFirewallProfile -Profile $name -DefaultOutboundAction Block");
    const restore = buildRestoreScript(snapshot);
    expect(restore).toContain("if ($current -eq 'Block')");
    expect(restore).toContain("Get-NetFirewallRule -Group 'Bailey Host Network Lock'");
    expect(restore).not.toContain("Remove-NetFirewallRule -All");
  });

  it("installs a watchdog that restores only after a stale heartbeat or expired unlock", () => {
    const watchdog = buildWatchdogScript("C:\\Users\\Tester\\AppData\\Bailey\\network-lock.json", snapshot);
    expect(watchdog).toContain("Bailey Host Network Lock");
    expect(watchdog).toContain("-gt 900000");
    expect(watchdog).toContain("$state.mode -eq 'temporary-unlock' -and $expired");
    expect(watchdog).toContain("if ($current -eq 'Block')");
    expect(watchdog).toContain("if (-not (Test-Path -LiteralPath $statePath))");
    expect(watchdog).toContain("$fallbackOutbound = ConvertFrom-Json");
    expect(watchdog).not.toContain("Remove-NetFirewallRule -All");
  });

  it("runs recovery before startup cleanup and leaves orphaned firewall state untouched", () => {
    const cleanup = buildStaleCleanupScript();
    expect(cleanup).toContain("Start-ScheduledTask -TaskName 'BaileyHostNetworkLockWatchdog'");
    expect(cleanup).toContain("rules exist without their recovery task; leaving Windows firewall state unchanged");
    expect(cleanup).not.toContain("Remove-NetFirewallRule -All");
    expect(cleanup.indexOf("Start-ScheduledTask")).toBeLessThan(cleanup.indexOf("$deadline"));
  });

  it("restores the exact prior firewall state on emergency disable", async () => {
    const { instance, adapter } = await manager();
    await instance.setMode("bailey-only");
    expect(instance.status().mode).toBe("bailey-only");
    await instance.setMode("normal");
    expect(instance.status().mode).toBe("normal");
    expect(adapter.actions).toEqual(["capture", "apply", "restore"]);
  });

  it("rolls back a partial lock setup when Windows rejects a rule", async () => {
    const adapter = new FakeAdapter();
    adapter.failApply = true;
    const { instance } = await manager(adapter);
    await expect(instance.setMode("bailey-only")).rejects.toThrow("mock firewall failure");
    expect(instance.status().mode).toBe("normal");
    expect(instance.status().events.at(-1)?.action).toBe("Network Lock setup failed");
    expect(adapter.actions).toEqual(["capture", "apply", "restore"]);
  });

  it("relocks after an allowed temporary-unlock duration", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-09-23T09:00:00Z"));
    const { instance, adapter } = await manager();
    await instance.setMode("bailey-only");
    await instance.temporaryUnlock(5);
    expect(instance.status().mode).toBe("temporary-unlock");
    await vi.advanceTimersByTimeAsync(5 * 60_000);
    await instance.waitForScheduledRelock();
    expect(instance.status().mode).toBe("bailey-only");
    expect(adapter.actions).toEqual(["capture", "apply", "restore", "apply"]);
  });

  it("persists mode, remaining unlock time and recent actions", async () => {
    const { instance, filePath } = await manager();
    await instance.setMode("bailey-only");
    const document = JSON.parse(await readFile(filePath, "utf8")) as { mode: string; snapshot?: FirewallSnapshot };
    expect(document.mode).toBe("bailey-only");
    expect(document.snapshot?.appExecutable).toBe(snapshot.appExecutable);
  });

  it("grants only the approved runtime while Bailey Only remains active", async () => {
    const scoped = buildTemporaryProgramAllowScript("C:\\Bailey\\Modules\\.bailey-venv\\Scripts\\python.exe");
    expect(scoped.add).toContain("-Direction Outbound -Action Allow");
    expect(scoped.add).toContain("-Group 'Bailey Host Network Lock'");
    expect(scoped.add).toContain("Scripts\\python.exe");
    expect(scoped.remove).toContain(scoped.name);

    const { instance, adapter } = await manager();
    await instance.setMode("bailey-only");
    const output = await instance.withProgramNetworkAccess("C:\\Bailey\\python.exe", async () => {
      expect(instance.status().mode).toBe("bailey-only");
      return "installed";
    });
    expect(output).toBe("installed");
    expect(adapter.actions.slice(-2)).toEqual(["allow:C:\\Bailey\\python.exe", "remove:C:\\Bailey\\python.exe"]);
  });
});
