plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.purrmatch.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.purrmatch.android"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":presentation"))
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
}

// This module hosts screens and the component catalogue; the UI library also builds in release.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}
