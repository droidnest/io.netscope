plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

mavenPublishing {
    signAllPublications()
}

android {
    namespace = "io.netscope.notification"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }
}

dependencies {
    api(project(":netscope-core"))
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
}
