rootProject.name = "NetScope"
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
include(":netscope-core")
include(":netscope-ui")
include(":netscope-notification")
include(":netscope-no-op")
include(":sample")

