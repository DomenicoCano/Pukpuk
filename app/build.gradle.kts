import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.pukpuk"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pukpuk"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // credentials come from a keystore.properties outside git (see README), or from env vars in CI
            val props = Properties().apply {
                val f = rootProject.file("keystore.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            val path = props.getProperty("storeFile") ?: System.getenv("PUKPUK_STORE_FILE")
            if (path != null) {
                storeFile = file(path)
                storePassword = props.getProperty("storePassword") ?: System.getenv("PUKPUK_STORE_PASSWORD")
                keyAlias = props.getProperty("keyAlias") ?: System.getenv("PUKPUK_KEY_ALIAS")
                keyPassword = props.getProperty("keyPassword") ?: System.getenv("PUKPUK_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
// Build the Rust engine (chimera-android) into jniLibs via cargo-ndk before packaging.
val chimeraSrc = file("${rootDir}/rust")
val buildChimera = tasks.register<Exec>("buildChimera") {
    workingDir = chimeraSrc
    commandLine(
        "cargo", "ndk", "-t", "arm64-v8a", "-t", "armeabi-v7a", "-t", "x86_64",
        "-o", file("src/main/jniLibs").absolutePath, "build", "--release", "-p", "chimera-android",
    )
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach { dependsOn(buildChimera) }
