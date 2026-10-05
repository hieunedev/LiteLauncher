plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.lite.launcher"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.lite.launcher"
        minSdk = 24; targetSdk = 34
        versionCode = 1; versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true; isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.recyclerview:recyclerview:1.3.2") }
