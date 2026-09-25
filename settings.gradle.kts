plugins {
    // Если на машине нет JDK 21, Gradle сам скачает его для toolchain.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "carsharing"
