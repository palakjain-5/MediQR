import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Supabase credentials live in secrets.properties (git-ignored) so they are never
// committed. If the file or a key is missing, the values fall back to empty strings
// and the app simply cannot reach Supabase until real credentials are provided.
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Release signing lives in keystore.properties (git-ignored) next to the keystore
// file, so no signing password is ever committed. When the file is absent (e.g. a
// fresh checkout) the release build falls back to the debug key so it still builds.
val releaseSigning = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKeystore = releaseSigning.getProperty("storeFile")?.isNotBlank() == true

android {
    namespace = "com.example.mediqr"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.mediqr"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${secrets.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secrets.getProperty("SUPABASE_ANON_KEY", "")}\"")

        // Base URL encoded into the QR code (Phase 5 resolves this URL as the
        // public card deep link). Set the real domain in secrets.properties -
        // PUBLIC_CARD_BASE_URL - when a hosted card page is deployed. The host
        // also feeds the manifest intent filter (manifestPlaceholders below).
        val publicCardBaseUrl = secrets
            .getProperty("PUBLIC_CARD_BASE_URL", "https://your-domain.example.com")
            .trim()
            .trimEnd('/')
        manifestPlaceholders["publicCardHost"] = publicCardBaseUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")

        buildConfigField(
            "String",
            "PUBLIC_CARD_BASE_URL",
            "\"$publicCardBaseUrl\"",
        )
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            // Production build: R8 code shrinking + resource shrinking,
            // keep rules in proguard-rules.pro plus consumer rules from
            // the libraries (compose, kotlinx.serialization, supabase-kt).
            optimization {
                enable = true
            }
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed with the local release keystore when configured
            // (keystore.properties, git-ignored); otherwise falls back to
            // the debug key so a fresh checkout can still build a runnable
            // release APK for demos.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // supabase-kt targets API 26+; desugaring keeps it working on our minSdk 24.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)

    // App architecture: navigation between screens, view models, coroutines.
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Supabase: authentication + database (Postgrest).
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // QR code generation.
    implementation(libs.zxing.core)
    // Unit tests round-trip the production QR encoding (encode + decode).
    testImplementation(libs.zxing.core)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
}
