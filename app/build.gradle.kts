plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.khumam.gbaduo"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    // Public builds do not reuse a developer's debug key for release APKs.
    // Supply all four variables to sign, otherwise assembleRelease is unsigned.
    val signingNames = listOf("PLUS_KEYSTORE_PATH", "PLUS_STORE_PASSWORD", "PLUS_KEY_ALIAS", "PLUS_KEY_PASSWORD")
    val signingValues = signingNames.map { providers.environmentVariable(it).orNull }
    require(signingValues.all { it.isNullOrBlank() } || signingValues.all { !it.isNullOrBlank() }) {
        "Set all four PLUS signing environment variables, or leave all unset for an unsigned release."
    }
    signingConfigs {
        if (signingValues.all { !it.isNullOrBlank() }) create("plusRelease") {
            storeFile = file(requireNotNull(signingValues[0]))
            storePassword = signingValues[1]
            keyAlias = signingValues[2]
            keyPassword = signingValues[3]
        }
    }

    defaultConfig {
        applicationId = "com.khumam.gbaduo.plus"
        minSdk = 30
        targetSdk = 35
        versionCode = 10
        versionName = "0.1.7-plus.2"

        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    // Plus-only implementations live outside the upstream application tree.
    // One compilation unit preserves internal visibility without duplicate classes.
    sourceSets.getByName("main") {
        java.srcDir("../plus/src/main/java")
        assets.srcDir("../plus/src/main/assets")
        res.srcDir("../plus/src/main/res")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("plusRelease")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true // RetroAchievements' User-Agent carries the version
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Compose UI tests (PackScreenshotTest) launch a ComponentActivity from the merged test manifest.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // PackScreenshotTest re-records its reference images with -PrecordScreenshots.
            test.systemProperty("gbaduo.recordScreenshots", project.findProperty("recordScreenshots") != null)
            // Files the screenshot/renderer tests read directly, so editing them re-runs the tests.
            test.inputs.dir("../docs-packs/examples")
            test.inputs.dir("src/test/screenshots")
            test.inputs.file("src/main/assets/companion/default.css")
        }
    }
}

dependencies {
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")

    // Companion screen (second display) UI.
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // Hosting Compose in a Presentation (no Activity to provide these).
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    // Runs WidgetRendererTest against real Android views.
    testImplementation("org.robolectric:robolectric:4.14.1")
}
