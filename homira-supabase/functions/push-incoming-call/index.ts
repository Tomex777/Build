import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

type PushBody = {
  call_id?: string;
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

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

function utf8(value: string): Uint8Array {
  return new TextEncoder().encode(value);
}

function pemToPkcs8(pem: string): Uint8Array {
  const normalized = pem.replaceAll("\\n", "\n").trim();
  const body = normalized
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const binary = atob(body);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

async function googleAccessToken(
  clientEmail: string,
  privateKeyPem: string,
): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(
    utf8(JSON.stringify({ alg: "RS256", typ: "JWT" })),
  );
  const payload = base64Url(
    utf8(
      JSON.stringify({
        iss: clientEmail,
        scope: "https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
      }),
    ),
  );
  const signingInput = `${header}.${payload}`;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToPkcs8(privateKeyPem),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign(
      { name: "RSASSA-PKCS1-v1_5" },
      key,
      utf8(signingInput),
    ),
  );
  const assertion = `${signingInput}.${base64Url(signature)}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });

  const data = await response.json();
  if (!response.ok || typeof data?.access_token !== "string") {
    throw new Error("Could not obtain Firebase access token");
  }
  return data.access_token;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json({ error: "method_not_allowed" }, 405);
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return json({ error: "missing_authorization" }, 401);
  }

  let body: PushBody;
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  const callId = body.call_id?.trim();
  if (!callId) {
    return json({ error: "call_id_required" }, 400);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const publishableKey =
    Deno.env.get("SUPABASE_ANON_KEY") ??
    getJsonEnv("SUPABASE_PUBLISHABLE_KEYS", "default");
  const secretKey =
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ??
    getJsonEnv("SUPABASE_SECRET_KEYS", "default");

  if (!supabaseUrl || !publishableKey || !secretKey) {
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

  const admin = createClient(supabaseUrl, secretKey, {
    auth: { persistSession: false },
  });

  const { data: call, error: callError } = await admin
    .from("call_sessions")
    .select("id,caller_id,callee_id,media_type,state,expires_at")
    .eq("id", callId)
    .maybeSingle();

  if (callError || !call) {
    return json({ error: "call_not_found" }, 404);
  }

  if (call.caller_id !== user.id) {
    return json({ error: "not_call_owner" }, 403);
  }

  if (
    call.state !== "ringing" ||
    new Date(call.expires_at).getTime() <= Date.now()
  ) {
    return json({ error: "call_not_ringable" }, 409);
  }

  const { data: callerProfile } = await admin
    .from("profiles")
    .select("display_name,username,phone_e164")
    .eq("id", user.id)
    .maybeSingle();

  const callerName =
    callerProfile?.display_name?.trim() ||
    callerProfile?.username?.trim() ||
    callerProfile?.phone_e164?.trim() ||
    "Homira caller";

  const { data: tokens, error: tokenError } = await admin
    .from("device_push_tokens")
    .select("device_id,platform,token")
    .eq("user_id", call.callee_id)
    .eq("platform", "android");

  if (tokenError) {
    return json({ error: "token_lookup_failed" }, 500);
  }

  if (!tokens?.length) {
    return json({
      configured: true,
      delivered: 0,
      reason: "no_android_tokens",
    });
  }

  const projectId = Deno.env.get("FIREBASE_PROJECT_ID");
  const clientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL");
  const privateKey = Deno.env.get("FIREBASE_PRIVATE_KEY");

  if (!projectId || !clientEmail || !privateKey) {
    return json({
      configured: false,
      delivered: 0,
      reason: "firebase_credentials_missing",
    });
  }

  let accessToken: string;
  try {
    accessToken = await googleAccessToken(clientEmail, privateKey);
  } catch {
    return json({
      configured: true,
      delivered: 0,
      error: "firebase_auth_failed",
    }, 502);
  }

  let delivered = 0;
  let failed = 0;

  for (const row of tokens) {
    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: row.token,
            data: {
              type: "incoming_call",
              call_id: call.id,
              caller_id: call.caller_id,
              caller_name: callerName,
              media_type: call.media_type,
              expires_at: call.expires_at,
            },
            android: {
              priority: "high",
              ttl: "45s",
            },
          },
        }),
      },
    );

    if (response.ok) {
      delivered += 1;
      continue;
    }

    failed += 1;
    const errorText = await response.text();

    if (
      errorText.includes("UNREGISTERED") ||
      errorText.includes("registration-token-not-registered")
    ) {
      await admin
        .from("device_push_tokens")
        .delete()
        .eq("token", row.token);
    }
  }

  return json({
    configured: true,
    delivered,
    failed,
  });
});
