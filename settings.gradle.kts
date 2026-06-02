rootProject.name = "dm3270"

val openlegacyVersion: String by settings

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

val artifactoryOpsUser: String? = System.getenv("ARTIFACTORY_OL_OPS_USER")
val artifactoryOpsPassword: String? = System.getenv("ARTIFACTORY_OL_OPS_PASSWORD")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "openlegacy-m2-public"
            url = uri("https://openlegacy.jfrog.io/openlegacy/ol-public")
            credentials {
                username = artifactoryOpsUser
                password = artifactoryOpsPassword
            }
        }
        maven {
            name = "openlegacy-m2"
            url = uri("https://openlegacy.jfrog.io/openlegacy/ol-dev")
            credentials {
                username = artifactoryOpsUser
                password = artifactoryOpsPassword
            }
        }
        maven {
            name = "ol-3rd-party-libs"
            url = uri("https://openlegacy.jfrog.io/openlegacy/ol-3rd-party-libs")
            credentials {
                username = artifactoryOpsUser
                password = artifactoryOpsPassword
            }
        }
        mavenLocal()
    }
    versionCatalogs {
        create("libs") {
            from("io.openlegacy:version-catalog:$openlegacyVersion")
        }
    }
}
