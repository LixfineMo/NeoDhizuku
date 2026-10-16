pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "Dhizuku"
include(":app")
include(":hidden-api")
include(":Dhizuku-API")
include(":Dhizuku-SERVER_API")

project(":Dhizuku-API").projectDir = file("Dhizuku-API/dhizuku-api")
project(":Dhizuku-SERVER_API").projectDir = file("Dhizuku-API/dhizuku-server_api")
