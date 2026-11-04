plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":domain"))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    testImplementation(libs.coroutines.test)
    testImplementation("junit:junit:4.13.2")
}
