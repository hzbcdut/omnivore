import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    id("dagger.hilt.android.plugin")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

val keystorePropertiesFile = rootProject.file("app/external/keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { input ->
        keystoreProperties.load(input)
    }
}

android {
    namespace = "app.omnivore.omnivore"

    compileSdk = 34

    defaultConfig {
        applicationId = "app.omnivore.omnivore"
        minSdk = 26
        targetSdk = 34
        versionCode = 2300000
        versionName = "0.230.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = "key0"
            storeFile = file("external/omnivore-prod.keystore")
            storePassword = keystoreProperties["prodStorePassword"] as String?
            keyPassword = keystoreProperties["prodKeyPassword"] as String?
        }/*        debug {
                    if (keystoreProperties["demoStorePassword"] && keystoreProperties["demoKeyPassword"]) {
                        keyAlias = "androiddebugkey"
                        storeFile = file("external/omnivore-demo.keystore")
                        storePassword = keystoreProperties["demoStorePassword"] as String
                        keyPassword = keystoreProperties["demoKeyPassword"] as String
                    }
                }*/
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)


    val bom = platform(libs.androidx.compose.bom)
    implementation(bom)
    androidTestImplementation(bom)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.util)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    androidTestImplementation(libs.androidx.compose.ui.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.testManifest)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)

    implementation(libs.androidx.lifecycle.viewModelKtx)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.androidx.lifecycle.viewmodelSavedstate)

    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.compose.runtime.livedata)


    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.dataStore.preferences)
    implementation(libs.androidx.documentfile)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)


    implementation(libs.accompanist.flowlayout)

    implementation(libs.coil.kt.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    annotationProcessor(libs.room.compiler)
    ksp(libs.room.compiler)

    implementation(libs.gson)
    implementation(libs.pspdfkit)
    implementation(libs.compose.markdown)
    implementation(libs.chiptextfield.m3)

    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.androidx.core.splashscreen)

}

tasks.register("printVersion") {
    doLast {
        println("omnivoreVersion: ${android.defaultConfig.versionName}")
    }
}
