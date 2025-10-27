plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.talkgrow_"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.talkgrow_"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { ndk { abiFilters += listOf("arm64-v8a") } }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { viewBinding = true }

    androidResources { noCompress += listOf("tflite", "task", "json", "bin") }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

val cameraxVersion = "1.3.4"

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // CameraX
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")

    // MediaPipe Tasks Vision (신형 API)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // 강제 버전 통일(혹시 모를 끼어듦 방지)
    constraints {
        implementation("com.google.mediapipe:tasks-core:0.10.14") { because("API 시그니처 통일") }
        implementation("com.google.mediapipe:framework:0.10.14") { because("API 시그니처 통일") }
    }

    // TFLite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// mediapipe 계열 강제 통일(compile/runtime 모두)
configurations.configureEach {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "com.google.mediapipe") {
                useVersion("0.10.14")
            }
        }
        force(
            "com.google.mediapipe:tasks-vision:0.10.14",
            "com.google.mediapipe:tasks-core:0.10.14",
            "com.google.mediapipe:framework:0.10.14"
        )
    }
}
