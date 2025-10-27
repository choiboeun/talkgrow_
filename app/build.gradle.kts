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


        // ▼ 추가: 앱과 unityLibrary의 ABI를 정확히 맞춘다
        ndk {
            abiFilters.clear()
            abiFilters += listOf("arm64-v8a")
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
        debug { ndk { abiFilters += listOf("arm64-v8a") } }
    }

    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

    }
    kotlinOptions { jvmTarget = "17" }




    buildFeatures {
        viewBinding = true
    }

    // Unity 데이터 파일 압축 금지 (라이브러리 + 앱 모두에 넣어두면 안전)
    androidResources {
        noCompress += listOf(
            ".unity3d",
            ".ress",
            ".resource",
            ".obb",
            ".bundle",
            ".unityexp",
            "tflite",
            "task",
            "json",
            "bin"
        )
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/AL2.0", "META-INF/LGPL2.1")
        }

    }

    // ★ 추가: Unity .so 로딩 호환(신규 Android에서도 안전)
    packagingOptions {
        // 네이티브 so 충돌 회피
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf(
                "**/libil2cpp.so",
                "**/libmain.so",
                "**/libunity.so",
                "**/lib_burst_generated.so",
                "**/libc++_shared.so" // TF/CameraX/Unity가 중복으로 넣는 대표 so
            )
        }

        // 리소스(META-INF 등) 중복 제거
        resources {
            excludes += listOf(
                // 라이선스/메타데이터
                "META-INF/DEPENDENCIES",
                "META-INF/DEPENDENCIES.*",
                "META-INF/LICENSE",
                "META-INF/LICENSE.*",
                "META-INF/NOTICE",
                "META-INF/NOTICE.*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.version",
                "META-INF/proguard/**",

                // Kotlin/AndroidX 메타
                "META-INF/*.kotlin_module",
                "META-INF/androidx.*.version",
                "META-INF/com.android.tools/**",

                // 가끔 중복나는 인덱스
                "META-INF/INDEX.LIST"
            )
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
    implementation(project(":unityLibrary"))

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
