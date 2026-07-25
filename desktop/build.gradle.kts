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
    implementation("com.dorkbox:SystemTray:4.4")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.happwner.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Happwner PC"
            packageVersion = "0.1.0"
            description = "Local and home-network proxy subscription server"
            vendor = "Happwner contributors"
            modules("java.base", "java.desktop", "java.net.http", "jdk.httpserver", "java.logging")
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
