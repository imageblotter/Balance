import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
}

val gdxVersion: String by project
val abis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

val natives by configurations.creating

android {
    namespace = "com.imageblotter.balance.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.imageblotter.balance"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets["main"].apply {
        assets.srcDir(rootProject.file("assets"))
        jniLibs.srcDir(layout.buildDirectory.dir("natives").get().asFile)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    for (abi in abis) {
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-bullet-platform:$gdxVersion:natives-$abi")
    }
}

// libGDX ships its native libraries inside jars; unpack them where the Android packaging expects them.
val copyAndroidNatives by tasks.registering(Copy::class) {
    into(layout.buildDirectory.dir("natives"))
    for (abi in abis) {
        from(provider { natives.files.filter { it.name.endsWith("natives-$abi.jar") }.map { zipTree(it) } }) {
            include("*.so")
            into(abi)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(copyAndroidNatives) }

// libGDX's Android backend pulls in a recent androidx.core, which insists on compileSdk 36; 1.15 builds against 35.
configurations.configureEach {
    resolutionStrategy.force("androidx.core:core:1.15.0")
}
