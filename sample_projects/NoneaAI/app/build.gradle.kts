plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// ---- AI configuration: read from Gradle properties or the environment, never from source files ----
// Put these in ~/.gradle/gradle.properties (NOT in the repo):
//   nonea.proxyUrl=https://your-proxy.example.com     # makes "Secure proxy" the default mode
//   nonea.allowDirectKey=false                        # recommended for any build you distribute
fun optionalProp(name: String, env: String): String =
    (project.findProperty(name) as String?) ?: System.getenv(env) ?: ""

val proxyUrl = optionalProp("nonea.proxyUrl", "NONEA_PROXY_URL").trim().replace("\"", "")
val allowDirectKey = (optionalProp("nonea.allowDirectKey", "NONEA_ALLOW_DIRECT_KEY").ifBlank { "true" }).toBoolean()

android {
    namespace = "com.nonea.ai"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.nonea.ai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "PROXY_URL", "\"$proxyUrl\"")
        buildConfigField("boolean", "ALLOW_DIRECT_KEY", allowDirectKey.toString())
        buildConfigField("String", "DEFAULT_AI_MODE", if (proxyUrl.isNotBlank()) "\"proxy\"" else "\"direct\"")
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}

// Fails the build if something that looks like a Google API key is committed under src/.
val checkNoSecrets by tasks.registering {
    val srcDir = layout.projectDirectory.dir("src")
    inputs.dir(srcDir)
    doLast {
        val rx = Regex("AIza[0-9A-Za-z_\\-]{35}")
        val exts = setOf("kt", "xml", "kts", "json", "properties", "txt", "md", "js")
        val hits = srcDir.asFile.walkTopDown()
            .filter { it.isFile && it.extension in exts && rx.containsMatchIn(it.readText()) }
            .map { it.relativeTo(srcDir.asFile).path }.toList()
        if (hits.isNotEmpty()) throw GradleException("Possible Google API key found in source: $hits. Remove it and revoke the key.")
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(checkNoSecrets) }   // lazy: AGP may add preBuild after this script runs
