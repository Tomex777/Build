import { Readable } from "node:stream";
import { describe, expect, it, vi } from "vitest";
import { S3CompatibleStorageProvider, AzureBlobStorageProvider, GoogleCloudStorageProvider, SupabaseStorageProvider } from "../src/services/cloud-storage-providers";

describe("cloud object storage adapters with mocked SDK clients", () => {
  it("maps S3-compatible put/get/exists/list/delete and link operations", async () => {
    const provider = new S3CompatibleStorageProvider({ bucket: "test-bucket", region: "us-east-1", endpoint: "https://storage.example.test", accessKeyId: "test", secretAccessKey: "test" });
    const send = vi.fn(async (command: any) => {
      const name = command.constructor.name;
      if (name === "GetObjectCommand") return { Body: { transformToByteArray: async () => new Uint8Array([1, 2]) } };
      if (name === "HeadObjectCommand") return {};
      if (name === "ListObjectsV2Command") return { Contents: [{ Key: "a.bin", Size: 2 }], IsTruncated: false };
      return {};
    });
    (provider as any).client = { send };
    expect(await provider.put("a.bin", Buffer.from([1, 2]))).toMatchObject({ key: "a.bin", size: 2 });
    expect([...await provider.get("a.bin")]).toEqual([1, 2]);
    expect(await provider.exists("a.bin")).toBe(true);
    expect(await provider.list()).toMatchObject([{ key: "a.bin", size: 2 }]);
    await provider.delete("a.bin");
    expect(send).toHaveBeenCalledTimes(5);
  });

  it("maps Azure Blob operations through mocked container clients", async () => {
    const provider = new AzureBlobStorageProvider({ accountName: "example", accountKey: "ZmFrZQ==", container: "objects" });
    const uploadData = vi.fn(async () => {});
    const download = vi.fn(async () => ({ readableStreamBody: Readable.from([Buffer.from("azure")]) }));
    const deleteBlob = vi.fn(async () => {});
    const exists = vi.fn(async () => true);
    const listBlobsFlat = vi.fn(async function* () { yield { name: "a.txt", properties: { contentLength: 5 } }; });
    const generateSasUrl = vi.fn(async () => "https://example.test/sas");
    (provider as any).container = {
      getBlockBlobClient: () => ({ uploadData }),
      getBlobClient: () => ({ download, exists, generateSasUrl }),
      deleteBlob,
      listBlobsFlat,
    };
    expect(await provider.put("a.txt", Buffer.from("azure"))).toMatchObject({ size: 5 });
    expect((await provider.get("a.txt")).toString()).toBe("azure");
    expect(await provider.exists("a.txt")).toBe(true);
    expect(await provider.list()).toMatchObject([{ key: "a.txt", size: 5 }]);
    await provider.delete("a.txt");
    expect(await provider.createTemporaryLink("a.txt", 60)).toContain("/sas");
    expect(uploadData).toHaveBeenCalledOnce();
    expect(deleteBlob).toHaveBeenCalledOnce();
  });

  it("maps Google Cloud Storage operations through mocked bucket clients", async () => {
    const provider = new GoogleCloudStorageProvider({ projectId: "test", clientEmail: "bailey@example.test", privateKey: "test-key", bucket: "objects" });
    const save = vi.fn(async () => {});
    const download = vi.fn(async () => [Buffer.from("gcs")]);
    const deleteFile = vi.fn(async () => {});
    const exists = vi.fn(async () => [true]);
    const getMetadata = vi.fn(async () => [{ size: 3, updated: new Date().toISOString() }]);
    const getSignedUrl = vi.fn(async () => ["https://example.test/signed"]);
    const file = { name: "a.txt", save, download, delete: deleteFile, exists, getMetadata, getSignedUrl };
    const getFiles = vi.fn(async () => [[file]]);
    (provider as any).bucket = { file: () => file, getFiles };
    expect(await provider.put("a.txt", Buffer.from("gcs"))).toMatchObject({ size: 3 });
    expect((await provider.get("a.txt")).toString()).toBe("gcs");
    expect(await provider.exists("a.txt")).toBe(true);
    expect(await provider.list()).toMatchObject([{ key: "a.txt", size: 3 }]);
    await provider.delete("a.txt");
    expect(await provider.createTemporaryLink("a.txt", 60)).toContain("/signed");
    expect(save).toHaveBeenCalledOnce();
    expect(getFiles).toHaveBeenCalledOnce();
  });

  it("maps Supabase Storage operations through mocked bucket methods", async () => {
    const provider = new SupabaseStorageProvider({ url: "https://project.supabase.co", serviceKey: "service-key", bucket: "objects" });
    const upload = vi.fn(async () => ({ data: {}, error: null }));
    const download = vi.fn(async () => ({ data: new Blob(["supabase"]), error: null }));
    const remove = vi.fn(async () => ({ data: [], error: null }));
    const list = vi.fn(async () => ({ data: [{ id: "object-id", name: "a.txt", metadata: { size: 8 }, updated_at: "2026-01-01T00:00:00Z" }], error: null }));
    const createSignedUrl = vi.fn(async () => ({ data: { signedUrl: "https://example.test/signed" }, error: null }));
    (provider as any).client = { storage: { from: () => ({ upload, download, remove, list, createSignedUrl }) } };
    expect(await provider.put("a.txt", Buffer.from("supabase"))).toMatchObject({ size: 8 });
    expect((await provider.get("a.txt")).toString()).toBe("supabase");
    expect(await provider.list()).toMatchObject([{ key: "a.txt", size: 8 }]);
    expect(await provider.createTemporaryLink("a.txt", 60)).toContain("/signed");
    await provider.delete("a.txt");
    expect(upload).toHaveBeenCalledOnce();
    expect(remove).toHaveBeenCalledOnce();
  });
});
