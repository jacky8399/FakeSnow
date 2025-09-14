plugins {
    id("java-library")
}

group = "com.jacky8399.fakesnow"

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")
    compileOnlyApi("net.dmulloy2:ProtocolLib:5.4.0")
    compileOnlyApi("org.jetbrains:annotations:23.0.0")
}