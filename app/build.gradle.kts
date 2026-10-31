import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

// Local development URL stays in the ignored SDK local.properties file.
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) localFile.inputStream().use { load(it) }
}
fun cleanUrl(raw: String?): String = raw.orEmpty().trim().trimEnd('/')
val debugApiUrl = cleanUrl(localProperties.getProperty("mindhavenDevApiUrl")).ifEmpty { "http://10.0.2.2:8080" }
val releaseApiUrl = cleanUrl(project.findProperty("mindhavenReleaseApiUrl") as String? ?: "https://emotionsense-ai-3vxa.onrender.com")
fun signingValue(property: String, environment: String): String? = localProperties.getProperty(property)?.takeIf { it.isNotBlank() } ?: System.getenv(environment)?.takeIf { it.isNotBlank() }
val uploadStore = signingValue("mindhavenUploadStoreFile", "MINDHAVEN_UPLOAD_STORE_FILE")
val uploadStorePassword = signingValue("mindhavenUploadStorePassword", "MINDHAVEN_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = signingValue("mindhavenUploadKeyAlias", "MINDHAVEN_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = signingValue("mindhavenUploadKeyPassword", "MINDHAVEN_UPLOAD_KEY_PASSWORD")
val signingValues = listOf(uploadStore, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
if (signingValues.any { it != null } && signingValues.any { it == null }) throw GradleException("All four upload signing settings are required together")
fun validateOrigin(value: String, release: Boolean) {
    val uri = try { URI(value) } catch (_: Exception) { throw GradleException("Invalid backend URL") }
    if (uri.host.isNullOrEmpty() || uri.userInfo != null || uri.query != null || uri.fragment != null || !(uri.path.isNullOrEmpty() || uri.path == "/")) throw GradleException("Backend URL must be an origin")
    if (release && uri.scheme != "https") throw GradleException("Release backend must be https://")
    if (!release && uri.scheme !in listOf("http", "https")) throw GradleException("Unsupported debug backend scheme")
}
validateOrigin(debugApiUrl, false)
validateOrigin(releaseApiUrl, true)

android {
    namespace = "com.example.mindhaven"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mindhaven"
        minSdk = 33
        targetSdk = 36
        // Increase the integer for every Play upload; use a human-readable release name.
        versionCode = (project.findProperty("mindhavenVersionCode") as String?)?.let { it.toIntOrNull()?.takeIf { n -> n > 0 } ?: throw GradleException("mindhavenVersionCode must be a positive integer") } ?: 1
        versionName = (project.findProperty("mindhavenVersionName") as String?)?.takeIf { it.isNotBlank() } ?: "1.0.0"

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

    signingConfigs {
        if (signingValues.all { it != null }) {
            create("upload") {
                storeFile = rootProject.file(uploadStore!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.findByName("upload")
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
    implementation(libs.androidx.runtime.livedata)
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


    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)


}
