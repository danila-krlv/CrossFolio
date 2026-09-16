import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    androidLibrary {
        namespace = "com.crossfolio.common"
        compileSdk = 37
        minSdk = 23
    }

    val xcf = XCFramework("Common")

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "Common"
            isStatic = true
            xcf.add(this)
        }
    }

    val macosTarget = macosArm64()
    macosTarget.binaries.all {
        linkerOpts("-lsqlite3")
    }
    macosTarget.binaries.framework {
        baseName = "Common"
        isStatic = true
        xcf.add(this)
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.room.runtime)
        }

        androidMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }

        iosMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }

        macosMain.dependencies {
            implementation(libs.androidx.sqlite.framework)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspMacosArm64", libs.androidx.room.compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
