pluginManagement {
    repositories {
        gradlePluginPortal()
        // com.vanniktech.maven.publish is released to Maven Central only
        mavenCentral()
    }
    plugins {
        id("net.ltgt.errorprone") version providers.gradleProperty("errorpronePluginVersion").get()
        id("io.github.ben-manes.versions") version providers.gradleProperty("versionsPluginVersion").get()
        id("com.vanniktech.maven.publish") version providers.gradleProperty("mavenPublishPluginVersion").get()
    }
}

rootProject.name = "consul-api"
