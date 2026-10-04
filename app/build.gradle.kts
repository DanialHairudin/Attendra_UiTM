plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.attendra.uitm"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.attendra.uitm"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Realtime Database URL. Set here because google-services.json was downloaded
        // before the database existed, so it has no firebase_url in it.
        buildConfigField(
            "String", "DATABASE_URL",
            "\"https://attendra-uitm-default-rtdb.asia-southeast1.firebasedatabase.app\""
        )
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        debug {
            // false in debug so Gmail accounts can be used for testing (CLAUDE.md section 4)
            buildConfigField("boolean", "REQUIRE_UITM_EMAIL", "false")
        }
        release {
            buildConfigField("boolean", "REQUIRE_UITM_EMAIL", "true")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}