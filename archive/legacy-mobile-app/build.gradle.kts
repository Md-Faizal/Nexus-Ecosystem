buildscript {
    repositories {
        maven(url = "https://maven.mappls.com/repository/mappls/")
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.mappls.services:mappls-services:1.0.0")
    }
}

plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("com.google.dagger.hilt.android") version "2.55" apply false
}

allprojects {
    // Workaround for Windows MAX_PATH issues with AAPT2 and KSP.
    // Keeps the build output path exactly as short as it was when the folder was named "APP".
    layout.buildDirectory.set(file("${rootProject.projectDir}/b/${project.name}"))
}
