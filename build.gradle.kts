// Plugins are put on one shared classpath here: the Kotlin Android plugin has to see the Android Gradle
// plugin, which fails when the two are loaded from different places. The Android plugin is only added
// when the :android module is part of the build (see settings.gradle.kts).
buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
        if (rootProject.findProject(":android") != null) {
            classpath("com.android.tools.build:gradle:8.9.1")
        }
    }
}
