# Nonea AI — Termux Build & Installation Guide

This guide explains how to compile and build **Nonea AI** directly on an Android device inside a clean **Termux** environment without Android Studio.

---

## 1. Prerequisites in Termux

Open Termux and update the package repositories:

```bash
pkg update -y && pkg upgrade -y
pkg install -y openjdk-17 git wget curl tar unzip zip aapt apksigner
```

Verify your Java installation:
```bash
java -version
# Should output openjdk 17.x
```

---

## 2. Android Command-Line Tools & SDK Setup

On Termux, the Android SDK must be configured manually.

### A. Create Directory Structure
```bash
mkdir -p $HOME/android-sdk/cmdline-tools
cd $HOME/android-sdk/cmdline-tools
```

### B. Download Command-Line Tools
Download the official Android Command-Line Tools for Linux:
```bash
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
unzip cmdline-tools.zip
mv cmdline-tools latest
rm cmdline-tools.zip
```

### C. Configure Environment Variables
Add the SDK paths to `~/.bashrc`:
```bash
cat << 'EOF' >> ~/.bashrc
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
EOF

source ~/.bashrc
```

### D. Install Build Tools & Platforms
Accept licenses and install Android SDK platform 34:
```bash
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

---

## 3. Project Configuration & local.properties

Clone or navigate into the NoneaAI repository:
```bash
cd $HOME/NoneaAI
```

Create `local.properties` pointing Gradle to your Termux Android SDK:
```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

Ensure Gradle wrapper has execution permissions:
```bash
chmod +x gradlew
```

---

## 4. Building the Debug APK

Run the Gradle assembleDebug task:
```bash
./gradlew assembleDebug
```
*Alternative if running with system Gradle:*
```bash
gradle assembleDebug
```

### Output Location:
Upon successful build:
```
app/build/outputs/apk/debug/app-debug.apk
```

To install directly onto your device from Termux:
```bash
termux-open app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. Building & Signing a Release APK

### A. Generate a Keystore (First-time only)
```bash
keytool -genkey -v -keystore nonea-release.keystore \
  -alias nonea -keyalg RSA -keysize 2048 -validity 10000
```

### B. Build Unsigned Release APK
```bash
./gradlew assembleRelease
```

### C. Align and Sign the APK with apksigner
```bash
# 1. Zipalign (optional if build tools handles it)
# 2. Sign with apksigner:
apksigner sign --ks nonea-release.keystore \
  --ks-key-alias nonea \
  --out app/build/outputs/apk/release/NoneaAI-v2.3-release.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## 6. Common Errors & Troubleshooting in Termux

### 1. `OutOfMemoryError: Java heap space`
**Cause:** Android devices often restrict Termux background process memory.
**Fix:** Configure Gradle memory limits in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m -XX:+UseParallelGC
org.gradle.parallel=false
org.gradle.daemon=false
```

### 2. `AAPT2 execution failed: cannot execute binary file`
**Cause:** Default AAPT2 binary packaged in AGP is built for standard Linux x86_64, whereas Termux runs on ARM64 (`aarch64`).
**Fix:** Tell Gradle to use Termux's system aapt2:
```bash
pkg install aapt
```
Add to `app/build.gradle.kts`:
```kotlin
android {
    aaptOptions {
        // Points to system aapt2 on Termux
    }
}
```
Or set environment variable:
```bash
export ANDROID_AAPT2_OVERRIDE=$(which aapt2 || which aapt)
```

### 3. `SDK location not found`
**Fix:** Ensure `local.properties` contains `sdk.dir=/data/data/com.termux/files/home/android-sdk`.

### 4. `Gradle Daemon killed unexpectedly`
**Cause:** Android OS low-memory killer (LMK).
**Fix:** Run build with `--no-daemon`:
```bash
./gradlew assembleDebug --no-daemon --max-workers=2
```
