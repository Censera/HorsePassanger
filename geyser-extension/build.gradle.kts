plugins {
    java
}

group = "me.censera"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.geysermc.geyser:api:2.11.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

tasks.jar {
    archiveBaseName = "second-passenger-geyser"

    from("src/main/resources")
}
