import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.mappls.services.android")
    id("com.google.dagger.hilt.android")
}

// Dynamic release versioning loader
val versionPropsFile = project.rootProject.file("version.properties")
var vCode = 1
var vName = "1.0.0"
if (versionPropsFile.exists()) {
    val versionProps = Properties()
    versionPropsFile.inputStream().use { versionProps.load(it) }
    vCode = versionProps.getProperty("VERSION_CODE", "1").toInt()
    vName = versionProps.getProperty("VERSION_NAME", "1.0.0")
}

android {
    namespace = "com.nexus.portal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nexus.portal"
        minSdk = 26
        targetSdk = 35
        versionCode = vCode
        versionName = vName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xcontext-receivers"
        )
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.whenTaskAdded {
    if (name.contains("AarMetadata")) {
        enabled = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.4.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Drag & Drop Reordering
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")
    
    // Proper iOS-style Liquid Glass (Haze)
    implementation("dev.chrisbanes.haze:haze:0.7.3")
    
    // CameraX for Video Overlay Recording
    val cameraVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-extensions:$cameraVersion")
}

dependencies {
    implementation("androidx.camera:camera-view:1.3.1")
    
    // WorkManager (Background Updates)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Biometric & Fragment KTX (Biometric Login Support)
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // Security (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Google Sign-In (OAuth 2.0)
    implementation("com.google.android.gms:play-services-auth:21.1.0")
    
    // Location and Maps
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation(platform("com.mappls.sdk:mappls-bom:2.0.1")) // Standard BoM version
    implementation("com.mappls.sdk:mappls-android-sdk")
    implementation("com.mappls.sdk:annotation-plugin") // For Polylines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    
    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.55")
    ksp("com.google.dagger:hilt-android-compiler:2.55")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")
}


