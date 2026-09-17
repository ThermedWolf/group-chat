plugins {
    id("net.fabricmc.fabric-loom") version "1.18.2"
    id("java")
}

base {
    archivesName.set("groupchat-fabric")
}

repositories {
    maven("https://maven.fabricmc.net/")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(26))
}

dependencies {
    // Minecraft 26.1+ ships unobfuscated with Mojang's own names, so there's
    // no more `mappings(...)` entry and no more `modImplementation` - regular
    // `implementation`/`compileOnly` work directly against Minecraft now.
    minecraft("com.mojang:minecraft:26.3")

    // Latest stable for 26.3 - bump via https://fabricmc.net/develop/
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation("net.fabricmc.fabric-api:fabric-api:0.160.6+26.3")

    implementation(project(":core"))
}

// Bundle core's classes into the mod jar the same way the Paper build does.
tasks.jar {
    from(project(":core").sourceSets.main.get().output)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}
