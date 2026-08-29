plugins {
    id("com.android.application")
}

android {
    namespace = "com.youngsix6.betterpowermenu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.youngsix6.betterpowermenu"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
    }

    buildTypes {
        release {
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    // Xposed API：仅编译期使用，运行时由 Xposed/LSPosed 框架提供
    compileOnly("de.robv.android.xposed:api:82")
}
