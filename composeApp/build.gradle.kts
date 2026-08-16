import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val appVersion = "1.4.0"

// Usage: ./gradlew :composeApp:run -PbakedKeys="vless://key1#Name1,vless://key2#Name2"
val bakedKeys: String = findProperty("bakedKeys")?.toString() ?: ""

val generateVersion by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/version")
    outputs.dir(outputDir)
    inputs.property("appVersion", appVersion)
    doLast {
        val file = outputDir.get().file("green/Version.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package green\n\nconst val APP_VERSION = \"$appVersion\"\n")
    }
}

val generateBakedKeys by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/bakedKeys")
    outputs.dir(outputDir)
    inputs.property("bakedKeys", bakedKeys)
    doLast {
        val file = outputDir.get().file("baked_keys.txt").asFile
        file.parentFile.mkdirs()
        // store one URI per line
        file.writeText(bakedKeys.split(",").map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n"))
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            resources.srcDir(layout.buildDirectory.dir("generated/bakedKeys"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/version"))
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

tasks.named("desktopProcessResources") { dependsOn(generateBakedKeys) }
tasks.named("compileKotlinDesktop") { dependsOn(generateVersion) }

compose.desktop {
    application {
        mainClass = "green.MainKt"

        jvmArgs(
            "-Xmx256m",                        // cap heap — prevents OOM on low-RAM machines
            "-Dsun.java2d.nodraw=true",         // disable DirectDraw (avoids D3D init crash in some drivers)
            "-Dsun.java2d.noddraw=true",        // belt-and-suspenders: also disable DDraw
        )

        nativeDistributions {
            val os = org.gradle.internal.os.OperatingSystem.current()
            targetFormats(*buildList {
                if (os.isMacOsX) add(TargetFormat.Dmg)
                if (os.isWindows) add(TargetFormat.Msi)
            }.toTypedArray())
            packageName = "Green"
            packageVersion = appVersion
            description = "Green VPN Client"

            macOS {
                bundleID = "app.green.desktop"
                iconFile.set(project.file("src/desktopMain/resources/icons/icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icons/icon.ico"))
                upgradeUuid = "3D5A2C8F-1B4E-4F9A-8E3D-7C6B2A1F0E9D"
                menuGroup = "Green"
                perUserInstall = true
            }
        }
    }
}
