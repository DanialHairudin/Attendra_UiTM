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

        // Realtime Database URL.
        buildConfigField(
            "String", "DATABASE_URL",
            "\"https://attendra-uitm-default-rtdb.asia-southeast1.firebasedatabase.app\""
        )

        // Gmail account that sends the 6-digit verification codes.
        buildConfigField("String", "SMTP_USER", localString("SMTP_USER"))
        buildConfigField("String", "SMTP_PASS", localString("SMTP_PASS"))
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        debug {
            // How long a verification code stays valid.
            buildConfigField("int", "CODE_TTL_MINUTES", "1")

            // Test account logins for the hidden tester panel.
            buildConfigField("String", "TEST_LECTURER_EMAIL", localString("TEST_LECTURER_EMAIL"))
            buildConfigField("String", "TEST_LECTURER_PASS", localString("TEST_LECTURER_PASS"))
            buildConfigField("String", "TEST_STUDENT_EMAIL", localString("TEST_STUDENT_EMAIL"))
            buildConfigField("String", "TEST_STUDENT_PASS", localString("TEST_STUDENT_PASS"))

            // Off unless built with -PuseEmulators=true: then Auth and the Realtime Database use
            // the local Firebase emulators and no verification email is sent. 127.0.0.1 needs
            // "adb reverse tcp:9000 tcp:9000" and "adb reverse tcp:9099 tcp:9099".
            buildConfigField("boolean", "USE_EMULATORS",
                (project.findProperty("useEmulators") == "true").toString())
            buildConfigField("String", "EMULATOR_HOST",
                "\"" + (project.findProperty("emulatorHost") ?: "127.0.0.1") + "\"")
        }
        release {
            buildConfigField("boolean", "USE_EMULATORS", "false")
            buildConfigField("String", "EMULATOR_HOST", "\"\"")

            buildConfigField("int", "CODE_TTL_MINUTES", "10")

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
    packaging {
        resources {
            // Android-mail and android-activation both ship these licence files, and an APK can
            // only hold one copy of each path.
            excludes += setOf("META-INF/NOTICE.md", "META-INF/LICENSE.md")
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
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    // JavaMail for Android: sends the verification code email over SMTP
    implementation(libs.android.mail)
    implementation(libs.android.activation)
    // OpenStreetMap maps (osmdroid): venue pin map and live check-in map.
    implementation(libs.osmdroid.android)
    // FusedLocationProviderClient: one fresh GPS fix when the student taps Check in
    implementation(libs.play.services.location)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}