plugins {
    alias(libs.plugins.android.library)
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
    namespace = "io.netscope.export"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        // Written into HAR `creator.version`.
        buildConfigField("String", "VERSION_NAME", "\"${project.properties["VERSION_NAME"]}\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }

    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    api(project(":netscope-core"))
    implementation(libs.gson)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
