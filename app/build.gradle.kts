plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.gitgud.tunerack"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gitgud.tunerack"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipe Extractor uses java.nio APIs that need desugaring below API 33.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.haze)
    implementation(libs.haze.blur)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.newpipe.extractor)
    implementation(libs.jsoup)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    implementation(libs.work.runtime)
    implementation(libs.androidx.documentfile)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
}

ksp {
    // Exported schemas let future versions migrate the user's library instead of wiping it.
    arg("room.schemaLocation", "$projectDir/schemas")
}
