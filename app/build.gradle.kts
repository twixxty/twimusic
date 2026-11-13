import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    val appId = "com.twixxty.twimusic"

    namespace = "app.vitune.android"
    compileSdk = 37

    val abis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
    val cmakeVersion = "4.1.2"
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = appId

        minSdk = 24
        targetSdk = 37

        versionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: 27
        versionName = System.getenv("ANDROID_VERSION_NAME") ?: project.version.toString()
    }

    // One APK per ABI plus a universal APK. ndk.abiFilters must not be set together with ABI splits
    // (AGP rejects the combination), so the ABI list lives here, and 32-bit x86 / riscv64 libraries
    // are kept out of the universal APK via packaging.jniLibs.excludes below.
    splits {
        abi {
            isEnable = true
            reset()
            include(*abis.toTypedArray())
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("signing/release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "twimusic"
                keyAlias = System.getenv("KEY_ALIAS") ?: "twimusic"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "twimusic"
            }
        }
        create("ci") {
            storeFile = System.getenv("ANDROID_NIGHTLY_KEYSTORE")?.let { file(it) }
                ?: rootProject.file("signing/release.keystore").takeIf { it.exists() }
            storePassword = System.getenv("ANDROID_NIGHTLY_KEYSTORE_PASSWORD") ?: "twimusic"
            keyAlias = System.getenv("ANDROID_NIGHTLY_KEYSTORE_ALIAS") ?: "twimusic"
            keyPassword = System.getenv("ANDROID_NIGHTLY_KEYSTORE_PASSWORD") ?: "twimusic"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            manifestPlaceholders["appName"] = "TwiMusic Debug"
            signingConfigs.findByName("release")?.takeIf { it.storeFile?.exists() == true }?.let {
                signingConfig = it
            }
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appName"] = "TwiMusic"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.takeIf { it.storeFile?.exists() == true }?.let {
                signingConfig = it
            }
        }

        create("nightly") {
            initWith(getByName("release"))
            matchingFallbacks += "release"

            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-NIGHTLY"
            manifestPlaceholders["appName"] = "TwiMusic Nightly"
            signingConfig = signingConfigs.findByName("ci")
        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes.add("META-INF/CONTRIBUTORS.md")
            excludes.add("META-INF/*.md")
            excludes.add("META-INF/*.MD")
            excludes.add("META-INF/*.txt")
            excludes.add("META-INF/LICENSE*")
            excludes.add("META-INF/NOTICE*")
            excludes.add("META-INF/INDEX.LIST")
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/*")
            excludes.add("META-INF/**/*")
            pickFirsts.add("META-INF/**")
            merges.add("META-INF/services/**")
        }
        jniLibs {
            useLegacyPackaging = true
            excludes += setOf("lib/x86/**", "lib/riscv64/**", "lib/mips/**", "lib/mips64/**")
        }
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_5)

        freeCompilerArgs.addAll(
            "-Xconsistent-data-class-copy-visibility",
            "-Xstring-concat=inline"
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

composeCompiler {
    if (project.findProperty("enableComposeCompilerReports") == "true") {
        val dest = layout.buildDirectory.dir("compose_metrics")
        metricsDestination = dest
        reportsDestination = dest
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)

    implementation(projects.compose.persist)
    implementation(projects.compose.preferences)
    implementation(projects.compose.routing)
    implementation(projects.compose.reordering)

    implementation(fileTree(projectDir.resolve("vendor")))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.activity)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.shimmer)
    implementation(libs.compose.lottie)
    implementation(libs.compose.material3)

    implementation(libs.coil.compose)
    implementation(libs.coil.ktor)

    implementation(libs.palette)
    implementation(libs.monet)
    runtimeOnly(projects.core.materialCompat)

    implementation(libs.exoplayer)
    implementation(libs.exoplayer.workmanager)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.hls)
    implementation(libs.media)

    implementation(libs.workmanager)
    implementation(libs.workmanager.ktx)

    //noinspection LoginCredentials
    implementation(libs.credentials)
    //noinspection LoginCredentials
    implementation(libs.credentials.play)

    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.immutable)
    implementation(libs.kotlin.datetime)

    implementation(libs.room)
    ksp(libs.room.compiler)

    implementation(libs.log4j)
    implementation(libs.slf4j)
    implementation(libs.logback)

    implementation(projects.providers.github)
    implementation(projects.providers.innertube)
    implementation(project(":innertube-core"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.innertubex)
    implementation(libs.timber)
    implementation(libs.kuromoji)
    implementation(libs.tinypinyin)
    implementation(projects.providers.kugou)
    implementation(projects.providers.lrclib)
    implementation(projects.providers.piped)
    implementation(projects.providers.sponsorblock)
    implementation(projects.providers.translate)
    implementation(projects.core.data)
    implementation(projects.core.ui)
}
