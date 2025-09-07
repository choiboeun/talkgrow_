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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }

    // .tflite, .task 파일 압축 금지
    androidResources {
        noCompress += listOf("tflite", "task")
    }
}

// CameraX 버전
val cameraxVersion = "1.3.0"

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // CameraX
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")

    // MediaPipe Tasks Vision (Hand, Pose, Face Landmarker)
    implementation("com.google.mediapipe:tasks-vision:0.10.7")

    // Lifecycle + Coroutine
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // TensorFlow Lite (모델 연결용)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    // 선택적으로 GPU delegate / SelectOps 등도 추가 가능
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.14.0")

    // Retrofit / Gson (네 기존 네트워크 코드용)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}
