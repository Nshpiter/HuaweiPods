plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.lsplugin.resopt)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.parcelize)
    alias(libs.plugins.compose.compiler)
}

val moduleBuildTimestamp = System.currentTimeMillis()
val moduleBuildIdBase = "b$moduleBuildTimestamp"

android {
    namespace = "moe.chenxy.huaweipods"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "moe.chenxy.huaweipods"
        minSdk = 35
        targetSdk = 36
        versionCode = 15
        versionName = "1.7.1"
        buildConfigField("long", "BUILD_TIMESTAMP", moduleBuildTimestamp.toString())
    }

    buildTypes {
        debug {
            val moduleBuildId = "$moduleBuildIdBase-debug"
            buildConfigField("String", "MODULE_BUILD_ID", "\"$moduleBuildId\"")
            manifestPlaceholders["moduleBuildId"] = moduleBuildId
            isDebuggable = true
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            val moduleBuildId = "$moduleBuildIdBase-release"
            buildConfigField("String", "MODULE_BUILD_ID", "\"$moduleBuildId\"")
            manifestPlaceholders["moduleBuildId"] = moduleBuildId
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    dependenciesInfo.includeInApk = false

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/**.version"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "okhttp3/**"
            excludes += "kotlin/**"
            excludes += "org/**"
            excludes += "**.properties"
            excludes += "**.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(JavaVersion.VERSION_23.majorVersion)
    }
}

kotlin {
    jvmToolchain(JavaVersion.VERSION_23.majorVersion.toInt())
}

configurations.configureEach {
    exclude(group = "androidx.lifecycle", module = "lifecycle-viewmodel-ktx")
}

dependencies {
    implementation(libs.coreKtx)
    compileOnly(libs.libxposedApi)
    implementation(libs.libxposedService)
    implementation(libs.kotlinx.serialization.json)
    testImplementation("junit:junit:4.13.2")

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)

    // MIUIX
    implementation(libs.miuix)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.navigation3.ui)

    // Navigation3
    implementation(libs.navigation3.runtime)

    // HyperOS Focus Island API
    implementation(libs.focus.api)
}
