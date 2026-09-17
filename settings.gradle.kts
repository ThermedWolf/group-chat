pluginManagement {
    repositories {
        // fabric-loom is published here, not on the default Gradle Plugin Portal.
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "groupchat"
include("core", "paper", "fabric")
