import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

android {
    namespace = "com.rui.composes.svga"
    compileSdk = 36
    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 关键：混淆规则，确保库在混淆后仍能正常运行
        consumerProguardFiles("consumer-rules.pro")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.github.TroyLi-Dev"
            artifactId = "compose-svga"
            version = project.version as String? ?: "1.0.0"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // 基础核心
    implementation(libs.androidx.core.ktx)

    // Compose 核心：仅包含绘制和基础组件，不包含 Material 样式
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation) // 用于 Box, 基础动画
    implementation(libs.androidx.compose.runtime)    // 用于 State, LaunchedEffect

    // SVGA 核心依赖
    implementation(libs.wire.runtime) // Protobuf 解析
    implementation(libs.okio) // 流式解码基础
}
