plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidApplication)
}

import java.net.HttpURLConnection
import java.net.URI

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(project(":shared"))
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.activity.compose)
            implementation("com.google.mediapipe:tasks-genai:0.10.27")
        }
    }
}

// Download and cache the Gemma model at build time
val modelCacheDir = file(System.getProperty("user.home") + "/.cache/ghostchess")
val modelFileName = "gemma3-1b-it-int4.task"
val cachedModel = file("$modelCacheDir/$modelFileName")
val assetsModel = file("src/androidMain/assets/$modelFileName")

val downloadGemmaModel by tasks.registering {
    description = "Downloads the Gemma model if not cached, then links to assets"
    outputs.file(assetsModel)

    doLast {
        modelCacheDir.mkdirs()
        file("src/androidMain/assets").mkdirs()

        if (!cachedModel.exists()) {
            // Read HF token from .env files (check project root, workspace, or home)
            val envFile = listOf(
                rootProject.file(".env"),
                rootProject.file("../.env"),
                file(System.getProperty("user.home") + "/.env")
            ).firstOrNull { it.exists() }

            val token = envFile?.readLines()
                ?.firstOrNull { it.startsWith("HUGGING_FACE=") }
                ?.substringAfter("HUGGING_FACE=")
                ?.trim()
                ?: throw GradleException(
                    "HUGGING_FACE token not found. Add HUGGING_FACE=hf_... to a .env file " +
                    "(project root, parent dir, or ~/.env)"
                )

            logger.lifecycle("Downloading Gemma model to cache (~554MB)...")
            val url = URI(
                "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/$modelFileName"
            ).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 30000
            conn.readTimeout = 120000

            if (conn.responseCode != 200) {
                throw GradleException("Failed to download model: HTTP ${conn.responseCode}")
            }

            val tmpFile = file("$modelCacheDir/$modelFileName.tmp")
            conn.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
            }
            tmpFile.renameTo(cachedModel)
            logger.lifecycle("Model cached at: $cachedModel")
        } else {
            logger.lifecycle("Using cached model: $cachedModel")
        }

        // Copy to assets if not already there (or outdated)
        if (!assetsModel.exists() || assetsModel.length() != cachedModel.length()) {
            cachedModel.copyTo(assetsModel, overwrite = true)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadGemmaModel)
}

android {
    namespace = "chess.simulator"
    compileSdk = 35

    androidResources {
        noCompress += "task"
    }

    defaultConfig {
        applicationId = "chess.simulator"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
