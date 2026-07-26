import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("com.github.hypfvieh:dbus-java-core:5.1.1")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.1")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.happwner.desktop.MainKt"
        if (System.getProperty("os.name").startsWith("Linux", ignoreCase = true)) {
            // OpenGL through XWayland can leave stale or duplicated regions while resizing.
            // The application UI is small enough that the stable software renderer is preferable.
            jvmArgs("-Dskiko.renderApi=SOFTWARE")
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Happwner PC"
            packageVersion = project.version.toString()
            description = "Local and home-network proxy subscription server"
            vendor = "Happwner contributors"
            modules(
                "java.base",
                "java.desktop",
                "java.net.http",
                "jdk.httpserver",
                "java.logging",
                "jdk.security.auth",
            )
            windows {
                menuGroup = "Happwner PC"
                shortcut = true
                perUserInstall = true
            }
            linux {
                shortcut = true
                menuGroup = "Network"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
