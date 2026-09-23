import { mkdir, mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { ExternalModuleManager, type ExternalHostServiceContext } from "../src/external/external-module-manager";
import { registerDatabaseServices } from "../src/services/sqlite-service";
import { LocalStorageProvider, StorageHostService } from "../src/services/storage-service";

const roots: string[] = [];
afterEach(async () => Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true }))));

function testContext(dataDirectory: string): ExternalHostServiceContext {
  return { moduleId: "economy", moduleName: "Economy", capabilities: ["services", "storage"], permissions: ["database.read", "database.write", "storage.read", "storage.write"], dataDirectory };
}

async function root(prefix: string): Promise<string> {
  const path = await mkdtemp(join(tmpdir(), prefix));
  roots.push(path);
  return path;
}

describe("SQLite and local storage host services", () => {
  it("keeps realistic account transfers atomic and prevents overdrafts", async () => {
    const path = await root("bailey-economy-transaction-");
    const manager = new ExternalModuleManager(join(path, "modules"), () => ({}), () => true, join(path, "data"), () => ["database.read", "database.write"]);
    registerDatabaseServices(manager);
    const services = (manager as any).services;
    const context = testContext(join(path, "data", "economy"));
    await mkdir(join(path, "data", "economy"), { recursive: true });
    const exec = services.get("database:exec").handler;
    const run = services.get("database:run").handler;
    const all = services.get("database:all").handler;

    await exec({ sql: "CREATE TABLE accounts (id TEXT PRIMARY KEY, balance_cents INTEGER NOT NULL CHECK (balance_cents >= 0)); INSERT INTO accounts VALUES ('alice', 12500), ('bob', 3000), ('merchant', 0);" }, context);
    await exec({ sql: "BEGIN IMMEDIATE; UPDATE accounts SET balance_cents = balance_cents - 4250 WHERE id = 'alice' AND balance_cents >= 4250; UPDATE accounts SET balance_cents = balance_cents + 4250 WHERE id = 'bob'; COMMIT;" }, context);
    expect((await run({ sql: "UPDATE accounts SET balance_cents = balance_cents - @amount WHERE id = @id AND balance_cents >= @amount", params: { amount: 9000, id: "alice" } }, context)).changes).toBe(0);

    expect(() => exec({ sql: "BEGIN IMMEDIATE; UPDATE accounts SET balance_cents = balance_cents - 1 WHERE id = 'alice'; UPDATE accounts SET balance_cents = -1 WHERE id = 'merchant'; COMMIT;" }, context)).toThrow("CHECK constraint failed");
    const result = await all({ sql: "SELECT id, balance_cents FROM accounts ORDER BY id" }, context);
    expect(result.rows).toEqual([
      { id: "alice", balance_cents: 8250 },
      { id: "bob", balance_cents: 7250 },
      { id: "merchant", balance_cents: 0 },
    ]);
    expect(() => all({ sql: "DELETE FROM accounts" }, context)).toThrow("only accepts SELECT");
  });

  it("stores, reads, lists and deletes provider-neutral objects using local storage", async () => {
    const path = await root("bailey-local-storage-service-");
    const manager = new ExternalModuleManager(join(path, "modules"), () => ({}), () => true, join(path, "data"), () => ["storage.read", "storage.write", "storage.delete"]);
    let measured = 0;
    const service = new StorageHostService((bytes) => { measured += bytes; });
    service.addProfile("default", new LocalStorageProvider(join(path, "objects")), true);
    service.register(manager);
    const services = (manager as any).services;
    const context = testContext(join(path, "data", "economy"));
    const call = (name: string, input: unknown) => services.get(name).handler(input, context);

    expect(await call("storage:put", { key: "accounts/alice.json", text: "{\"cents\":12500}" })).toMatchObject({ key: "accounts/alice.json", size: 15 });
    expect(await call("storage:get", { key: "accounts/alice.json", encoding: "text" })).toMatchObject({ text: "{\"cents\":12500}" });
    expect(await call("storage:list", { prefix: "accounts" })).toMatchObject({ objects: [{ key: "accounts/alice.json", size: 15 }] });
    expect(measured).toBe(30);
    await expect(call("storage:put", { key: "../escape", text: "bad" })).rejects.toThrow("invalid");
    await call("storage:delete", { key: "accounts/alice.json" });
    expect(await call("storage:exists", { key: "accounts/alice.json" })).toMatchObject({ exists: false });
  });
});
