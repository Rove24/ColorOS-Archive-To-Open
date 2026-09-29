plugins {
    id("com.android.library")
}

// The reference API 102 sources are vendored under src/api/java (see src/api/README.md). Compiling
// them here keeps a single source of truth for the module API, avoids a Maven download at build
// time, and keeps the repository buildable on its own.
val apiSourceDir = file("src/api/java")
val annotationSourceDir = file("src/annotation/java")

android {
    namespace = "io.github.libxposed.api"
    compileSdk = 35
    androidResources.enable = false

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDir(apiSourceDir)
            java.srcDir(annotationSourceDir)
        }
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.10.0")
}

tasks.register("checkApiSources") {
    doFirst {
        check(apiSourceDir.isDirectory) {
            "libxposed API sources not found: ${apiSourceDir.absolutePath}"
        }
    }
}
