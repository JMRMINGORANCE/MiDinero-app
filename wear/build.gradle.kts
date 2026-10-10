plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// App de MiDinero para relojes Wear OS (Galaxy Watch y otros). Comparte applicationId y firma con la del móvil:
// la capa de datos de Wear OS solo conecta apps con el mismo paquete y la misma firma.
android {
    namespace = "com.midinero.control.wear"
    compileSdk = 36
    sourceSets["main"].java.srcDir("../shared/src/main/java")

    defaultConfig {
        applicationId = "com.midinero.control"
        minSdk = 30
        targetSdk = 36
        // Play exige un versionCode distinto en cada bundle de la app: el del reloj va en su propio rango.
        // +200: mismo desfase que la app del móvil (el repositorio anterior llegó a la compilación 131).
        versionCode = 100_000 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(200) ?: 0)
        versionName = providers.gradleProperty("midinero.version").get() + "." + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(200)?.toString() ?: "0")
    }

    // Misma clave que el móvil; el .aab del reloj tampoco se genera con la de desarrollo.
    signingConfigs {
        create("midinero") {
            val envKeystore = System.getenv("MIDINERO_KEYSTORE")?.takeIf { it.isNotBlank() }
            if (envKeystore != null) {
                storeFile = file(envKeystore)
                storePassword = System.getenv("MIDINERO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MIDINERO_KEY_ALIAS")
                keyPassword = System.getenv("MIDINERO_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("app/midinero.keystore")
                storePassword = System.getenv("MIDINERO_KEYSTORE_PASSWORD")
                keyAlias = "midinero"
                keyPassword = System.getenv("MIDINERO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("midinero") }
        release {
            signingConfig = signingConfigs.getByName("midinero")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.environment("SCREENSHOTS", System.getenv("SCREENSHOTS") ?: "")
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.wear.compose:compose-material:1.6.2")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")
    implementation("androidx.wear.compose:compose-navigation:1.6.2")
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.3.0")
    implementation("androidx.concurrent:concurrent-futures:1.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core-ktx:1.7.0")
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    doFirst {
        if (System.getenv("MIDINERO_KEYSTORE").isNullOrBlank()) {
            throw GradleException("bundleRelease necesita la clave de subida (MIDINERO_KEYSTORE…): mira PLAY_STORE.md")
        }
    }
}
