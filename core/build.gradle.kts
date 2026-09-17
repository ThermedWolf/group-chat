plugins {
    id("java-library")
}

dependencies {
    // Both Paper and Fabric ship Gson on the runtime classpath already
    // (Bukkit/CraftBukkit and vanilla Minecraft both depend on it), so we
    // only need it at compile time here - no shading required.
    compileOnly("com.google.code.gson:gson:2.10.1")
}
