// Minimal Gemini proxy for Cloudflare Workers. The real Gemini key lives only here (as a secret);
// the Android app sends requests with a bearer token instead.
//
//   wrangler secret put GEMINI_API_KEY      # the real Google key (never goes in the app or in Git)
//   wrangler secret put APP_TOKENS          # comma-separated access tokens, e.g. "tok_abc,tok_def"
//   wrangler.toml: [vars] ALLOWED_MODELS = "gemini-3.8-flash,gemini-3.5-flash-lite"   (optional allowlist)
//
// In the app: Settings -> Gemini AI brain -> Secure proxy -> address = your worker URL, token = one of APP_TOKENS.

const UPSTREAM = "https://generativelanguage.googleapis.com";
const MAX_BODY_CHARS = 256 * 1024;
const POST_PATH = /^\/v1beta\/models\/([A-Za-z0-9._-]+):(generateContent|streamGenerateContent|countTokens)$/;
const GET_PATH = /^\/v1beta\/models(\/[A-Za-z0-9._-]+)?$/;
const FORWARDED_QUERY = ["alt", "pageSize", "pageToken"];   // never forward a client-supplied ?key=

// Error bodies use Google's shape so the app's error handling works unchanged.
const fail = (code, status, message) =>
  new Response(JSON.stringify({ error: { code, status, message } }), {
    status, headers: { "content-type": "application/json", "cache-control": "no-store" },
  });

function safeEqual(a, b) {                      // constant-time-ish comparison
  const n = Math.max(a.length, b.length);
  let diff = a.length ^ b.length;
  for (let i = 0; i < n; i++) diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  return diff === 0;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    const presented = (request.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "");
    const tokens = (env.APP_TOKENS || "").split(",").map((t) => t.trim()).filter(Boolean);
    if (!presented || !tokens.some((t) => safeEqual(t, presented))) {
      return fail(401, "UNAUTHENTICATED", "Invalid or missing access token.");
    }

    let body;
    if (request.method === "POST") {
      const m = POST_PATH.exec(url.pathname);
      if (!m) return fail(404, "NOT_FOUND", "Unsupported path.");
      const allow = (env.ALLOWED_MODELS || "").split(",").map((x) => x.trim()).filter(Boolean);
      if (allow.length && !allow.includes(m[1])) return fail(403, "PERMISSION_DENIED", "Model not allowed on this proxy.");
      body = await request.text();
      if (body.length > MAX_BODY_CHARS) return fail(413, "INVALID_ARGUMENT", "Request too large.");
    } else if (request.method === "GET") {
      if (!GET_PATH.test(url.pathname)) return fail(404, "NOT_FOUND", "Unsupported path.");
    } else {
      return fail(405, "INVALID_ARGUMENT", "Method not allowed.");
    }

    const query = new URLSearchParams();
    for (const k of FORWARDED_QUERY) if (url.searchParams.has(k)) query.set(k, url.searchParams.get(k));
    const qs = query.toString();

    const upstream = await fetch(`${UPSTREAM}${url.pathname}${qs ? "?" + qs : ""}`, {
      method: request.method,
      headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
      body,
    });

    // Stream the body straight through so SSE replies stay incremental.
    return new Response(upstream.body, {
      status: upstream.status,
      headers: {
        "content-type": upstream.headers.get("content-type") || "application/json",
        "cache-control": "no-store",
      },
    });
  },
};
