import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    application
}

val gdxVersion: String by project

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

tasks.withType<JavaCompile> { options.release.set(11) }

application {
    mainClass.set("com.imageblotter.balance.desktop.DesktopLauncherKt")
    applicationDefaultJvmArgs = listOf("-XstartOnFirstThread").filter {
        System.getProperty("os.name").contains("Mac")
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    runtimeOnly("com.badlogicgames.gdx:gdx-bullet-platform:$gdxVersion:natives-desktop")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
}
