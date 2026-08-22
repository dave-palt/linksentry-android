plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.dav3.linksentry"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dav3.linksentry"
        minSdk = 26
        targetSdk = 37
        versionCode = (System.currentTimeMillis() / 1000).toInt()
        versionName = "0.1.0"

        // Git SHA for build traceability (config-cache safe)
        buildConfigField(
            "String",
            "GIT_SHA",
            "\"${providers.exec {
                commandLine("git", "rev-parse", "HEAD")
            }.standardOutput.asText.getOrElse("").trim().take(40)}\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            // Populated from environment or local.properties for local builds
            // CI uses GHA secrets (see docs/ci-cd.md)
            val storeFilePath = providers.environmentVariable("SIGNING_STORE_FILE").orNull
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
            }
        }
        create("sharedDebug") {
            // A committed debug keystore so local and CI dev builds share the
            // same signature — clean upgrades between local installs and GitHub
            // dev releases without INSTALL_FAILED_UPDATE_INCOMPATIBLE.
            // Safe to commit: debug credentials are public and this only signs
            // the .debug applicationIdSuffix variant, never releases.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.findByName("sharedDebug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Allow incremental localization — new strings may not be translated yet
        disable += "MissingTranslation"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
roborazzi {
    generateComposePreviewRobolectricTests {
        enable = true
        packages = listOf("com.dav3.linksentry")
        includePrivatePreviews = true
        robolectricConfig =
            mapOf(
                "sdk" to "[34]",
                "application" to "android.app.Application::class",
            )
    }
    outputDir.set(file("build/outputs/roborazzi"))
}

dependencies {
    // Kotlin stdlib — force-consistent version to prevent transitive conflicts
    // (Room/KSP pull in old stdlib; AGP 9.1.0 binary-store serialization fails
    // on the constraint graph if versions are inconsistent)
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:${libs.versions.kotlin.get()}"))

    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Room for local history database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Logging
    implementation(libs.timber)

    // NOTE: deliberately absent — LinkSentry must never gain network capability:
    //   no INTERNET permission, no Retrofit/OkHttp/Coil/browser/WorkManager.
    //   Enforced by .github/workflows/dev-build.yml guardrails step.

    // JVM unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)

    // Screenshot testing (JVM — renders Compose Previews without an emulator)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.composable.preview.scanner)
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:${libs.versions.roborazzi.get()}")
    // Compose tooling + preview support needed in test for @Preview rendering
    testImplementation(libs.androidx.compose.ui.tooling.preview)
    testImplementation("androidx.compose.material3:material3")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
}
