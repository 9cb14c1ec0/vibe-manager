import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation("io.github.compose-fluent:fluent:v0.1.0")
            implementation("io.github.compose-fluent:fluent-icons-extended:v0.1.0")
            implementation("dev.snipme:kodeview:0.9.0")
            implementation("com.mikepenz:multiplatform-markdown-renderer:0.39.0")
            implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.39.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation("com.agentclientprotocol:acp:0.20.1")
            implementation("org.slf4j:slf4j-simple:2.0.16")
            implementation("com.risaboss:bossterm-compose:1.0.92")
            implementation("com.risaboss:bossterm-core:1.0.92")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.oss.vibemanager.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "VibeManager"
            packageVersion = "1.0.0"
        }
    }
}
