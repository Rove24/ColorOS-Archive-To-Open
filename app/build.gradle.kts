plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.andrea_lyz.archivetoopen"
    // The module only touches framework classes by reflection, so it compiles against the
    // newest platform this AGP understands. (The locally installed android-37.0 ships SDK
    // XML v4, which AGP 8.13.2 rejects.)
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.andrea_lyz.archivetoopen"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
    }

    buildFeatures {
        buildConfig = false
    }

    buildTypes {
        release {
            // The module is a single class plus the libxposed entry metadata; keeping the
            // obfuscator out of the way removes one more thing that could rename the entry point.
            isMinifyEnabled = false
            isShrinkResources = false
            // Signed with the local debug key so the APK installs straight from a file manager.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    // Modern Xposed API 102 must stay compile-only; the framework provides it at runtime.
    compileOnly(project(":libxposed-api"))
    compileOnly("androidx.annotation:annotation:1.10.0")
}
