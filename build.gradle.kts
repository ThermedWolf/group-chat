allprojects {
    version = "2.0.0"
    group = "net.thermedwolf.groupchat"
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_26
        targetCompatibility = JavaVersion.VERSION_26
    }
}
