plugins {
  java
  kotlin("jvm")
  id("org.jlleitschuh.gradle.ktlint")
  id("com.diffplug.spotless")
  `maven-publish`
}

group = "com.blazemeter"

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

val artifactoryOpsUser: String? = System.getenv("ARTIFACTORY_OL_OPS_USER")
val artifactoryOpsPassword: String? = System.getenv("ARTIFACTORY_OL_OPS_PASSWORD")

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
    name = "ol-3rd-party-libs-proprietary"
    url = uri("https://openlegacy.jfrog.io/openlegacy/ol-3rd-party-libs-proprietary")
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


spotless {
  java {
    googleJavaFormat("1.22.0").aosp()
    target("src/**/*.java")
    removeUnusedImports()
    endWithNewline()
    trimTrailingWhitespace()
    encoding("UTF-8")
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    custom("noWildcardImports") { content ->
      if (content.contains("import ") && content.contains(".*;")) {
        throw GradleException("Wildcard imports are not allowed! Found in: $content")
      }
      content
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
  }
}

sourceSets {
  main {
    java {
      setSrcDirs(listOf("src/main/java"))
    }
    kotlin {
      setSrcDirs(listOf("src/main/kotlin"))
    }
    resources {
      setSrcDirs(listOf("src/main/resources"))
    }
  }
  test {
    java {
      setSrcDirs(listOf("src/test/java"))
    }
    kotlin {
      setSrcDirs(listOf("src/test/kotlin"))
    }
    resources {
      setSrcDirs(listOf("src/test/resources"))
    }
  }
}

tasks.withType<JavaCompile> {
  options.isIncremental = false
  options.encoding = "UTF-8"
  options.release.set(8)
}

tasks.test {
  // Equivalent to Maven Surefire useSystemClassLoader=false
  forkEvery = 0
  maxParallelForks = 1
}

// Create source JAR task
val sourcesJar by tasks.creating(Jar::class) {
  archiveBaseName.set(project.name)
  archiveClassifier.set("sources")
  archiveVersion.set("")
  from(sourceSets.main.get().allSource)
}

// Add source JAR to build artifacts
artifacts {
  archives(sourcesJar)
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
  version.set("1.3.1")
  enableExperimentalRules.set(true)
}

// Publishing configuration
afterEvaluate {
  publishing {
    publications {
      // Regular JAR publication
      create<MavenPublication>("maven") {
        groupId = project.group.toString()
        artifactId = project.name
        version = project.version.toString()

        // Add the main JAR artifact
        artifact(tasks.named("jar").get())

        // Add source JAR
        artifact(sourcesJar)

        pom {
          name.set(project.name)
          description.set("${project.name} library")
        }
      }
    }

    repositories {
      mavenLocal()

      // Publish to OpenLegacy Artifactory
      maven {
        name = "openlegacy-m2-public"
        url = uri("https://openlegacy.jfrog.io/openlegacy/ol-public")
        credentials {
          username = artifactoryOpsUser
          password = artifactoryOpsPassword
        }
      }
    }
  }
}
