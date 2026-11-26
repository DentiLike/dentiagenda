plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dentilike.dentiagenda"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dentilike.dentiagenda"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "1.6"
    }

    signingConfigs {
        create("dentilike") {
            storeFile = file("../keystore/dentiagenda.jks")
            storePassword = "dentilike2026"
            keyAlias = "dentiagenda"
            keyPassword = "dentilike2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dentilike")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    implementation("com.google.androidbrowserhelper:androidbrowserhelper:2.5.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.core:core-ktx:1.12.0")
}
