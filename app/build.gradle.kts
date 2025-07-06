plugins {
    alias(libs.plugins.android.application)   // 안드로이드 애플리케이션 플러그인 적용
    alias(libs.plugins.kotlin.android)        // 코틀린 안드로이드 플러그인 적용
}

android {
    namespace = "com.talkgrow_"               // 앱 패키지 네임스페이스
    compileSdk = 35                           // 컴파일 SDK 버전

    defaultConfig {
        applicationId = "com.talkgrow_"      // 앱 고유 아이디
        minSdk = 24                          // 최소 지원 SDK 버전
        targetSdk = 35                       // 타겟 SDK 버전
        versionCode = 1                      // 버전 코드 (숫자)
        versionName = "1.0"                  // 버전 이름 (문자열)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"  // 테스트 러너 설정
    }

    buildTypes {
        release {
            isMinifyEnabled = false           // 릴리즈 빌드 시 코드 축소 사용 여부
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),  // 프로가드 기본 파일
                "proguard-rules.pro"                                      // 커스텀 프로가드 규칙 파일
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8    // 자바 소스 호환 버전
        targetCompatibility = JavaVersion.VERSION_1_8    // 자바 타겟 호환 버전
    }

    kotlinOptions {
        jvmTarget = "1.8"                                 // 코틀린 JVM 타겟 버전
    }
    buildFeatures {
        viewBinding = true                                // 뷰 바인딩 활성화
    }
}

// CameraX 라이브러리 버전 정의
val cameraxVersion = "1.3.0"

dependencies {
    implementation(libs.androidx.core.ktx)               // 코틀린 확장 라이브러리
    implementation(libs.androidx.appcompat)               // 앱호환 라이브러리
    implementation(libs.material)                         // 머티리얼 디자인 라이브러리
    implementation(libs.androidx.activity)                // 액티비티 라이브러리
    implementation(libs.androidx.constraintlayout)        // 제약 레이아웃 라이브러리

    testImplementation(libs.junit)                        // 단위 테스트용 JUnit
    androidTestImplementation(libs.androidx.junit)        // 안드로이드 테스트용 JUnit
    androidTestImplementation(libs.androidx.espresso.core) // UI 테스트용 Espresso

    /* CameraX 의존성 */
    implementation("androidx.camera:camera-core:$cameraxVersion")         // CameraX 핵심 라이브러리
    implementation("androidx.camera:camera-camera2:$cameraxVersion")      // Camera2 API 지원
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")    // 생명주기 연동 라이브러리
    implementation("androidx.camera:camera-video:$cameraxVersion")        // 비디오 녹화 지원 라이브러리
    implementation("androidx.camera:camera-view:$cameraxVersion")         // 카메라 뷰 지원 라이브러리
    implementation("androidx.camera:camera-extensions:$cameraxVersion")   // 카메라 확장 기능 라이브러리

    implementation("com.google.mediapipe:tasks-vision:0.10.7")            // Mediapipe Vision Tasks 라이브러리
}

