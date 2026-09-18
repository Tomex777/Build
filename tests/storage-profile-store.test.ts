import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { StorageProfileStore } from "../src/services/storage-profile-store";

const dirs: string[] = [];
afterEach(async () => {
  await Promise.all(dirs.splice(0).map((dir) => rm(dir, { recursive: true, force: true })));
});

describe("StorageProfileStore", () => {
  it("keeps provider credentials out of plaintext profile JSON", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-storage-profile-"));
    dirs.push(root);
    const path = join(root, "storage-profiles.json");
    const codec = {
      encode(value: string) { return Buffer.from(`encrypted:${value}`, "utf8").toString("base64"); },
      decode(value: string) { return Buffer.from(value, "base64").toString("utf8").replace(/^encrypted:/, ""); },
    };
    const store = new StorageProfileStore(path, codec);
    await store.load();
    await store.upsert({
      name: "media",
      provider: "s3",
      config: { bucket: "anime", region: "auto", endpoint: "https://example.invalid" },
      secrets: { accessKeyId: "public-id", secretAccessKey: "do-not-store-plain" },
    });
    const raw = await readFile(path, "utf8");
    expect(raw).not.toContain("do-not-store-plain");
    expect(store.listForUi().find((profile) => profile.name === "media")?.secretFields).toContain("secretAccessKey");
    expect(store.resolved().find((profile) => profile.name === "media")?.secrets.secretAccessKey).toBe("do-not-store-plain");
  });

  it("always starts with a usable local default profile", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-storage-default-"));
    dirs.push(root);
    const store = new StorageProfileStore(join(root, "profiles.json"), { encode: (v) => v, decode: (v) => v });
    await store.load();
    expect(store.listForUi()).toEqual([
      expect.objectContaining({ name: "default", provider: "local", isDefault: true }),
    ]);
  });
});
