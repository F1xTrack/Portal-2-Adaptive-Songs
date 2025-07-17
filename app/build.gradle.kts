// Конфигурация сборки для приложения Portal 2 Adaptive Songs
plugins {
    alias(libs.plugins.android.application)  // Плагин для Android приложений
    alias(libs.plugins.kotlin.android)       // Плагин для Kotlin
}

android {
    namespace = "com.f1xtrack.portal2adaptivesongs"
    compileSdk = 34  // Версия SDK для компиляции
    
    defaultConfig {
        applicationId = "com.f1xtrack.portal2adaptivesongs"  // Уникальный идентификатор приложения
        minSdk = 30      // Минимальная версия Android (API 30 = Android 11)
        targetSdk = 33   // Целевая версия Android (API 33 = Android 13)
        versionCode = 2  // Код версии для Play Store
        versionName = "1.1"  // Название версии
    }
    
    buildFeatures {
        viewBinding = true  // Включение View Binding для удобной работы с UI
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // Отключение минификации для отладки
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11  // Версия Java для компиляции
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"  // Целевая версия JVM для Kotlin
    }
}

dependencies {
    // Google Play Services для работы с местоположением
    implementation("com.google.android.gms:play-services-location:21.2.0")
    
    // Основные Android библиотеки
    implementation(libs.androidx.core.ktx)      // Kotlin расширения для Android
    implementation(libs.androidx.appcompat)     // Совместимость с разными версиями Android
    implementation(libs.material)               // Material Design компоненты
    
    // ExoPlayer для воспроизведения аудио (альтернатива MediaPlayer)
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    
    // Библиотеки для тестирования
    testImplementation(libs.junit)              // Unit тесты
    androidTestImplementation(libs.androidx.junit)      // Android тесты
    androidTestImplementation(libs.androidx.espresso.core)  // UI тесты
}