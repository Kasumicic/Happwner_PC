plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
}

val appVersion = providers.gradleProperty("appVersion").get()

allprojects {
    group = "com.happwner"
    version = appVersion
}
