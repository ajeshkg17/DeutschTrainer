plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.ajesh.deutschtrainer.v4"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ajesh.deutschtrainer.v4"
        minSdk = 23
        targetSdk = 35
        versionCode = 40_000 + ciRunNumber
        versionName = "4.0.$ciRunNumber"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
