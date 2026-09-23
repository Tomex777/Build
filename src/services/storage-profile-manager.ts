import { join } from "node:path";
import type { ResolvedStorageProfile, StorageProfileStore } from "./storage-profile-store";
import { StorageHostService, LocalStorageProvider, type StorageProvider } from "./storage-service";
import {
  AzureBlobStorageProvider,
  GoogleCloudStorageProvider,
  S3CompatibleStorageProvider,
  SupabaseStorageProvider,
} from "./cloud-storage-providers";

function text(config: Record<string, string | boolean>, key: string): string {
  return String(config[key] ?? "").trim();
}

function secret(profile: ResolvedStorageProfile, key: string): string {
  const value = String(profile.secrets[key] ?? "");
  if (!value) throw new Error(`Storage profile ${profile.name} is missing secret ${key}.`);
  return value;
}

export function storageProviderFromProfile(profile: ResolvedStorageProfile, userDataRoot: string): StorageProvider {
  if (profile.provider === "local") {
    return new LocalStorageProvider(join(userDataRoot, "storage", "profiles", profile.name));
  }
  if (profile.provider === "s3") {
    return new S3CompatibleStorageProvider({
      bucket: text(profile.config, "bucket"),
      region: text(profile.config, "region"),
      endpoint: text(profile.config, "endpoint") || undefined,
      forcePathStyle: Boolean(profile.config.forcePathStyle),
      accessKeyId: secret(profile, "accessKeyId"),
      secretAccessKey: secret(profile, "secretAccessKey"),
      sessionToken: profile.secrets.sessionToken || undefined,
    });
  }
  if (profile.provider === "azure") {
    return new AzureBlobStorageProvider({
      accountName: text(profile.config, "accountName"),
      container: text(profile.config, "container"),
      accountKey: secret(profile, "accountKey"),
    });
  }
  if (profile.provider === "gcs") {
    return new GoogleCloudStorageProvider({
      projectId: text(profile.config, "projectId"),
      clientEmail: text(profile.config, "clientEmail"),
      bucket: text(profile.config, "bucket"),
      privateKey: secret(profile, "privateKey"),
    });
  }
  return new SupabaseStorageProvider({
    url: text(profile.config, "url"),
    bucket: text(profile.config, "bucket"),
    serviceKey: secret(profile, "serviceKey"),
  });
}

export function createStorageHostService(store: StorageProfileStore, userDataRoot: string, onPayloadBytes?: (bytes: number) => void): StorageHostService {
  const service = new StorageHostService(onPayloadBytes);
  for (const profile of store.resolved()) {
    try {
      service.addProfile(profile.name, storageProviderFromProfile(profile, userDataRoot), profile.isDefault);
    } catch (error) {
      console.warn(`[storage:${profile.name}] ${error instanceof Error ? error.message : String(error)}`);
    }
  }
  return service;
}
