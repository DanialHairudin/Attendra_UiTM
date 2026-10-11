import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// Secrets live in local.properties (never committed, see .gitignore).
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** A local.properties value as a Java string literal for buildConfigField ("" if missing). */
fun localString(key: String): String {
    val value = localProps.getProperty(key, "")
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "com.attendra.uitm"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.attendra.uitm"
        minSdk = 26
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
            // Test account logins for the hidden tester panel (CLAUDE.md 12.4).
            // Only the debug build gets the real values.
            buildConfigField("String", "TEST_LECTURER_EMAIL", localString("TEST_LECTURER_EMAIL"))
            buildConfigField("String", "TEST_LECTURER_PASS", localString("TEST_LECTURER_PASS"))
            buildConfigField("String", "TEST_STUDENT_EMAIL", localString("TEST_STUDENT_EMAIL"))
            buildConfigField("String", "TEST_STUDENT_PASS", localString("TEST_STUDENT_PASS"))
        }
        release {
            // Empty in release, so no test password is ever inside a release APK.
            buildConfigField("String", "TEST_LECTURER_EMAIL", "\"\"")
            buildConfigField("String", "TEST_LECTURER_PASS", "\"\"")
            buildConfigField("String", "TEST_STUDENT_EMAIL", "\"\"")
            buildConfigField("String", "TEST_STUDENT_PASS", "\"\"")
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