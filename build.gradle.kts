plugins {
    id("main.convention")
}

version = project.findProperty("version") as String

val guavaVersion: String by project

dependencies {
    implementation(libs.slf4j.api)
    // we are using a compile only dependency to overcome a cyrclic references in core,
    // because this library is used by ol3270
    // if any other module will use this library, it also needs to include a loki-tcp-recorder
    compileOnly(libs.openlegacy.loki.tcp.recorder)

    // test
    testImplementation(libs.openlegacy.loki.tcp.recorder)
    testImplementation(libs.junit.jupiter.engine.java11)
    testImplementation("org.junit.jupiter:junit-jupiter-params:${libs.versions.junitJupiterJava11.get()}")
    testRuntimeOnly(libs.junit.platform.launcher.java11)
    testImplementation(libs.assertj.core)
    testImplementation("com.google.guava:guava:$guavaVersion")
    testImplementation(libs.mockito.core)
    testImplementation("org.mockito:mockito-junit-jupiter:${libs.versions.mockito.get()}")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("dm3270")
    archiveVersion.set("")
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }
}
