plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.onesecclone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.onesecclone"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Add environment variables as build config fields
        buildConfigField("String", "SERVER_URL", "\"${System.getenv("SERVER_URL") ?: ""}\"")
        buildConfigField("String", "API_KEY", "\"${System.getenv("API_KEY") ?: ""}\"")
    }

    buildTypes {
        debug {
            // Development configuration - using EC2 server
            buildConfigField("String", "SERVER_URL", "\"${System.getenv("DEV_SERVER_URL") ?: "http://54.186.25.9:8080/"}\"")
            buildConfigField("String", "BUILD_ENVIRONMENT", "\"development\"")
        }

        create("staging") {
            initWith(getByName("debug"))
            buildConfigField("String", "SERVER_URL", "\"${System.getenv("STAGING_SERVER_URL") ?: "https://staging.your-domain.com/"}\"")
            buildConfigField("String", "BUILD_ENVIRONMENT", "\"staging\"")
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "SERVER_URL", "\"${System.getenv("PROD_SERVER_URL") ?: "https://your-domain.com/"}\"")
            buildConfigField("String", "BUILD_ENVIRONMENT", "\"production\"")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true  // Enable BuildConfig generation
        dataBinding = true  // Enable data binding for test activity
    }
}

dependencies {
    // Add desugaring support for Java 8 Time API
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Networking dependencies for server communication
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
