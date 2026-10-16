plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}

// ---- Backend base URL (Phase 2B.4) -------------------------------------------------------------
// Debug builds talk to a DEVELOPMENT backend only. Default: the local Express server seen from the
// Android emulator (10.0.2.2 = the host machine; the backend listens on PORT 8080 locally).
// Override with  -PmindhavenDevApiUrl=https://<dev-host>  (e.g. in ~/.gradle/gradle.properties).
// Release builds have NO default: set -PmindhavenReleaseApiUrl=https://<host> explicitly; with no value the app
// reports "backend not configured" and makes no network calls. Both must stay off the production Render service
// until the production migration + backend are deployed and approved.
val productionHostMarkers = listOf("emotionsense-ai-3vxa.onrender.com")
fun cleanUrl(raw: String?): String = raw.orEmpty().trim().trimEnd('/')
fun assertNotProduction(name: String, url: String) {
    if (productionHostMarkers.any { url.contains(it, ignoreCase = true) } && project.findProperty("mindhavenAllowProdBackend") != "true") {
        throw GradleException("$name points at the PRODUCTION backend. Not allowed until production is migrated and approved.")
    }
}
val debugApiUrl = cleanUrl(project.findProperty("mindhavenDevApiUrl") as String?).ifEmpty { "http://10.0.2.2:8080" }
val releaseApiUrl = cleanUrl(project.findProperty("mindhavenReleaseApiUrl") as String?)
assertNotProduction("mindhavenDevApiUrl (debug)", debugApiUrl)
if (releaseApiUrl.isNotEmpty()) {
    assertNotProduction("mindhavenReleaseApiUrl", releaseApiUrl)
    if (!releaseApiUrl.startsWith("https://")) throw GradleException("mindhavenReleaseApiUrl must be https:// (release builds never allow cleartext HTTP).")
}
if (!debugApiUrl.startsWith("https://") && !Regex("""^http://(10\.0\.2\.2|localhost|127\.0\.0\.1)(:\d+)?$""").matches(debugApiUrl)) {
    throw GradleException("mindhavenDevApiUrl: cleartext http:// is only allowed for the emulator/localhost (10.0.2.2, localhost, 127.0.0.1).")
}

android {
    namespace = "com.example.mindhaven"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mindhaven"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    kapt {
        correctErrorTypes = false
        arguments {
            // Hilt's Gradle plugin rewrites the superclass of @AndroidEntryPoint classes in bytecode; the annotation
            // processor must therefore not demand that the source already extends the generated Hilt_* base class.
            // (Hilt 2.59 with AGP 9 + kapt does not pass this itself.)
            arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiUrl\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("androidx.arch.core:core-testing:2.2.0") // InstantTaskExecutorRule: LiveData in JVM tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.core:core-ktx:1.13.0")

    implementation("com.airbnb.android:lottie-compose:6.6.7")

    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("com.google.dagger:hilt-android:${libs.versions.hilt.get()}")
    kapt("com.google.dagger:hilt-android-compiler:${libs.versions.hilt.get()}")

    implementation("androidx.media:media:1.7.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")

    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Backend networking (Phase 2B.4): one small HTTP client + typed JSON; coroutines for Firebase Tasks.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)


}