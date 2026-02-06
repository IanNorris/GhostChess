plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()
    js(IR) {
        browser()
        binaries.executable()
    }
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
