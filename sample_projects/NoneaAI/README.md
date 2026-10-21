# Nonea AI — Android assistant (Kotlin + Compose + Gemini)

Bengali / Banglish / English chat + voice, Gemini brain, phone control via official Android APIs.

## Gemini AI brain (spec section 4)
- `ai/AiTypes.kt` defines the provider-neutral `AiProvider` interface (generate / stream / testConnection / listModels) and typed `AiError`s.
  `ai/GeminiProvider.kt` implements it for Gemini (SSE streaming, retries with backoff for 429/5xx, error mapping for rate limits, network, invalid key, unavailable model, blocked, empty/truncated replies).
  To add another provider: implement `AiProvider`, register an `AiProviderFactory` in `ai/AiService.kt`.
- `ai/AiService.kt` is the one entry point the app uses: picks provider from Settings, trims history to a token budget (`ai/ContextManager.kt`, calibrated against Gemini's real `usageMetadata`), streams, tracks usage and connection status.
- Settings → Gemini AI brain: connection type (own key / secure proxy), model chips + custom ID + "refresh from Google", temperature, top-p, top-k, max reply length, thinking level (Gemini 3), history token budget, streaming toggle, **Test connection**, status dot (also in the chat top bar), and usage counters (counted on this phone only).
- Models: Google retires models often. The default is `gemini-3.8-flash` (checked 2026-10-21); `gemini-2.5-*` is restricted to existing users, so a saved old default is migrated once. Use "Refresh model list" if a model disappears.

### Secrets & security
- No key in source or in the APK. A build step (`checkNoSecrets`) fails the build if a Google-key-shaped string appears under `src/`; `.gitignore` excludes `local.properties`, keystores and `.env` files.
- A key typed in Settings is encrypted with an Android Keystore AES-GCM key (`data/SecureStore.kt`), kept out of backups, and the old plain-text DataStore copy is migrated and deleted. The key is sent only in the `x-goog-api-key` header, never in a URL.
- Direct-key mode is for **personal use**: Settings shows a risk warning and requires an acknowledgement before a key can be entered.
- For distributed builds use a **secure proxy** that holds the key server-side: see `docs/proxy/` (sample Cloudflare Worker). Build flags in `~/.gradle/gradle.properties`: `nonea.proxyUrl=https://…` (default to proxy mode) and `nonea.allowDirectKey=false` (hide direct-key entry).
- Keys and tokens are redacted from error text, logs (`SafeLog`) and uncaught-exception reports (`CrashRedactor`, installed in `NoneaApp`). Debug logging never includes request bodies.
- Failed requests are shown in the error banner instead of being saved as chat messages, so errors never end up in the next prompt.

## What works
- Chat UI (Compose), history in Room, settings in DataStore (API key, model, voice language, spoken replies).
- Voice in (SpeechRecognizer) and voice out (TextToSpeech): see *Voice assistant* below.
- Coding help: Gemini replies with fenced code, rendered in monospace blocks.
- Phone actions, two paths: offline rules (`LocalRouter`) for common commands, and `[[ACTION:..]]` tags that Gemini emits.
  Open app, call (contacts lookup), SMS draft, flashlight, alarm, timer, reminder (WorkManager), volume, web search,
  maps, Wi-Fi/Bluetooth panels, Settings pages, YouTube links and search, media keys, app launcher, https links, Play Store search (all plain Android intents),
  plus optional screen automation through an AccessibilityService (see *Screen automation* below).
- Not possible with public APIs: toggling Wi-Fi/Bluetooth silently, sending SMS without user tap, freely controlling other apps.

## Screen automation (spec section 6)
Two layers, deliberately separate:

**1. Intents and deep links (no special permission).** `Actions.kt`: `open_app` (known package first, then label match; if the app isn't installed Nonea says so and offers `play_store|<name>`, which only opens the store search), `settings|<page>` (Wi-Fi, Bluetooth, display, sound, battery, location, accessibility, apps, Nonea's own app info, or main Settings), `youtube_search|<query>` (opens the results page through an `https://www.youtube.com/results` link; it never auto-plays, because the first result can be an ad), `open_url|<https link>` (only plain http/https), `play_store|<name>`.

**2. Optional AccessibilityService (`actions/NoneaAccessibilityService.kt`, `actions/AutomationExecutor.kt`).** Needs *both* the user turning Nonea on in Android Settings > Accessibility *and* the in-app master switch (off by default). Actions: `system|back/home/recents/notifications/quick_settings/screenshot/lock`, `scroll|down/up/left/right` (uses the screen's own scroll action first, a swipe gesture second), `tap_text|<label>` (finds a visible element by its text/description and clicks it, falling back to a gesture on its centre), `tap|` or `tap|x%,y%` (position tap; always asks first), `type|<text>` (appends to the focused field).
- Voice/typed rules in `LocalRouter` are anchored so ordinary questions ("how do I scroll in Excel?", "type of cars?") still go to Gemini. `type` needs quotes: `type "hello"`.
- Acting on another app: Nonea is in front when you talk to her, so before a screen action she brings the app you were last in back to the front (allowed because Nonea is visible), then acts. If she doesn't know one yet: "Open YouTube first".

**Permission status screen** (`ui/AutomationUi.kt`, Settings > Automation & Accessibility status): service enabled in Android, service running, master switch, gestures / read-screen capabilities as Android reports them, screenshot support (Android 9+), last app Nonea can act on, a prominent disclosure with a required tick box, step-by-step instructions to enable Accessibility manually (including "Allow restricted settings" for sideloaded installs on Android 13+), an Emergency stop button and "Turn off Accessibility access".

**Safety model** (`automation/AutomationPolicy.kt`, unit-tested; `automation/AutomationGuard.kt`):
- Master switch off by default, kept out of backups, switched off by Emergency stop and when the service is unbound.
- **Emergency stop / cancel**: red Stop on the task pill; Emergency stop in Phone tools and the status screen; a floating red *Stop Nonea* button drawn over other apps (accessibility overlay window, no extra permission) while she works and for a few seconds after; typing or saying "stop" / "cancel" / "থামো" works even mid-action. Stopping bumps an epoch so every step in flight throws, denies any open question and turns the master switch off.
- **Confirmation** (tap only; voice can't approve; 30 s timeout = no): coordinate taps, labels that look sensitive (pay, buy, order, send, delete, install, allow, sign in, confirm, subscribe, Bengali equivalents ...), several matches for a label, anything in the Play Store, "Skip ad" style buttons, and *every* action if the user turns on "Ask before every automated action". The dialog shows in the app, or as an overlay panel over the other app. After the user says yes the screen is re-checked; if it changed, nothing is tapped.
- **Refused outright**: acting inside Nonea itself (so she can never press her own Allow button), Android Settings / permission controller / package installer / system UI, banking, wallet and payment apps, password managers and authenticators, password fields, anything that looks like an advertisement (text, description or view-id signals on the element or its ancestors), unknown foreground app, and more than 5 screen steps in one reply or 20 per minute.
- **Privacy**: screen content is used on the phone only to locate a button. Result messages never contain text read from the screen, because chat results are saved and sent to Gemini as history. The persona tells the model it cannot see the screen.
- Never bypasses lock screens, biometrics, permission dialogs or FLAG_SECURE windows (Android hides those from accessibility services anyway). When an action can't be done, the reply says why and suggests doing it yourself or another way.

**Honest limits**
- This is not a general phone-control agent. Some apps (games, Flutter/canvas UIs, banking, anything with `FLAG_SECURE` or that blocks accessibility) don't expose their controls; "tap_text" will say it couldn't find the label, and you can still use position taps with confirmation.
- Android may stop the service (battery optimisation on some brands). Re-enable it in Accessibility settings; the status screen shows when it isn't running.
- Google Play restricts non-accessibility uses of the Accessibility API and requires a declaration and prominent disclosure. This build is meant for personal/sideloaded use; a Play release needs that review first.
- A phone you hand to someone else: anyone who unlocks it could switch automation on. Emergency stop and "Turn off Accessibility access" are always one tap away.

**Test checklist (on a device)** 1) Status screen shows Off for everything on a fresh install. 2) Enable in Android Settings, return: running + capabilities show. 3) "Open YouTube", back to Nonea, "scroll down": she switches to YouTube and scrolls. 4) "tap search" works; "tap buy"/"tap install" ask first; a tap on an ad-labelled element is refused. 5) "tap the screen" asks first. 6) While a step waits for yes, say "stop": denied and switched off. 7) Emergency stop from the floating button over another app. 8) Settings app: "tap wifi" is refused, "open wifi settings" works. 9) Turn the service off in Android Settings: status updates on return.

### Status of this change
Written without an Android SDK, so it has **not been compiled or run**. Run `./gradlew testDebugUnitTest` first (`AutomationPolicyTest`, `LocalRouterAutomationTest`, and for section 7 `LocalRouterAppsTest`, `AppControlTest`), fix any compile slips, then walk through the checklist above on a real phone.

## App & YouTube control (spec section 7)
Everything here uses public Android interfaces and **needs no special permission** (no Accessibility, no notification access). Code: `apps/` (pure Kotlin logic, unit-tested), `apps/AppLauncher.kt` (Android calls), `ui/AppLauncherUi.kt`.

| You say / do | What happens | Mechanism |
|---|---|---|
| "Open YouTube" / "ইউটিউব খোলো" / "open yt" | Opens the YouTube app | package launch intent (`com.google.android.youtube`) |
| "Open https://youtu.be/…" / "play <link>" | Opens **that** video, Short or playlist (start time kept) | the link is parsed, the id validated, and a canonical `https://www.youtube.com/…` link is rebuilt and sent to the YouTube app (browser if the app is missing). `music.youtube.com`, channels and anything unparseable are refused |
| "search lofi on youtube", "play lofi on youtube", "open a video about …" | Opens the YouTube **results page** | `https://www.youtube.com/results?search_query=…` |
| "pause", "resume", "next song", "previous track", "stop the music", "গান থামাও" | Presses the matching media key | `AudioManager.dispatchMediaKeyEvent` (the same key headphones send) |
| "show my apps" / ⊞ Apps | App launcher: icon grid, search, tap to open, press and hold to name an app | `PackageManager` + LAUNCHER `<queries>` |
| "open <name>" | Opens an installed app by your alias, package, built-in alias or label | `AppNames.resolve` |
| "open bluetooth settings", "open date and time settings", "show chrome app info" | Settings / system screens | standard `Settings.ACTION_*` intents |

**Honest limits**
- **A search can't start a specific video.** YouTube/Android give apps no way to "play the top result", and the first result is often an ad. With plain words Nonea opens the results and tells you to tap one; with a **link** she opens exactly that video. (If you've turned on screen automation you can still say "tap <title>", which has its own confirmations and ad refusal.)
- **Media keys go to whichever app last played media.** Nonea can't choose the player, see what is playing, or seek. After sending the key she checks `AudioManager.isMusicActive` and says "Paused" / "Playing" only if that changed; otherwise she says it didn't work and why. Next/Previous can't be confirmed, and she says so.
- Apps that are disabled, have no launcher screen, or are hidden from other apps by the phone maker can't be opened. Work-profile duplicates show once.
- No unrestricted control of YouTube or any other app: tapping, scrolling and typing inside other apps remain the separate, opt-in Accessibility layer (section 6) with all its confirmations.

**Apps that aren't installed**
- A name that matches nothing: "I couldn't find an app called X … say “search Play Store for X”" (this only opens the store search; installing stays your tap). The launcher shows the same button when its search is empty.
- A well-known app that is missing is **not** swapped for a look-alike ("YouTube" never opens "YouTube Music"). YouTube itself falls back to youtube.com in the browser, for links, searches and "open YouTube".
- Several equally good matches ("ca" -> Calculator, Calendar, Camera): she lists them and asks for the full name instead of guessing.
- An alias that points at an app you've since uninstalled is shown as "(not installed)" in Settings and answered like a missing app.

**Aliases and custom voice commands** (Settings → *Apps & YouTube commands*; stored in their own DataStore file `nonea_apps`)
- *App aliases*: press and hold an app in the launcher and give it a name ("fb", "work chat"). Stored by package name, so they survive renames. Your names win over the built-in ones (English, Bengali and Banglish names for YouTube, Facebook, WhatsApp, Camera, Calculator, Clock, Maps, Gmail, Spotify, Netflix, Play Store …, in `AppNames.builtinAliases`).
- *Voice commands*: "movie night" -> open Netflix, "my tunes" -> a YouTube search, "main settings" -> Settings, and so on. Exact phrase match (case, punctuation and "hey nonea," / "please" ignored). Allowed actions: open app, Settings page, YouTube search, YouTube link, media key, plain https link, show app list. **Never** calls, SMS, taps, typing or anything that acts inside another app, and "stop" / "cancel" cannot be redefined (they are checked first). Rows that don't validate are dropped when loaded, so a damaged store can't smuggle in a risky action. Limits: 50 commands, 100 aliases.
- Gemini can use the same actions (`youtube_play|…`, `media|…`, `appinfo|…`, `show_apps|`, `open_app|<name or alias>`); the persona tells it the limits above so it doesn't promise a video will play.

**Test checklist (on a device)**
1. Open YouTube / ইউটিউব খোলো / open yt: the app opens. 2. Paste a `youtu.be` link after "open": that video opens; "open https://music.youtube.com/…" is refused ("couldn't read a video"). 3. "search lofi on youtube": results page, no autoplay. 4. Start music in any player, then "pause", "resume", "next song": confirmations match what you hear; with nothing playing "resume" says nothing started. 5. Apps sheet: icons load, search filters, tap opens, long-press adds an alias, then "open <alias>" works and ✕ in Settings removes it. 6. Disable or uninstall YouTube: "open YouTube" and a link both fall back to the browser with an honest message. 7. "open Nonexistent": not-found message + Play Store button. 8. "open ca": lists matches. 9. Add voice command "movie night" -> Open app -> Netflix; try to add phrase "stop" (refused) and a `javascript:` link (refused). 10. "open bluetooth settings", "open date and time settings", "show whatsapp app info". 11. Airplane mode / TalkBack: everything above except the browser fallback works offline.

### Status of this change
Written without an Android SDK or Kotlin compiler, so it has **not been compiled or run on a device**. The pure-Kotlin rules were cross-checked by running their regular expressions outside Kotlin, but run `./gradlew testDebugUnitTest` first (`AppControlTest`, `LocalRouterAppsTest`), fix any compile slips, then walk through the checklist above on a phone. Compose APIs used: `combinedClickable` (opted in), `LazyVerticalGrid`, `OutlinedTextField.supportingText` (BOM 2024.06).

## Personality, memory & voice (spec section 2)
- Persona prompt is built per message (`ai/Persona.kt`): warm female best-friend tone, honest about being an AI (no human/emotion claims), replies in Bengali / Banglish / English per Settings ("Match me" mirrors the user).
- Memory: only user-approved facts are stored (Room `memories`, DB v2 with migration). Say "mone rakho: …" / "remember: …", or approve Nonea's "Remember this?" card. View/delete in Settings.
- Suggestions: last 30 messages + approved memories go to Gemini; she offers at most one light suggestion when it fits. Tap 💡 to ask for up to 3.
- Voice out: Settings → speaking language, rate, pitch, per-language voice picker, preview buttons. Android doesn't expose voice gender, so choose the voice that sounds right.

## Interface (futuristic UI)
- `ui/Orb.kt`: animated orb (breathing core, rotating ring, mic-level ripples + radial waveform while listening, orbiting arcs while thinking, voice pulse while speaking). Respects the system "remove animations" setting.
- Live status: orb label + Listening / Thinking / Speaking pills + a Task pill for phone actions (running, then result).
- Chat: history drawer (permanent sidebar on tablets), New chat, per-chat delete. Old messages are migrated into one "Earlier chat" (DB v3).
- Quick actions: YouTube, Apps picker, Code (prefills the input), Settings, Phone tools (torch, volume, Wi-Fi/Bluetooth panels, screenshot, Back/Home/Recents/Notifications).
- Dark / Light / System theme (☀/☾ in the top bar or Settings). Interface language: Auto / English / বাংলা (`res/values`, `res/values-bn`; switches instantly, no restart).
- Responsive: phone portrait (orb above chat), short window / keyboard open (mini orb in the top bar), landscape (orb left, chat right), tablet >= 840dp (history sidebar, width-capped content).

## Voice assistant (spec section 5)
Everything below works **only while Nonea is on screen**. Leaving the app (or locking the screen) stops listening, speaking, hands-free and the wake phrase. There is no background service and no foreground-service microphone use.

- **Talk button** (Settings → Voice assistant): *Tap to talk* (tap = start, tap again = stop and send, tap while she speaks = interrupt and listen) or *Hold to talk* (hold = listen, release = send; a pause while holding doesn't end the turn, pieces are joined). With TalkBack on, hold mode falls back to double-tap = toggle. A very short press shows a "hold while you speak" hint.
- **Hands-free conversation** (optional): tap the mic once and she listens again after every reply. Ends on the *End hands-free* button, after N quiet turns (1–5, configurable), on any error, or when you leave the app. Hold-to-talk always sends one message.
- **Speech-to-text** (`voice/Voice.kt → SpeechManager`): bn-BD, en-US or **Auto (best effort)**. Engine choice: online if available / prefer offline / offline only (on-device recogniser, Android 12+). Offline needs the language pack from your phone's speech settings.
- **Spoken replies** (`TtsManager`): replies are cut into per-language segments and spoken one after another; code blocks, links, markdown and emoji are not read out; audio focus ducks other audio; long replies are chunked below the engine's input limit.
- **Controls**: *Stop speaking*, *Stop listening* (discards the turn and ends hands-free) and *End hands-free* appear whenever there is something to stop. Each assistant message has a 🔊 replay button.
- **History**: voice and typed messages share the same chats; spoken user messages carry a 🎤 badge (DB v4 adds `messages.voice`).
- **Errors** are typed (`VoiceError`) and shown, localized (English/বাংলা), with the action that fixes them: *Allow* (mic not granted yet), *Open settings* (denied for good), *TTS settings* (no engine / voice missing). Typing always keeps working.

### Language selection: what "auto" can and can't do
- **Speaking** (TTS): reliable. Each sentence is detected by script: Bengali script → Bengali voice, Latin → English voice. **Banglish (Bengali in Latin letters) can't be told from English and is read with the English voice.**
- **Listening** (STT): Android cannot reliably detect the language *before* you speak. Auto starts with the last language she recognised (first turn: Bengali unless the app language is English), switches after a turn that came back in the other script, retries once in the other language when nothing matched, and on Android 13+ also asks the engine to detect among bn/en (only some engines honour that). Pick a fixed language if auto guesses wrong for you.

### Wake phrase — experimental, and NOT a real wake-word engine
No acoustic wake-word engine (Porcupine, openWakeWord, …) is bundled, so Nonea **does not** support true always-listening wake words. What exists is an opt-in, off-by-default *in-app wake phrase*: while the app is open and idle it keeps the phone's speech recogniser running and looks for your phrase in the transcript (`WakePhrase.match`, tolerant of a one-letter slip). Costs and caveats, also shown in Settings (and you must tick "I understand"): more battery, your phone may beep at every recogniser restart, the speech service may send audio to Google while it listens, it can miss words in the gap between restarts, and it stops when the app leaves the screen. It turns itself off after repeated errors. A real engine would need a foreground service with a microphone notification; that is deliberately not built.

### Status of this change
Written without an Android SDK or Kotlin compiler available, so **it has not been compiled or run on a device.** Before relying on it:
1. `./gradlew testDebugUnitTest` (JVM tests for language detection, speech text prep, wake-phrase matching).
2. On a phone, check: mic permission allow / deny / deny-for-good → Open settings; tap, hold (with a pause), hands-free (speak, stay silent twice), stop buttons, replay; airplane mode with each speech-engine option; Bengali and English replies with and without the Bengali voice installed; wake phrase on/off and backgrounding the app.

## Pinned versions
JDK 17 · Gradle 8.7 · AGP 8.5.2 · Kotlin 1.9.24 · KSP 1.9.24-1.0.20 · Compose BOM 2024.06.00 (compiler 1.5.14)
Room 2.6.1 · DataStore 1.1.1 · WorkManager 2.9.0 · OkHttp 4.12.0 · Coroutines 1.8.1
SDK: platforms;android-34, build-tools;34.0.0, platform-tools. minSdk 26.

## Build in Termux
```
pkg update && pkg upgrade -y
pkg install -y openjdk-17 gradle aapt2 git wget unzip

# Android SDK: install an arm64 build of build-tools + platform-tools (e.g. the community
# "android-sdk-tools" release for Termux), then the android-34 platform (android.jar) into:
export ANDROID_HOME=$HOME/android-sdk      # needs platforms/android-34, build-tools/34.0.0
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Use Termux's native aapt2 (Google's x86 aapt2 from Maven can't run on Android):
echo "android.aapt2FromMavenOverride=$PREFIX/bin/aapt2" >> ~/.gradle/gradle.properties

gradle wrapper --gradle-version 8.7      # generates gradlew
./gradlew assembleDebug --no-daemon
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/Nonea.apk
```
Then open the APK from Downloads and allow "install unknown apps".
Low RAM? Keep `org.gradle.jvmargs=-Xmx1536m` (gradle.properties) and close other apps.

## First run
Settings (⚙) → Gemini AI brain → tick “I understand the risks”, paste your Gemini API key (free at aistudio.google.com) → **Test connection** → Save → grant permissions → tap 🎤.
