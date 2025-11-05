plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.1")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
}
