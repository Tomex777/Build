export type MediaType = "anime" | "movie" | "tv" | "manga" | "music";

export interface ExtensionDescriptorInput {
  id: string;
  name: string;
  mediaTypes: readonly MediaType[];
  enabled?: boolean;
  /** Lower values run first after the configured default. Registration order breaks ties. */
  priority?: number;
}

export interface ExtensionDescriptor extends Omit<ExtensionDescriptorInput, "enabled" | "priority"> {
  readonly mediaTypes: readonly MediaType[];
  readonly enabled: boolean;
  readonly priority: number;
}

export interface SearchRequest {
  readonly query: string;
  readonly mediaType: MediaType;
  /** Aborted when the request is cancelled or its timeout expires. */
  readonly signal: AbortSignal;
}

export interface ExtensionSearchResult {
  title: string;
  year?: number | string;
  season?: number | string;
  episode?: number | string;
  /** Additional provider metadata is preserved, but host-owned attribution fields are overwritten. */
  [key: string]: unknown;
}

export interface MediaExtension {
  readonly descriptor: ExtensionDescriptorInput;
  search(request: SearchRequest): Promise<readonly ExtensionSearchResult[]>;
}

export interface ExtensionSearchOptions {
  /** Explicit default takes precedence over the configured default. */
  defaultExtensionId?: string;
  /** Per-extension timeout in milliseconds; invalid/non-positive values use the default. */
  timeoutMs?: number;
  signal?: AbortSignal;
}

export type ExtensionAttemptStatus = "matched" | "empty" | "timeout" | "error" | "cancelled";
export type ExtensionSearchStatus =
  | "matched"
  | "empty"
  | "failed"
  | "cancelled"
  | "invalid-query"
  | "unsupported-media"
  | "unavailable";

export interface ExtensionSearchAttempt {
  readonly extensionId: string;
  readonly status: ExtensionAttemptStatus;
}

export interface AttributedMediaResult extends ExtensionSearchResult {
  readonly title: string;
  readonly extensionId: string;
  readonly extensionName: string;
}

export interface ExtensionSearchResponse {
  readonly status: ExtensionSearchStatus;
  readonly items: readonly AttributedMediaResult[];
  readonly attempts: readonly ExtensionSearchAttempt[];
}

/** Validates and freezes a descriptor, rejecting invalid IDs, names, media types, and flags. */
export function validateDescriptor(descriptor: ExtensionDescriptorInput): ExtensionDescriptor;

/**
 * Deterministic default-first fallback host. Providers are queried sequentially; fallbacks run
 * only after empty/error/timeout responses, and cancellation halts the chain.
 */
export class ExtensionHost {
  constructor(extensions?: readonly MediaExtension[], defaults?: Partial<Record<MediaType, string>>);
  register(extension: MediaExtension): ExtensionDescriptor;
  list(options?: { mediaType?: MediaType }): readonly ExtensionDescriptor[];
  search(mediaType: MediaType | string, query: unknown, options?: ExtensionSearchOptions): Promise<ExtensionSearchResponse>;
}

export function parseYouTubeVideoUrl(input: string): string | null;
/** Returns an official privacy-enhanced YouTube embed URL, never a media file URL. */
export function youtubeEmbedUrl(input: string): string | null;
/** Builds a YouTube Data API v3 search URL; callers must supply an API key separately. */
export function youtubeSearchUrl(query: string): string | null;

export interface YouTubeSearchPayload {
  items?: readonly unknown[];
}

export interface YouTubeSearchResult {
  readonly id: string;
  readonly title: string;
  readonly channel: string;
  readonly thumbnail: string;
  readonly url: string;
}

export function normalizeYouTubeSearchPayload(payload: YouTubeSearchPayload | null | undefined): YouTubeSearchResult[];
