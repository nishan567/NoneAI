# Permissions, Compatibility & Troubleshooting Guide

This document describes Android permission requirements, background execution policies, compatibility limitations, and step-by-step troubleshooting for Nonea AI.

---

## 1. Declared Permissions & Features

| Permission | Category | Purpose | Behavior if Denied |
|---|---|---|---|
| `android.permission.INTERNET` | Normal | Connects to Gemini API (direct key or secure backend proxy). | App functions in offline mode only (`LocalRouter` intent commands). |
| `android.permission.RECORD_AUDIO` | Dangerous (Runtime) | Allows user speech recognition via Android `SpeechRecognizer`. | Voice input is disabled; text chat input remains fully functional. |
| `android.permission.POST_NOTIFICATIONS` | Dangerous (Android 13+) | Used by `ReminderWorker` for reminder alerts and background action notifications. | Reminders will not post visual notifications to the system shade. |
| `com.android.alarm.permission.SET_ALARM` | Normal | Sets device system clock alarms and timers. | Alarms cannot be registered directly in Clock app. |
| `BIND_ACCESSIBILITY_SERVICE` | System Bound | Allows optional `NoneaAccessibilityService` to perform user-authorized UI navigation & taps. | Screen automation is unavailable; intent-based actions still function normally. |

---

## 2. Package Visibility (`<queries>`)

Android 11+ (API 30+) restricts package visibility. Nonea AI includes `<queries>` in `AndroidManifest.xml`:
- `com.google.android.youtube`: Checks if YouTube app is installed to open deep links.
- `android.intent.action.MAIN` with `category.LAUNCHER`: Discovers installed apps for the in-app launcher grid.
- `android.speech.RecognitionService`: Discovers system speech engines.
- `android.intent.action.TTS_SERVICE`: Discovers Text-to-Speech engines.
- `android.intent.action.VIEW` for `https` & `market`: Resolves browser and Play Store intent handlers.

---

## 3. Compatibility & Minimum Supported Version

- **Target SDK**: Android 14 (API 34)
- **Compile SDK**: Android 14 (API 34)
- **Minimum SDK**: Android 8.0 (API 26) - Oreo
- **Supported Architectures**: `arm64-v8a`, `armeabi-v7a`, `x86_64`

### Compatibility Limitations:
1. **Android 13+ (API 33+) Sideloaded Restriction**:
   - Android blocks Accessibility Services for sideloaded APKs by default.
   - **Fix**: Go to **Android Settings > Apps > Nonea AI > tap three dots (top-right) > Allow restricted settings**.
2. **Android 9+ (API 28+) Screenshots**:
   - Accessibility `takeScreenshot()` requires API 28 or newer. On older versions, screenshots are disabled.
3. **FLAG_SECURE & Banking Apps**:
   - Any screen with `FLAG_SECURE` (banking, password managers, incognito) hides its hierarchy from accessibility services. This is an intentional Android security policy.

---

## 4. Background Execution & Battery Optimization

Aggressive OEM task killers (MIUI, OneUI, ColorOS) may terminate background workers or disable the Accessibility service.

### Troubleshooting Battery Optimization:
1. Open **Android Settings > Apps > Nonea AI > Battery**.
2. Select **Unrestricted** (or "Don't optimize").
3. Enable **Autostart** (on Xiaomi/Vivo/Oppo devices).

---

## 5. Emergency Stop & Automation Recovery

If Nonea AI enters an unexpected loop or automation state:
1. **Floating Panic Button**: Tap the red floating **"Stop"** button visible on the screen.
2. **Voice Command**: Say *"Stop"*, *"Cancel"*, or *"থামো"*.
3. **Settings Reset**: Open Nonea AI Settings > Automation > Toggle master switch OFF.
4. **Android System Kill**: Go to Android Settings > Accessibility > Nonea AI > Turn OFF.
