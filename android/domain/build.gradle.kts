plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.coroutines.core)
    testImplementation("junit:junit:4.13.2")
}
