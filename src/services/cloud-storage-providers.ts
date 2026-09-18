import {
  DeleteObjectCommand,
  GetObjectCommand,
  HeadObjectCommand,
  ListObjectsV2Command,
  PutObjectCommand,
  S3Client,
} from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import { BlobServiceClient, BlobSASPermissions, StorageSharedKeyCredential } from "@azure/storage-blob";
import { Storage } from "@google-cloud/storage";
import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import type { StorageObjectInfo, StorageProvider } from "./storage-service";

export interface S3ProviderOptions {
  bucket: string;
  region: string;
  endpoint?: string;
  forcePathStyle?: boolean;
  accessKeyId: string;
  secretAccessKey: string;
  sessionToken?: string;
}

export class S3CompatibleStorageProvider implements StorageProvider {
  readonly kind = "s3";
  private readonly client: S3Client;

  constructor(private readonly options: S3ProviderOptions) {
    this.client = new S3Client({
      region: options.region,
      endpoint: options.endpoint || undefined,
      forcePathStyle: Boolean(options.forcePathStyle),
      credentials: {
        accessKeyId: options.accessKeyId,
        secretAccessKey: options.secretAccessKey,
        sessionToken: options.sessionToken || undefined,
      },
    });
  }

  async put(key: string, data: Buffer, metadata?: Record<string, string>): Promise<StorageObjectInfo> {
    await this.client.send(new PutObjectCommand({ Bucket: this.options.bucket, Key: key, Body: data, Metadata: metadata }));
    return { key, size: data.byteLength, updatedAt: Date.now() };
  }

  async get(key: string): Promise<Buffer> {
    const result = await this.client.send(new GetObjectCommand({ Bucket: this.options.bucket, Key: key }));
    if (!result.Body) throw new Error("S3 returned an empty object body.");
    return Buffer.from(await result.Body.transformToByteArray());
  }

  async delete(key: string): Promise<void> {
    await this.client.send(new DeleteObjectCommand({ Bucket: this.options.bucket, Key: key }));
  }

  async exists(key: string): Promise<boolean> {
    try {
      await this.client.send(new HeadObjectCommand({ Bucket: this.options.bucket, Key: key }));
      return true;
    } catch (error) {
      const status = (error as { $metadata?: { httpStatusCode?: number } }).$metadata?.httpStatusCode;
      if (status === 404) return false;
      throw error;
    }
  }

  async list(prefix = ""): Promise<StorageObjectInfo[]> {
    const objects: StorageObjectInfo[] = [];
    let token: string | undefined;
    do {
      const result = await this.client.send(new ListObjectsV2Command({
        Bucket: this.options.bucket,
        Prefix: prefix || undefined,
        ContinuationToken: token,
      }));
      for (const item of result.Contents ?? []) {
        if (!item.Key) continue;
        objects.push({
          key: item.Key,
          size: Number(item.Size ?? 0),
          updatedAt: item.LastModified?.getTime(),
        });
      }
      token = result.IsTruncated ? result.NextContinuationToken : undefined;
    } while (token);
    return objects;
  }

  async createTemporaryLink(key: string, expiresSeconds: number): Promise<string> {
    return getSignedUrl(
      this.client,
      new GetObjectCommand({ Bucket: this.options.bucket, Key: key }),
      { expiresIn: expiresSeconds },
    );
  }
}

export interface AzureBlobProviderOptions {
  accountName: string;
  accountKey: string;
  container: string;
}

async function readableToBuffer(stream: NodeJS.ReadableStream | undefined): Promise<Buffer> {
  if (!stream) return Buffer.alloc(0);
  const chunks: Buffer[] = [];
  for await (const chunk of stream as AsyncIterable<Buffer | Uint8Array | string>) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  return Buffer.concat(chunks);
}

export class AzureBlobStorageProvider implements StorageProvider {
  readonly kind = "azure";
  private readonly container;

  constructor(private readonly options: AzureBlobProviderOptions) {
    const credential = new StorageSharedKeyCredential(options.accountName, options.accountKey);
    const service = new BlobServiceClient(`https://${options.accountName}.blob.core.windows.net`, credential);
    this.container = service.getContainerClient(options.container);
  }

  async put(key: string, data: Buffer, metadata?: Record<string, string>): Promise<StorageObjectInfo> {
    await this.container.getBlockBlobClient(key).uploadData(data, { metadata });
    return { key, size: data.byteLength, updatedAt: Date.now() };
  }

  async get(key: string): Promise<Buffer> {
    const response = await this.container.getBlobClient(key).download();
    return readableToBuffer(response.readableStreamBody);
  }

  async delete(key: string): Promise<void> {
    await this.container.deleteBlob(key, { deleteSnapshots: "include" });
  }

  async exists(key: string): Promise<boolean> {
    return this.container.getBlobClient(key).exists();
  }

  async list(prefix = ""): Promise<StorageObjectInfo[]> {
    const output: StorageObjectInfo[] = [];
    for await (const item of this.container.listBlobsFlat({ prefix: prefix || undefined })) {
      output.push({
        key: item.name,
        size: Number(item.properties.contentLength ?? 0),
        updatedAt: item.properties.lastModified?.getTime(),
      });
    }
    return output;
  }

  async createTemporaryLink(key: string, expiresSeconds: number): Promise<string> {
    return this.container.getBlobClient(key).generateSasUrl({
      permissions: BlobSASPermissions.parse("r"),
      expiresOn: new Date(Date.now() + expiresSeconds * 1000),
    });
  }
}

export interface GoogleCloudProviderOptions {
  projectId: string;
  clientEmail: string;
  privateKey: string;
  bucket: string;
}

export class GoogleCloudStorageProvider implements StorageProvider {
  readonly kind = "gcs";
  private readonly bucket;

  constructor(private readonly options: GoogleCloudProviderOptions) {
    const storage = new Storage({
      projectId: options.projectId,
      credentials: { client_email: options.clientEmail, private_key: options.privateKey.replace(/\\n/g, "\n") },
    });
    this.bucket = storage.bucket(options.bucket);
  }

  async put(key: string, data: Buffer, metadata?: Record<string, string>): Promise<StorageObjectInfo> {
    await this.bucket.file(key).save(data, { resumable: false, metadata: { metadata } });
    return { key, size: data.byteLength, updatedAt: Date.now() };
  }

  async get(key: string): Promise<Buffer> {
    const [data] = await this.bucket.file(key).download();
    return data;
  }

  async delete(key: string): Promise<void> {
    await this.bucket.file(key).delete({ ignoreNotFound: true });
  }

  async exists(key: string): Promise<boolean> {
    const [exists] = await this.bucket.file(key).exists();
    return exists;
  }

  async list(prefix = ""): Promise<StorageObjectInfo[]> {
    const [files] = await this.bucket.getFiles({ prefix: prefix || undefined, autoPaginate: true });
    return Promise.all(files.map(async (file) => {
      const [metadata] = await file.getMetadata();
      return {
        key: file.name,
        size: Number(metadata.size ?? 0),
        updatedAt: metadata.updated ? Date.parse(String(metadata.updated)) : undefined,
      };
    }));
  }

  async createTemporaryLink(key: string, expiresSeconds: number): Promise<string> {
    const [url] = await this.bucket.file(key).getSignedUrl({
      action: "read",
      expires: Date.now() + expiresSeconds * 1000,
      version: "v4",
    });
    return url;
  }
}

export interface SupabaseProviderOptions {
  url: string;
  serviceKey: string;
  bucket: string;
}

export class SupabaseStorageProvider implements StorageProvider {
  readonly kind = "supabase";
  private readonly client: SupabaseClient;

  constructor(private readonly options: SupabaseProviderOptions) {
    this.client = createClient(options.url, options.serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
    });
  }

  private store() {
    return this.client.storage.from(this.options.bucket);
  }

  async put(key: string, data: Buffer): Promise<StorageObjectInfo> {
    const { error } = await this.store().upload(key, data, { upsert: true });
    if (error) throw error;
    return { key, size: data.byteLength, updatedAt: Date.now() };
  }

  async get(key: string): Promise<Buffer> {
    const { data, error } = await this.store().download(key);
    if (error) throw error;
    if (!data) throw new Error("Supabase returned no object data.");
    return Buffer.from(await data.arrayBuffer());
  }

  async delete(key: string): Promise<void> {
    const { error } = await this.store().remove([key]);
    if (error) throw error;
  }

  async exists(key: string): Promise<boolean> {
    try {
      await this.get(key);
      return true;
    } catch (error) {
      const status = (error as { statusCode?: string | number; status?: number }).status ?? Number((error as { statusCode?: string }).statusCode);
      if (status === 400 || status === 404) return false;
      throw error;
    }
  }

  async list(prefix = ""): Promise<StorageObjectInfo[]> {
    const slash = prefix.lastIndexOf("/");
    const directory = slash >= 0 ? prefix.slice(0, slash) : "";
    const search = slash >= 0 ? prefix.slice(slash + 1) : prefix;
    const { data, error } = await this.store().list(directory, {
      limit: 1000,
      search: search || undefined,
      sortBy: { column: "name", order: "asc" },
    });
    if (error) throw error;
    return (data ?? []).filter((item) => item.id).map((item) => ({
      key: directory ? `${directory}/${item.name}` : item.name,
      size: Number((item.metadata as { size?: number } | null)?.size ?? 0),
      updatedAt: item.updated_at ? Date.parse(item.updated_at) : undefined,
    }));
  }

  async createTemporaryLink(key: string, expiresSeconds: number): Promise<string> {
    const { data, error } = await this.store().createSignedUrl(key, expiresSeconds);
    if (error) throw error;
    return data.signedUrl;
  }
}
