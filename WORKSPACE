workspace(name = "talkgrow")

load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@rules_android//:workspace.bzl", "android_rules_dependencies")

# bazel_skylib 불러오기 (미디어파이프 필수)
http_archive(
    name = "bazel_skylib",
    sha256 = "74d544d96f4a5bb630d465ca8bbcfe231e3594e5aae57e1edbf17a6eb3ca2506",
    urls = ["https://github.com/bazelbuild/bazel-skylib/releases/download/1.3.0/bazel-skylib-1.3.0.tar.gz"],
)

bazel_skylib_workspace()

# android rules
http_archive(
    name = "rules_android",
    sha256 = "d0ef3936d8cf1c469244d67896bcd18d3d4d9b4e5e914d924313878f3f49b4e2",
    urls = ["https://github.com/bazelbuild/rules_android/releases/download/0.10.0/rules_android-0.10.0.tar.gz"],
)

android_rules_dependencies()

# Android SDK & NDK 설정 (로컬 경로에 맞게 수정하세요)
android_sdk_repository(
    name = "androidsdk",
    api_level = 30,
    build_tools_version = "30.0.3",
    path = "C:/Users/jkj08/AppData/Local/Android/Sdk",
)

android_ndk_repository(
    name = "androidndk",
    api_level = 21,
    path = "C:/Users/jkj08/AppData/Local/Android/Sdk/ndk/25.1.8937393",
)

# MediaPipe 라이브러리 외부 저장소 추가 (원하는 버전과 sha256 확인 필요)
http_archive(
    name = "mediapipe",
    sha256 = "FF4A2A85D0AC0C73FF1ACDF5CEDA47CB3640566E0430E056C7F12E44CB5C81BD",
    strip_prefix = "mediapipe-0.10.24",
    urls = ["https://github.com/google/mediapipe/archive/refs/tags/v0.10.24.tar.gz"],
)
