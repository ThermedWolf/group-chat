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
    // Pinned for reproducible builds — bump manually after testing against a new Paper build.
    // Candidate: 26.3.build.8-alpha is cached locally; check https://repo.papermc.io for newer.
    compileOnly("io.papermc.paper:paper-api:26.3.build.8-alpha")
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
