import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

android {
    namespace = "io.github.anilbeesetti.nextlib.mediainfo"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {

        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }

        ndk {
            abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Gradle task to setup ffmpeg
val ffmpegSetup by tasks.registering(Exec::class) {
    val sdkDir = androidComponents.sdkComponents.sdkDirectory
    val ndkDir = androidComponents.sdkComponents.ndkDirectory
    workingDir = file("../ffmpeg")
    doFirst {
        environment("ANDROID_SDK_HOME", sdkDir.get().asFile.absolutePath)
        environment("ANDROID_NDK_HOME", ndkDir.get().asFile.absolutePath)
        environment("ANDROID_CMAKE_VERSION", libs.versions.cmake.get())
    }
    commandLine("bash", "setup.sh")
}

// Ensure FFmpeg outputs exist before compiling native code.
// We depend on the media3ext task to avoid running `setup.sh` concurrently in parallel builds.
tasks.preBuild.dependsOn(project(":media3ext").tasks.named("ffmpegSetup"))

dependencies {
    implementation(libs.androidx.annotation)
}
