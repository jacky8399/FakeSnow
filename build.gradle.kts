import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("com.gradleup.shadow") version "9.0.0-beta4"
    id("java-library")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18" apply false
    id("xyz.jpenilla.run-paper") version "3.0.0-beta.1"
}

group = "com.jacky8399"
version = "1.4"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
            content {
                includeGroup("org.spigotmc")
            }
        }
        maven("https://maven.enginehub.org/repo/")
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")
    implementation(project(":common"))

    implementation(project(":v1_21_5_R1", configuration = "reobf"))
    implementation(project(":v1_21_8_R1", configuration = "reobf"))

    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.6")
    implementation("org.bstats:bstats-bukkit:3.0.0")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
}


tasks {
    assemble {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    shadowJar {
        archiveClassifier.set("")

        relocate("org.bstats", "com.jacky8399.fakesnow.bstats")
    }
}

tasks.runServer {
    minecraftVersion("1.21.8")
    downloadPlugins {
        github("dmulloy2", "ProtocolLib", "5.4.0", "ProtocolLib.jar")
        modrinth("worldedit", "Jk1z2u7n") // 7.3.15 version id for Bukkit
        modrinth("worldguard", "7.0.14")
    }
    jvmArgs(
//        "-XX:+AllowEnhancedClassRedefinition",
        "-Dnet.kyori.ansi.colorLevel=truecolor",
    )
}

tasks.register("runServer1_21_5", RunServer::class) {
    minecraftVersion("1.21.5")
    downloadPlugins {
        github("dmulloy2", "ProtocolLib", "5.4.0", "ProtocolLib.jar")
        modrinth("worldedit", "Jk1z2u7n") // 7.3.15 version id for Bukkit
        modrinth("worldguard", "7.0.14")
    }
    jvmArgs(
//        "-XX:+AllowEnhancedClassRedefinition",
        "-Dnet.kyori.ansi.colorLevel=truecolor",
    )
    pluginJars.from(tasks.shadowJar.flatMap { it.archiveFile })
    runDirectory = layout.projectDirectory.dir("run1_21_5")
    systemProperties["Paper.IgnoreJavaVersion"] = true
}

tasks.register("runServer1_21_6", RunServer::class) {
    minecraftVersion("1.21.6")
    downloadPlugins {
        github("dmulloy2", "ProtocolLib", "5.4.0", "ProtocolLib.jar")
        modrinth("worldedit", "Jk1z2u7n") // 7.3.15 version id for Bukkit
        modrinth("worldguard", "7.0.14")
    }
    jvmArgs(
//        "-XX:+AllowEnhancedClassRedefinition",
        "-Dnet.kyori.ansi.colorLevel=truecolor",
    )
    pluginJars.from(tasks.shadowJar.flatMap { it.archiveFile })
    runDirectory = layout.projectDirectory.dir("run1_21_6")
    systemProperties["Paper.IgnoreJavaVersion"] = true
}