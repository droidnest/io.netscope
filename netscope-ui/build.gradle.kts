plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.vanniktech.publish)

}


android {
    namespace = "io.netscope.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    buildFeatures {
        compose = true
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }
}

dependencies {
    api(project(":netscope-core"))
    implementation(project(":netscope-export"))

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.activity)
    implementation(libs.compose.viewmodel)
    implementation(libs.compose.lifecycle)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)
}
