val callguardBuildNumber = providers.environmentVariable("CALLGUARD_BUILD_NUMBER")
    .orElse(providers.gradleProperty("callguardBuildNumber"))
    .orElse("1")
    .get()
    .toInt()

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mainak.callguardai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mainak.callguardai"
        minSdk = 26
        targetSdk = 35
        versionCode = callguardBuildNumber
        versionName = "0.3.$callguardBuildNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.55")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
