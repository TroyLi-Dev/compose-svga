import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.squareup.wire") version "5.4.0"
    id("maven-publish")
}

android {
    namespace = "com.rui.compose.svga"
    compileSdk = 36
    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    publishing {
        singleVariant("release")
    }
}

wire {
    sourcePath {
        srcDir("src/main/proto")
    }
    kotlin {
        android = true
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.github.TroyLi-Dev"
            artifactId = "compose-svga"
            version = project.findProperty("version")?.toString()?.takeIf { it != "unspecified" } ?: "1.0.1-rc01"
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
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)

    // SVGA 核心依赖
    implementation(libs.wire.runtime)
    implementation(libs.okio)

}
