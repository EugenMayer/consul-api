pluginManagement {
    plugins {
        id("net.ltgt.errorprone") version providers.gradleProperty("errorpronePluginVersion").get()
        id("io.github.ben-manes.versions") version providers.gradleProperty("versionsPluginVersion").get()
    }
}

rootProject.name = "consul-api"
