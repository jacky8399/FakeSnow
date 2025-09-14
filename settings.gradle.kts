pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://papermc.io/repo/repository/maven-public/")
    }
}

rootProject.name = "FakeSnow"
include("common")
include("v1_21_5_R1")
include("v1_21_8_R1")

