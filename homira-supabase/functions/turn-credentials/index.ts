import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

type IceServer = {
  urls: string[];
  username?: string;
  credential?: string;
};

type CloudflareIceServer = {
  urls?: string | string[];
  username?: string;
  credential?: string;
};

type CloudflareIceResponse = {
  iceServers?: CloudflareIceServer[];
};

const DEFAULT_STUN: IceServer = {
  urls: ["stun:stun.l.google.com:19302"],
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
  });
}

function getJsonEnv(name: string, fallbackKey: string): string | null {
  const raw = Deno.env.get(name);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    const value = parsed?.[fallbackKey];
    return typeof value === "string" && value.length > 0 ? value : null;
  } catch {
    return null;
  }
}

function parseTurnUrls(value: string | undefined): string[] {
  if (!value?.trim()) return [];

  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) {
      return parsed
        .filter((item): item is string => typeof item === "string")
        .map((item) => item.trim())
        .filter((item) => item.startsWith("turn:") || item.startsWith("turns:"));
    }
  } catch {
    // Fall through to comma-separated form.
  }

  return value
    .split(",")
    .map((item) => item.trim())
    .filter((item) => item.startsWith("turn:") || item.startsWith("turns:"));
}

function base64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

async function turnRestPassword(
  sharedSecret: string,
  username: string,
): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(sharedSecret),
    { name: "HMAC", hash: "SHA-1" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(username),
  );
  return base64(new Uint8Array(signature));
}

function normalizeCloudflareIceServers(
  value: CloudflareIceResponse,
): IceServer[] {
  return (value.iceServers ?? [])
    .map((server) => {
      const rawUrls = Array.isArray(server.urls)
        ? server.urls
        : typeof server.urls === "string"
          ? [server.urls]
          : [];

      // Browsers commonly block the alternate TURN/STUN port 53.
      const urls = rawUrls
        .map((url) => url.trim())
        .filter((url) => url.length > 0 && !url.includes(":53"));

      return {
        urls,
        username: server.username,
        credential: server.credential,
      } satisfies IceServer;
    })
    .filter((server) => server.urls.length > 0);
}

async function cloudflareIceServers(
  keyId: string,
  apiToken: string,
  ttlSeconds: number,
): Promise<IceServer[]> {
  const response = await fetch(
    `https://rtc.live.cloudflare.com/v1/turn/keys/${encodeURIComponent(keyId)}/credentials/generate-ice-servers`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ ttl: ttlSeconds }),
    },
  );

  if (!response.ok) {
    console.error(
      "Cloudflare TURN credential generation failed",
      response.status,
    );
    throw new Error("cloudflare_turn_unavailable");
  }

  const payload = await response.json() as CloudflareIceResponse;
  const servers = normalizeCloudflareIceServers(payload);

  if (!servers.some((server) =>
    server.urls.some((url) => url.startsWith("turn:") || url.startsWith("turns:"))
  )) {
    throw new Error("cloudflare_turn_missing_relay");
  }

  return servers;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json({ error: "method_not_allowed" }, 405);
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return json({ error: "missing_authorization" }, 401);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const publishableKey =
    Deno.env.get("SUPABASE_ANON_KEY") ??
    getJsonEnv("SUPABASE_PUBLISHABLE_KEYS", "default");

  if (!supabaseUrl || !publishableKey) {
    return json({ error: "supabase_environment_missing" }, 500);
  }

  const userClient = createClient(supabaseUrl, publishableKey, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false },
  });

  const {
    data: { user },
    error: userError,
  } = await userClient.auth.getUser();

  if (userError || !user) {
    return json({ error: "invalid_user" }, 401);
  }

  const requestedTtl = Number(Deno.env.get("HOMIRA_TURN_TTL_SECONDS") ?? "3600");
  const ttlSeconds = Number.isFinite(requestedTtl)
    ? Math.min(Math.max(Math.floor(requestedTtl), 300), 86400)
    : 3600;
  const expiresAtEpoch = Math.floor(Date.now() / 1000) + ttlSeconds;
  const expiresAt = new Date(expiresAtEpoch * 1000).toISOString();

  // Option 1: Homira-owned Coturn using TURN REST shared-secret auth.
  const privateUrls = parseTurnUrls(Deno.env.get("HOMIRA_TURN_URLS"));
  const privateSecret = Deno.env.get("HOMIRA_TURN_SHARED_SECRET")?.trim();

  if (privateUrls.length > 0 && privateSecret) {
    const username = `${expiresAtEpoch}:${user.id}`;
    const credential = await turnRestPassword(privateSecret, username);

    return json({
      configured: true,
      provider: "homira-coturn",
      expires_at: expiresAt,
      ice_servers: [
        DEFAULT_STUN,
        {
          urls: privateUrls,
          username,
          credential,
        },
      ],
    });
  }

  // Option 2: Cloudflare Realtime TURN managed service.
  const cloudflareKeyId =
    Deno.env.get("HOMIRA_CLOUDFLARE_TURN_KEY_ID")?.trim();
  const cloudflareApiToken =
    Deno.env.get("HOMIRA_CLOUDFLARE_TURN_API_TOKEN")?.trim();

  if (cloudflareKeyId && cloudflareApiToken) {
    try {
      const servers = await cloudflareIceServers(
        cloudflareKeyId,
        cloudflareApiToken,
        ttlSeconds,
      );

      return json({
        configured: true,
        provider: "cloudflare-realtime-turn",
        expires_at: expiresAt,
        ice_servers: servers,
      });
    } catch (error) {
      console.error("TURN provider error", error);
      return json({ error: "turn_provider_unavailable" }, 502);
    }
  }

  // Never advertise an unverified public TURN relay. Calls still retain STUN
  // direct-connect behavior until a real TURN provider is configured.
  return json({
    configured: false,
    provider: null,
    expires_at: null,
    ice_servers: [DEFAULT_STUN],
  });
});
