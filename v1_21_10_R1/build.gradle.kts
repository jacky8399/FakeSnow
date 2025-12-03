plugins {
    `java-library`
    id("io.papermc.paperweight.userdev")
}

group = "com.jacky8399.fakesnow.v1_21_10_R1"

dependencies {
    paperweight.paperDevBundle("1.21.10-R0.1-SNAPSHOT")
    implementation(project(":common"))
}