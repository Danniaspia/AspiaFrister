plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dk.aspia.frister"
    compileSdk = 34

    defaultConfig {
        applicationId = "dk.aspia.frister"
        minSdk = 30
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"

        // Web3Forms-nøglen kommer fra GitHub-secret'en WEB3FORMS_KEY, så den ikke står i det offentlige repo.
        buildConfigField("String", "WEB3FORMS_KEY", "\"${System.getenv("WEB3FORMS_KEY") ?: ""}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
