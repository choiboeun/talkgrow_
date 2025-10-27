// <project root>/build.gradle.kts

// 1) Kotlin/AGP 플러그인 classpath (buildscript에는 repositories 허용됨)
buildscript {
    val kotlinVersion = "1.9.23"

    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

// 2) 버전 카탈로그 alias는 그대로 유지
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// ❌ 여기엔 repositories 블록 두지 말 것 (에러 원인)

// 3) Unity 스트리밍 에셋 경로
extra["unityStreamingAssets"] =
    File(project(":unityLibrary").projectDir, "src/main/assets")
        .absolutePath.replace("\\", "/")

