plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("kotlin-parcelize")
}

android {
    namespace = "com.pkm.said"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pkm.said"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "ACCESS_KEY", "\"${project.properties["ACCESS_KEY"]}\"")
        buildConfigField("String", "NEWS_API_KEY", "\"${project.properties["NEWS_API_KEY"]}\"")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${project.properties["cloudName"]}\"")
        buildConfigField("String", "CLOUDINARY_UNSIGNED_PRESET", "\"${project.properties["uploadPreset"]}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform ( libs.firebase.bom ))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.libraries.identity.googleid)
    implementation(libs.porcupine.android)
    implementation(libs.picovoice.rhino.android)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.gson)
    implementation(libs.retrofit2)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.moshi)
    implementation(libs.guava)
    implementation(libs.guava.listenablefuture)
    implementation(libs.glide)
    implementation(libs.slidetoact)
    implementation(libs.firebase.firestore)
    implementation(libs.play.services.location)
    implementation(libs.firebase.auth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}