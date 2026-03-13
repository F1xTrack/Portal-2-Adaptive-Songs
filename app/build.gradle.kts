plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.f1xtrack.portal2adaptivesongs"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.f1xtrack.portal2adaptivesongs"
        minSdk = 30
        targetSdk = 34
        versionCode = 4
        versionName = "1.3.2"
    }
    buildFeatures {
        viewBinding = true
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
    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        disable += setOf(
            "AndroidGradlePluginVersion",
            "DrawAllocation",
            "GradleDependency",
            "IconDuplicates",
            "IconLauncherShape",
            "IconLocation",
            "KaptUsageInsteadOfKsp",
            "NewerVersionAvailable",
            "NotifyDataSetChanged",
            "OldTargetApi",
            "Overdraw",
            "PluralsCandidate",
            "UnnecessaryArrayInit",
            "UnusedResources",
            "UseKtx",
            "UseTomlInstead"
        )
    }
}

dependencies {

    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.preference.ktx)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    // Maps
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    // Room (database)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    testImplementation(libs.junit)
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
