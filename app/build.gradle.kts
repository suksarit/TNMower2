plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.tnmower.tnmower"

    // 🔴 FIX: ใช้ SDK เสถียร
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tnmower.tnmower"
        minSdk = 24

        // 🔴 FIX: ให้ตรง compileSdk
        targetSdk = 34

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
