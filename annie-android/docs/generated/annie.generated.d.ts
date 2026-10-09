// GENERATED from the Annie operation registry. Do not edit by hand.
// Regenerate: ANNIE_UPDATE_GENERATED=1 ./gradlew testDebugUnitTest --tests '*OperationGeneratedArtifactsTest*'

type AnnieErrorCode =
  | "NOT_A_PACKAGE"
  | "NOT_DECLARED"
  | "NOT_GRANTED"
  | "UNAVAILABLE"
  | "INVALID_ARGUMENT"
  | "FOREGROUND_REQUIRED"
  | "RATE_LIMITED"
  | "RESOURCE_LIMIT"
  | "NETWORK_ERROR"
  | "NOT_FOUND"
  | "UNSUPPORTED"
  | "TIMEOUT"
  | "CANCELLED"
  | "HOST_NOT_ALLOWED"
  | "INTERNAL";

interface AnnieError extends Error {
  code: AnnieErrorCode;
  operation: string;
  retryable: boolean;
  retryAfterMs?: number;
  permission?: string;
}

interface AnnieMessage {
  type: string;
  [key: string]: unknown;
}

interface AnnieMessageHandle {
  id: string;
  packageId: string;
  conversationId: string;
  createdAt: number;
}

interface Annie {
  android: {
    /**
     * Registry operation android.device.info.
     * @throws AnnieError CANCELLED, FOREGROUND_REQUIRED, INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT, UNAVAILABLE, UNSUPPORTED
     */
    deviceInfo(): Promise<{ platform: string; apiLevel: number; locale: string }>;
    tts: {
      /**
       * Registry operation android.tts.speak.
       * @throws AnnieError CANCELLED, FOREGROUND_REQUIRED, INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT, UNAVAILABLE, UNSUPPORTED
       */
      speak(text: string, options?: { language?: string; queue?: "add" | "flush" }): Promise<Record<string, unknown>>;
      /**
       * Registry operation android.tts.status.
       * @throws AnnieError CANCELLED, FOREGROUND_REQUIRED, INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT, UNAVAILABLE, UNSUPPORTED
       */
      status(utteranceId: string): Promise<Record<string, unknown>>;
      /**
       * Registry operation android.tts.stop.
       * @throws AnnieError CANCELLED, FOREGROUND_REQUIRED, INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT, UNAVAILABLE, UNSUPPORTED
       */
      stop(): Promise<{ status: string; stopped: boolean; cancelledUtterances: number }>;
    };
    ocr: {
      /**
       * Registry operation android.ocr.asset.
       * @throws AnnieError CANCELLED, FOREGROUND_REQUIRED, INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT, UNAVAILABLE, UNSUPPORTED
       */
      asset(assetId: string): Promise<Record<string, unknown>>;
    };
    stt: {
      /**
       * Registry operation android.stt.listen.
       * @throws AnnieError CANCELLED, FOREGROUND_REQUIRED, INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT, UNAVAILABLE, UNSUPPORTED
       */
      listen(options?: { language?: string; prompt?: string }): Promise<Record<string, unknown>>;
    };
    documents: {
      /**
       * Registry operation android.documents.pickText.
       * @throws AnnieError CANCELLED, FOREGROUND_REQUIRED, INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT, UNAVAILABLE, UNSUPPORTED
       */
      pickText(options?: { mimeType?: string }): Promise<Record<string, unknown>>;
    };
    media: {
      /**
       * Registry operation android.media.inspectAsset.
       * @throws AnnieError CANCELLED, FOREGROUND_REQUIRED, INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT, UNAVAILABLE, UNSUPPORTED
       */
      inspectAsset(assetId: string): Promise<Record<string, unknown>>;
    };
    notifications: {
      /**
       * Registry operation android.notifications.post.
       * @throws AnnieError CANCELLED, FOREGROUND_REQUIRED, INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT, UNAVAILABLE, UNSUPPORTED
       */
      post(value: { key?: string; title: string; text: string }): Promise<Record<string, unknown>>;
      /**
       * Registry operation android.notifications.update.
       * @throws AnnieError CANCELLED, FOREGROUND_REQUIRED, INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT, UNAVAILABLE, UNSUPPORTED
       */
      update(value: { key: string; title: string; text: string }): Promise<Record<string, unknown>>;
      /**
       * Registry operation android.notifications.cancel.
       * @throws AnnieError CANCELLED, FOREGROUND_REQUIRED, INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT, UNAVAILABLE, UNSUPPORTED
       */
      cancel(key: string): Promise<Record<string, unknown>>;
    };
  };
  downloads: {
    /**
     * Registry operation downloads.start.
     * @throws AnnieError HOST_NOT_ALLOWED, INTERNAL, INVALID_ARGUMENT, NETWORK_ERROR, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT
     */
    start(spec: { url: string; title?: string; browserSession?: string; headers?: Record<string, string>; completionAction?: { action: string; payload?: unknown } }): Promise<{ id: string }>;
    /**
     * Registry operation downloads.status.
     * @throws AnnieError HOST_NOT_ALLOWED, INTERNAL, INVALID_ARGUMENT, NETWORK_ERROR, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT
     */
    status(id: string): Promise<{ id: string; state: string; progress: number; bytes: number; total: number | null; error: string | null }>;
    /**
     * Registry operation downloads.list.
     * @throws AnnieError HOST_NOT_ALLOWED, INTERNAL, INVALID_ARGUMENT, NETWORK_ERROR, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT
     */
    list(): Promise<Array<{ id: string; state: string; progress: number; bytes: number; total: number | null; error: string | null }>>;
    /**
     * Registry operation downloads.cancel.
     * @throws AnnieError HOST_NOT_ALLOWED, INTERNAL, INVALID_ARGUMENT, NETWORK_ERROR, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT
     */
    cancel(id: string): Promise<Record<string, unknown>>;
    /**
     * Registry operation downloads.pause.
     * @throws AnnieError HOST_NOT_ALLOWED, INTERNAL, INVALID_ARGUMENT, NETWORK_ERROR, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT
     */
    pause(id: string): Promise<Record<string, unknown>>;
    /**
     * Registry operation downloads.resume.
     * @throws AnnieError HOST_NOT_ALLOWED, INTERNAL, INVALID_ARGUMENT, NETWORK_ERROR, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT
     */
    resume(id: string): Promise<Record<string, unknown>>;
  };
  messages: {
    /**
     * Registry operation messages.send.
     * @throws AnnieError INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT
     */
    send(message: AnnieMessage): Promise<AnnieMessageHandle>;
    /**
     * Registry operation messages.update.
     * @throws AnnieError INTERNAL, INVALID_ARGUMENT, NOT_A_PACKAGE, NOT_DECLARED, NOT_FOUND, NOT_GRANTED, RATE_LIMITED, RESOURCE_LIMIT
     */
    update(id: string, message: AnnieMessage): Promise<AnnieMessageHandle>;
  };
}
