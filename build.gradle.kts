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
    // ── Maven publishing ──────────────────────────────────────────────────────
    plugins.withId("com.vanniktech.maven.publish") {
        val artifactId = project.name
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
            // Signing is wired explicitly in afterEvaluate below — do NOT call
            // signAllPublications() here; it silently skips useInMemoryPgpKeys()
            // when the property is null yet still registers sign tasks, causing
            // "has no configured signatory" at execution time.
            coordinates(libGroup, artifactId, libVersion)
            pom {
                name.set(artifactId)
                description.set(descriptions[artifactId] ?: "NetScope")
                inceptionYear.set("2024")
                url.set("https://github.com/droidnest/io.netscope")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://opensource.org/licenses/Apache-2.0")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("droidnest")
                        name.set("droidnest")
                        url.set("https://github.com/droidnest")
                    }
                }
                scm {
                    url.set("https://github.com/droidnest/io.netscope")
                    connection.set("scm:git:https://github.com/droidnest/io.netscope.git")
                    developerConnection.set("scm:git:ssh://git@github.com/droidnest/io.netscope.git")
                }
            }
        }
    }

}