plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.vanniktech.publish)
    signing
}

mavenPublishing {
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}


android {
    namespace = "io.netscope.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }

    testOptions { unitTests.isIncludeAndroidResources = true }
}

// Room exports one JSON schema per DB version into netscope-core/schemas/ (commit them).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // OkHttp — provided by consumer, not bundled
    compileOnly(libs.okhttp)
    implementation(libs.gson)
    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp) // main uses compileOnly
}
