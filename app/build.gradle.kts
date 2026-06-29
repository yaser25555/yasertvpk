plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.yassertv"
  ndkVersion = "27.0.12077973"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.yassertv"
    minSdk = 21
    targetSdk = 35
    versionCode = 5
    versionName = "1.4"

    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
    }

    externalNativeBuild {
      cmake {
        cFlags("-O2", "-Wall")
        abiFilters("arm64-v8a", "armeabi-v7a", "x86_64")
      }
    }
  }

  signingConfigs {
    create("release") {
      storeFile = file("yassertv.jks")
      storePassword = "yassertv123"
      keyAlias = "yassertv"
      keyPassword = "yassertv123"
    }
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  buildFeatures {
    prefab = true
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.leanback:leanback:1.0.0")
  implementation("androidx.recyclerview:recyclerview:1.3.2")

  implementation("androidx.media3:media3-exoplayer:1.5.1")
  implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
  implementation("androidx.media3:media3-ui:1.5.1")
  implementation("androidx.media3:media3-datasource:1.5.1")

  // IJKPlayer Java source is included directly in the project

  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.google.code.gson:gson:2.11.0")
  implementation("com.github.bumptech.glide:glide:4.16.0")
}
