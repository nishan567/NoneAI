# Gemini proxy (production setup)

An APK is public: anything inside it (including a Gemini key) can be extracted. For any build you give to other
people, keep the Gemini key on a server and let the app talk to that server.

`worker.js` is a ~70-line Cloudflare Worker that does exactly that. It exposes the same URL shape as the Gemini API
(`/v1beta/models/<model>:generateContent`, `:streamGenerateContent`, `models` listing), so the app needs no special
protocol: it just swaps the base URL and sends `Authorization: Bearer <token>` instead of the Google key.

## Deploy
1. `npm i -g wrangler && wrangler init nonea-proxy` and use `worker.js` as the entry point.
2. `wrangler secret put GEMINI_API_KEY` (the real key) and `wrangler secret put APP_TOKENS` (comma-separated tokens).
3. Optional `ALLOWED_MODELS` var to restrict which models can be called.
4. `wrangler deploy`, then in the app choose **Secure proxy** and enter the Worker URL and a token.
   To make proxy mode the default in your own builds, add this to `~/.gradle/gradle.properties` (not the repo):
   ```
   nonea.proxyUrl=https://nonea-proxy.<you>.workers.dev
   nonea.allowDirectKey=false
   ```

## What the proxy does NOT solve by itself
A bearer token shipped to many users can still be extracted and abused, so for a public release also:
- add rate limiting (Cloudflare rate-limiting rules, or per-token counters in KV/Durable Objects);
- issue per-user or per-install tokens, or verify the app with Play Integrity / Firebase App Check before issuing them;
- set a spend budget and alerts on the Google Cloud project that owns the Gemini key, and rotate the key if it leaks;
- keep request bodies out of your logs.

Any other backend works as long as it forwards those paths to Google, adds the `x-goog-api-key` header server-side,
authenticates the app, and streams the response body through unbuffered.
