plugins {
    id("com.android.application")
}

android {
    namespace = "com.purrmatch.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.purrmatch.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
