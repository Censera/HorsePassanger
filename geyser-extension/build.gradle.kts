plugins {
    java
}

group = "me.censera"
version = rootProject.version

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    compileOnly("org.geysermc.geyser:api:2.11.0-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

tasks.jar {
    archiveBaseName = "second-passenger-geyser"
}
