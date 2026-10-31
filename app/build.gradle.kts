plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val stableDebugStore = System.getenv("CAPSULE_DEBUG_KEYSTORE")
val stableDebugStorePassword = System.getenv("CAPSULE_DEBUG_STORE_PASSWORD")
val stableDebugAlias = System.getenv("CAPSULE_DEBUG_KEY_ALIAS")
val stableDebugKeyPassword = System.getenv("CAPSULE_DEBUG_KEY_PASSWORD")
val hasStableDebugSigning = listOf(
    stableDebugStore, stableDebugStorePassword, stableDebugAlias, stableDebugKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.example.capsule"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.capsulelite"
        minSdk = 24
        targetSdk = 34
        versionCode = 69
        versionName = "1.4.21"
        buildConfigField("String", "BUILD_ID", "\"${System.getenv("GITHUB_RUN_NUMBER") ?: "local"}\"")
    }

    signingConfigs {
        if (hasStableDebugSigning) {
            create("stableDebug") {
                storeFile = file(stableDebugStore!!)
                storePassword = stableDebugStorePassword
                keyAlias = stableDebugAlias
                keyPassword = stableDebugKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasStableDebugSigning) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
        }
    }

    // NEW: tells the Java compiler to use Java 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // NEW: tells the Kotlin compiler to use Java 17 as well
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.zxing:core:3.5.3")
}
