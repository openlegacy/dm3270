plugins {
    id("main.convention")
}

version = project.findProperty("version") as String

val slf4jVersion: String by project
val junitVersion: String by project
val wireshamVersion: String by project
val assertjVersion: String by project
val guavaVersion: String by project
val mockitoVersion: String by project

dependencies {
    // Main dependencies
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    // Test dependencies
    testImplementation("junit:junit:$junitVersion")
    testImplementation("us.abstracta:wiresham:$wireshamVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("com.google.guava:guava:$guavaVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
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
