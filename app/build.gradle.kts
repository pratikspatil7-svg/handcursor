plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.handcursor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.handcursor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // MediaPipe's .task model file must NOT be compressed
    androidResources {
        noCompress += "task"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // CameraX
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")

    // MediaPipe Tasks Vision (Hand Landmarker)
    // Check https://developers.google.com/mediapipe for the latest version before building
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Gives the Service a LifecycleOwner, which CameraX's bindToLifecycle needs
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")
}
