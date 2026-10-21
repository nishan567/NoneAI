# Complete Termux Android Build Guide

This comprehensive guide details the step-by-step process to set up the Android SDK in a clean **Termux** environment on an Android device, install the required build tools and platform components, configure Gradle, and run `./gradlew assembleDebug` to compile and install the APK.

---

## Table of Contents
1. [System Architecture & Termux Context](#1-system-architecture--termux-context)
2. [Step 1: Install Termux Packages & OpenJDK 17](#step-1-install-termux-packages--openjdk-17)
3. [Step 2: Install and Configure the Android SDK](#step-2-install-and-configure-the-android-sdk)
4. [Step 3: Setup Environment Variables in ~/.bashrc](#step-3-setup-environment-variables-in-bashrc)
5. [Step 4: Install Android Platforms & Build Tools](#step-4-install-android-platforms--build-tools)
6. [Step 5: Solve the Termux AAPT2 ARM64 Binary Issue](#step-5-solve-the-termux-aapt2-arm64-binary-issue)
7. [Step 6: Configure Project & local.properties](#step-6-configure-project--localproperties)
8. [Step 7: Optimize gradle.properties for Mobile RAM](#step-7-optimize-gradleproperties-for-mobile-ram)
9. [Step 8: Build the Debug APK](#step-8-build-the-debug-apk)
10. [Step 9: Install the APK on Your Device](#step-9-install-the-apk-on-your-device)
11. [Step 10: Building & Signing a Release APK](#step-10-building--signing-a-release-apk)
12. [Troubleshooting & Common Termux Errors](#troubleshooting--common-termux-errors)

---

## 1. System Architecture & Termux Context

Building an Android app inside Android using Termux requires understanding two key device factors:
- **CPU Architecture**: Most modern Android phones run on **ARM64** (`aarch64`). Standard Google SDK binaries (like AAPT2) are compiled for x86_64 Linux and will fail unless we use Termux's native ARM64 build tools.
- **RAM Constraints**: Mobile OS Low Memory Killers (LMK) will terminate memory-heavy Gradle daemons. We must tune the JVM heap to prevent OOM errors.

---

## Step 1: Install Termux Packages & OpenJDK 17

Open Termux and request storage permissions so Termux can access your shared storage:
```bash
termux-setup-storage
```

Update packages and install OpenJDK 17, Git, compression utilities, and native Android build tools:
```bash
pkg update -y && pkg upgrade -y
pkg install -y openjdk-17 git wget curl tar unzip zip aapt apksigner
```

Verify your Java version:
```bash
java -version
# Expected output:
# openjdk version "17.0.x" ...
```

---

## Step 2: Install and Configure the Android SDK

Android Command-Line Tools must be downloaded and placed into a specific directory structure expected by `sdkmanager`.

### 1. Create the SDK directory:
```bash
mkdir -p $HOME/android-sdk/cmdline-tools
cd $HOME/android-sdk/cmdline-tools
```

### 2. Download the official Google Command-Line Tools:
```bash
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
unzip cmdline-tools.zip
mv cmdline-tools latest
rm cmdline-tools.zip
```

> **Note**: The directory must be named `latest` so the path is `$HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager`.

---

## Step 3: Setup Environment Variables in ~/.bashrc

Configure the paths to the SDK, Java, and platform tools permanently:

```bash
cat << 'EOF' >> ~/.bashrc
# Android SDK & Java Configuration
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$HOME/android-sdk
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0
# Native AAPT2 override for Termux ARM64
export ANDROID_AAPT2_OVERRIDE=$(which aapt2 || which aapt)
EOF

# Reload environment
source ~/.bashrc
```

Verify that `sdkmanager` is recognized:
```bash
sdkmanager --version
```

---

## Step 4: Install Android Platforms & Build Tools

Accept the SDK licenses and install Android SDK Platform 34 and Build Tools 34.0.0:

```bash
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

Verify the installation:
```bash
ls -l $ANDROID_HOME/platforms/android-34/android.jar
# Should exist and be ~40MB
```

---

## Step 5: Solve the Termux AAPT2 ARM64 Binary Issue

By default, the Android Gradle Plugin (AGP) downloads an x86_64 version of `aapt2`, which causes the error:
```
AAPT2 execution failed: cannot execute binary file: Exec format error
```

### Solution:
Termux provides a precompiled ARM64 `aapt2` package. We already installed it in Step 1 (`pkg install aapt`).

We ensure Gradle uses Termux's ARM64 AAPT2 via the environment variable:
```bash
export ANDROID_AAPT2_OVERRIDE=$(which aapt2 || which aapt)
```

Add this to `~/.bashrc` (included in Step 3) so it applies automatically to all builds.

---

## Step 6: Configure Project & local.properties

Navigate to your project repository:
```bash
cd $HOME/NoneaAI
# or your cloned project directory
```

Create `local.properties` pointing to your Termux Android SDK:
```bash
echo "sdk.dir=/data/data/com.termux/files/home/android-sdk" > local.properties
```

Ensure the Gradle wrapper script has execution permissions:
```bash
chmod +x gradlew
```

---

## Step 7: Optimize gradle.properties for Mobile RAM

To prevent the Android OS Low Memory Killer (LMK) from killing the Gradle build daemon mid-compilation, configure memory limits in `gradle.properties`:

```bash
cat << 'EOF' > gradle.properties
# Prevent out-of-memory errors on mobile devices
org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m -XX:+UseParallelGC
org.gradle.parallel=false
org.gradle.daemon=false
org.gradle.caching=true
android.useAndroidX=true
android.enableJetifier=false
EOF
```

---

## Step 8: Build the Debug APK

Now you are ready to compile the Android project!

Run the build with `--no-daemon` to ensure the process exits cleanly and does not hold background memory:

```bash
./gradlew assembleDebug --no-daemon --stacktrace
```

*If `./gradlew` fails with shell permissions on custom storage mounts, run with bash:*
```bash
bash ./gradlew assembleDebug --no-daemon
```

### Expected Output:
```
> Task :app:preBuild UP-TO-DATE
> Task :app:compileDebugKotlin
> Task :app:mergeDebugResources
> Task :app:processDebugMainManifest
> Task :app:dexBuilderDebug
> Task :app:packageDebug

BUILD SUCCESSFUL in 1m 24s
42 actionable tasks: 42 executed
```

---

## Step 9: Install the APK on Your Device

Once the build finishes successfully, your generated debug APK is located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

To prompt the Android system package installer directly from Termux:
```bash
termux-open app/build/outputs/apk/debug/app-debug.apk
```

Android will show the standard installation dialog: tap **"Install"** or **"Update"**.

---

## Step 10: Building & Signing a Release APK

To create a signed, installable release APK:

### 1. Generate a Keystore (one-time setup):
```bash
keytool -genkey -v -keystore nonea-release.keystore \
  -alias nonea -keyalg RSA -keysize 2048 -validity 10000
```
Enter a secure password when prompted.

### 2. Assemble the Release APK:
```bash
./gradlew assembleRelease --no-daemon
```

### 3. Sign the APK with `apksigner`:
```bash
apksigner sign --ks nonea-release.keystore \
  --ks-key-alias nonea \
  --out app/build/outputs/apk/release/NoneaAI-release.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

### 4. Install the signed release APK:
```bash
termux-open app/build/outputs/apk/release/NoneaAI-release.apk
```

---

## Troubleshooting & Common Termux Errors

| Error Message | Cause | Exact Fix |
|---|---|---|
| `OutOfMemoryError: Java heap space` | JVM ran out of allocated memory. | In `gradle.properties`, set `org.gradle.jvmargs=-Xmx1536m` and `--no-daemon`. |
| `AAPT2 execution failed: cannot execute binary file` | AGP downloaded x86_64 binary on ARM64 device. | Run `pkg install aapt` and `export ANDROID_AAPT2_OVERRIDE=$(which aapt2)`. |
| `SDK location not found` | Missing `local.properties`. | Run `echo "sdk.dir=$HOME/android-sdk" > local.properties`. |
| `bash: ./gradlew: Permission denied` | Gradle wrapper is missing executable flag. | Run `chmod +x gradlew` or execute via `bash ./gradlew`. |
| `Failed to install the following Android SDK packages as some licences have not been accepted` | Unaccepted licenses in SDK. | Run `yes \| sdkmanager --licenses`. |
| `Termux killed: Signal 9 / Out of memory` | Android OS Low Memory Killer closed Termux. | Close heavy background apps and run build with `--max-workers=2`. |
| `Accessibility restricted settings` on Android 13+ | Android security policy blocks sideloaded accessibility. | Go to **Android Settings > Apps > Nonea AI > 3 dots (top right) > Allow restricted settings**. |

---

## Quick One-Liner Verification Script

To verify that your Termux environment is ready to build:
```bash
echo "=== Environment Check ===" && \
java -version && \
echo "ANDROID_HOME: $ANDROID_HOME" && \
sdkmanager --version && \
echo "AAPT2 Path: $ANDROID_AAPT2_OVERRIDE" && \
echo "=== Ready to build! ==="
```
