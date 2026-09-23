import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ExternalModuleManager, type ExternalHostServiceContext } from "../src/external/external-module-manager";
import { NetworkAccessPolicyStore, registerNetworkHttpService } from "../src/services/network-access";

const roots: string[] = [];
afterEach(async () => {
  vi.restoreAllMocks();
  await Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true })));
});

describe("Bailey host-mediated network access", () => {
  it("enforces module allow and deny rules, including subdomains", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-network-policy-"));
    roots.push(root);
    const store = new NetworkAccessPolicyStore(join(root, "policies.json"));
    await store.load();
    await store.set("economy", ["example.com", "*.api.test"], ["blocked.example.com"]);
    expect(store.permits("economy", "api.example.com")).toBe(true);
    expect(store.permits("economy", "api.test")).toBe(true);
    expect(store.permits("economy", "blocked.example.com")).toBe(false);
    expect(store.permits("economy", "example.net")).toBe(false);
    const loaded = new NetworkAccessPolicyStore(join(root, "policies.json"));
    await loaded.load();
    expect(loaded.get("economy")).toEqual({ moduleId: "economy", allow: ["api.test", "example.com"], deny: ["blocked.example.com"] });
    await expect(store.set("economy", ["https://example.com"], [])).rejects.toThrow("Invalid network domain");
    await expect(store.set("economy", ["127.0.0.1"], [])).rejects.toThrow("public domain names");
  });

  it("applies HTTPS, permission and domain gates before fetch and counts payload bytes", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-http-service-"));
    roots.push(root);
    const policies = new NetworkAccessPolicyStore(join(root, "policies.json"));
    await policies.load();
    await policies.set("economy", ["example.com"], []);

    const manager = new ExternalModuleManager(join(root, "modules"), () => ({}), () => true, join(root, "data"), () => ["network.http"]);
    const bytes: number[] = [];
    registerNetworkHttpService(manager, policies, (_id, count) => bytes.push(count));
    const service = (manager as any).services.get("network:request");
    const context = { moduleId: "economy", moduleName: "Economy", capabilities: ["services"], permissions: ["network.http"], dataDirectory: join(root, "data", "economy") } as ExternalHostServiceContext;
    const fakeFetch = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("hello", { status: 200 }));

    const result = await service.handler({ url: "https://api.example.com/status", method: "POST", text: "abc" }, context);
    expect(result).toMatchObject({ status: 200, body: "hello", requestBytes: 3, responseBytes: 5 });
    expect(bytes).toEqual([8]);
    expect(fakeFetch).toHaveBeenCalledOnce();
    expect(fakeFetch.mock.calls[0][1]).toMatchObject({ redirect: "manual", body: "abc" });

    await expect(service.handler({ url: "http://api.example.com/" }, context)).rejects.toThrow("HTTPS public domain URLs only");
    await expect(service.handler({ url: "https://127.0.0.1/" }, context)).rejects.toThrow("HTTPS public domain URLs only");
    await expect(service.handler({ url: "https://elsewhere.test/" }, context)).rejects.toThrow("Domain is not allowed");
    expect(fakeFetch).toHaveBeenCalledOnce();

    expect(await readFile(join(root, "policies.json"), "utf8")).toContain("economy");
  });

  it("sends decoded binary request data and rejects oversized bodies", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-http-binary-"));
    roots.push(root);
    const policies = new NetworkAccessPolicyStore(join(root, "policies.json"));
    await policies.load();
    await policies.set("economy", ["example.com"], []);
    const manager = new ExternalModuleManager(join(root, "modules"), () => ({}), () => true, join(root, "data"), () => ["network.http"]);
    registerNetworkHttpService(manager, policies);
    const service = (manager as any).services.get("network:request");
    const context = { moduleId: "economy", moduleName: "Economy", capabilities: ["services"], permissions: ["network.http"], dataDirectory: join(root, "data") } as ExternalHostServiceContext;
    const fakeFetch = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 204 }));
    await service.handler({ url: "https://example.com/upload", method: "PUT", base64: "AAEC" }, context);
    const body = fakeFetch.mock.calls[0][1]?.body as Blob;
    expect([...new Uint8Array(await body.arrayBuffer())]).toEqual([0, 1, 2]);
    await expect(service.handler({ url: "https://example.com/upload", method: "PUT", text: "x".repeat(256 * 1024 + 1) }, context)).rejects.toThrow("256 KB");
  });
});
