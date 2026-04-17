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
    namespace = "io.netscope.noop"
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
}

dependencies {
    // OkHttp — same compileOnly, so consumers can swap implementations freely
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
}
