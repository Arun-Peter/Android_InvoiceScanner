import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional: pre-configure the AI service at build time from local.properties
// (or environment variables) so users don't have to type it on the phone.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(key: String): String =
    (localProps.getProperty(key) ?: System.getenv(key) ?: "").trim().replace("\"", "")

// Release signing key, kept in <project>/keystore (not committed to Git).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.r2r.invoicescanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.r2r.invoicescanner"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "1.7.0"

        buildConfigField("String", "DEFAULT_AI_PROVIDER", "\"${cfg("AI_PROVIDER")}\"")
        buildConfigField("String", "DEFAULT_AZURE_ENDPOINT", "\"${cfg("AZURE_OPENAI_ENDPOINT")}\"")
        buildConfigField("String", "DEFAULT_AZURE_DEPLOYMENT", "\"${cfg("AZURE_OPENAI_DEPLOYMENT")}\"")
        buildConfigField("String", "DEFAULT_AZURE_API_VERSION", "\"${cfg("AZURE_OPENAI_API_VERSION")}\"")
        buildConfigField("String", "DEFAULT_OPENAI_BASE_URL", "\"${cfg("OPENAI_BASE_URL")}\"")
        buildConfigField("String", "DEFAULT_OPENAI_MODEL", "\"${cfg("OPENAI_MODEL")}\"")
        buildConfigField("String", "DEFAULT_AI_API_KEY", "\"${cfg("AI_API_KEY")}\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file("keystore/" + keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Capture + automatic edge detection / crop / perspective correction
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    // On-device OCR (bundled model, works offline)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // QR code for setting up the authenticator app (app lock)
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
