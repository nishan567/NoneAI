#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "===================================================="
echo "    Nonea AI — All-in-One Termux Build Script      "
echo "===================================================="

# 1. Update and install required Termux packages
echo "[1/6] Installing dependencies (Java 17, aapt, git, wget)..."
pkg update -y
pkg install -y openjdk-17 git wget curl tar unzip zip aapt apksigner

# 2. Setup Java environment
export JAVA_HOME="/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"

# 3. Setup Android SDK if not already present
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
    echo "[2/6] Setting up Android SDK commandline-tools..."
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    cd "$ANDROID_HOME/cmdline-tools"
    
    if [ ! -f "tools.zip" ]; then
        wget -q --show-progress https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O tools.zip
    fi
    unzip -q tools.zip
    rm -rf latest
    mv cmdline-tools latest
    rm -f tools.zip
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# 4. Accept licenses and install platform 34
echo "[3/6] Installing Android platform 34 and build-tools 34.0.0..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" > /dev/null

# 5. Configure local project
cd "$HOME/NoneaAI" 2>/dev/null || cd "$(dirname "$0")"

echo "[4/6] Configuring local.properties and memory limits..."
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Configure gradle properties for Termux ARM64 memory limits
cat << 'EOF' > gradle.properties
org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m -XX:+UseParallelGC
org.gradle.parallel=false
org.gradle.daemon=false
android.useAndroidX=true
EOF

# Workaround for ARM64 Termux AAPT2
if command -v aapt2 >/dev/null 2>&1; then
    export ANDROID_AAPT2_OVERRIDE="$(which aapt2)"
elif command -v aapt >/dev/null 2>&1; then
    export ANDROID_AAPT2_OVERRIDE="$(which aapt)"
fi

chmod +x gradlew

# 6. Build Debug APK
echo "[5/6] Compiling Nonea AI APK with Gradle..."
./gradlew assembleDebug --no-daemon --max-workers=2

echo "===================================================="
echo " [SUCCESS] Nonea AI Debug APK compiled successfully!"
echo " Output APK location:"
echo " $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
echo "===================================================="

# 7. Prompt installation
APK_PATH="$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo "[6/6] Launching installer on your device..."
    termux-open "$APK_PATH" 2>/dev/null || echo "To install, run: termux-open $APK_PATH"
fi
