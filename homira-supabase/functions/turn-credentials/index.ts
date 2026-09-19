import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

type IceServer = {
  urls: string[];
  username?: string;
  credential?: string;
};

const DEVELOPMENT_TURN_URLS = [
  "turn:staticauth.openrelay.metered.ca:80",
  "turn:staticauth.openrelay.metered.ca:80?transport=tcp",
  "turns:staticauth.openrelay.metered.ca:443?transport=tcp",
];
const DEVELOPMENT_TURN_SHARED_SECRET = "openrelayprojectsecret";

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

  const privateUrls = parseTurnUrls(Deno.env.get("HOMIRA_TURN_URLS"));
  const privateSecret = Deno.env.get("HOMIRA_TURN_SHARED_SECRET")?.trim();
  const hasPrivateTurn = privateUrls.length > 0 && !!privateSecret;

  const turnUrls = hasPrivateTurn ? privateUrls : DEVELOPMENT_TURN_URLS;
  const sharedSecret = hasPrivateTurn
    ? privateSecret!
    : DEVELOPMENT_TURN_SHARED_SECRET;

  const requestedTtl = Number(Deno.env.get("HOMIRA_TURN_TTL_SECONDS") ?? "3600");
  const ttlSeconds = Number.isFinite(requestedTtl)
    ? Math.min(Math.max(Math.floor(requestedTtl), 300), 86400)
    : 3600;

  const expiresAtEpoch = Math.floor(Date.now() / 1000) + ttlSeconds;
  const username = `${expiresAtEpoch}:${user.id}`;
  const credential = await turnRestPassword(sharedSecret, username);

  const iceServers: IceServer[] = [
    { urls: ["stun:stun.l.google.com:19302"] },
    {
      urls: turnUrls,
      username,
      credential,
    },
  ];

  return json({
    configured: true,
    provider: hasPrivateTurn ? "homira-coturn" : "openrelay-development",
    expires_at: new Date(expiresAtEpoch * 1000).toISOString(),
    ice_servers: iceServers,
  });
});
