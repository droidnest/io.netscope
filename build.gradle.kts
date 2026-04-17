import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension

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
                url.set("https://github.com/nidhal92/netscope")
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
                    url.set("https://github.com/nidhal92/netscope")
                    connection.set("scm:git:git://github.com/nidhal92/netscope.git")
                    developerConnection.set("scm:git:ssh://git@github.com/nidhal92/netscope.git")
                }
            }
        }
    }

    // ── Explicit signing — must run after publishing extension is configured ──
    plugins.withId("com.vanniktech.maven.publish") {
        afterEvaluate {
            val inMemoryKey = findProperty("signingInMemoryKey") as String?
            val inMemoryKeyId = findProperty("signingInMemoryKeyId") as String?
            val inMemoryKeyPwd = findProperty("signingInMemoryKeyPassword") as String? ?: ""

            val isCI = System.getenv("CI") == "true"

            if (inMemoryKey.isNullOrBlank()) {
                if (isCI) {
                    throw GradleException(
                        "[${project.name}] CI build detected but signingInMemoryKey is not set. " +
                        "Add SIGNING_KEY to GitHub repository secrets and ensure the workflow " +
                        "sets ORG_GRADLE_PROJECT_signingInMemoryKey."
                    )
                }
                // Local build without signing — skip
                logger.lifecycle("[${project.name}] No signing key found — skipping signing (local build).")
                return@afterEvaluate
            }

            apply(plugin = "signing")
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(inMemoryKeyId, inMemoryKey, inMemoryKeyPwd)
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }
    }
}