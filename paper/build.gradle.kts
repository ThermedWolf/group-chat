plugins {
    id("java")
}

base {
    archivesName.set("groupchat-paper")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(26))
}

dependencies {
    // "26.3.build.+" resolves to the latest published build for 26.3.
    // Pin to a specific build (e.g. 26.3.build.8-alpha) if you want reproducible builds.
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    implementation(project(":core"))
}

// Merge the core module's classes straight into the plugin jar - no shadow
// plugin needed since core has no runtime deps of its own (Gson is already
// on the Paper server classpath).
tasks.jar {
    from(project(":core").sourceSets.main.get().output)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(mapOf("version" to project.version))
    }
}
