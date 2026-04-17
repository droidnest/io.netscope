import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.vanniktech.publish) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val libVersion = project.properties["VERSION_NAME"] as String
val libGroup   = project.properties["GROUP"] as String

val descriptions = mapOf(
    "netscope-core"         to "NetScope — Core interceptor and storage module.",
    "netscope-ui"           to "NetScope — Compose UI module.",
    "netscope-notification" to "NetScope — Persistent notification with live request stats.",
    "netscope-export"       to "NetScope — Export transactions as cURL or HAR.",
    "netscope-no-op"        to "NetScope — Release no-op stub, zero overhead.",
)

subprojects {
    // Runs only on modules that have vanniktech applied
    plugins.withId("com.vanniktech.maven.publish") {
        val artifactId = project.name
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
            signAllPublications()  // reads ORG_GRADLE_PROJECT_signingInMemoryKey* on CI
            coordinates(libGroup, artifactId, libVersion)
            pom {
                name.set(artifactId)
                description.set(descriptions[artifactId] ?: "NetScope")
                inceptionYear.set("2024")
                url.set("https://github.com/nidhal92/io.netscope")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://opensource.org/licenses/Apache-2.0")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("nidhal92")
                        name.set("Nidhal Belhaj Salem")
                        email.set("nidhal.belhadjsalem@gmail.com")
                        url.set("https://github.com/nidhal92")
                    }
                }
                scm {
                    url.set("https://github.com/nidhal92/io.netscope")
                    connection.set("scm:git:git://github.com/nidhal92/io.netscope.git")
                    developerConnection.set("scm:git:ssh://git@github.com/nidhal92/io.netscope.git")
                }
            }
        }
    }
}