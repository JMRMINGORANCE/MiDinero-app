plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    sourceSets["main"].java.srcDir("../shared/src/main/java")
    namespace = "com.midinero.control"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.midinero.control"
        minSdk = 26
        targetSdk = 36
        // +200: el repositorio anterior llegó a la compilación 131; así el versionCode sigue subiendo en Play.
        // Siempre por encima de las versiones 1.0.x ya instaladas (su versionCode era el número de compilación),
        // para que Android acepte el APK nuevo como actualización y conserve los datos.
        versionCode = 100 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(200) ?: 0)
        versionName = providers.gradleProperty("midinero.version").get() + "." + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(200)?.toString() ?: "0")
    }

    // Firma fija para que cada APK nuevo se instale encima del anterior sin perder datos.
    // Si hay una clave privada en las variables de entorno (secretos de GitHub), se usa esa;
    // si no, la clave de desarrollo incluida en el repositorio (nunca para el .aab de Play: ver más abajo).
    signingConfigs {
        create("midinero") {
            val envKeystore = System.getenv("MIDINERO_KEYSTORE")?.takeIf { it.isNotBlank() }
            if (envKeystore != null) {
                storeFile = file(envKeystore)
                storePassword = System.getenv("MIDINERO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MIDINERO_KEY_ALIAS")
                keyPassword = System.getenv("MIDINERO_KEY_PASSWORD")
            } else {
                storeFile = file("midinero.keystore")
                storePassword = System.getenv("MIDINERO_KEYSTORE_PASSWORD")
                keyAlias = "midinero"
                keyPassword = System.getenv("MIDINERO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("midinero")
        }
        release {
            signingConfig = signingConfigs.getByName("midinero")
            // R8 reduce el APK y acelera Compose. Room, WorkManager y Glance traen sus propias reglas.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // El informe se guarda en CI y se muestra en el log; los avisos no bloquean la compilación, los errores sí.
        abortOnError = true
        checkReleaseBuilds = true
        textReport = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric con Compose necesita más memoria que los 512 MB por defecto
            it.maxHeapSize = "2g"
            // Las capturas de pantalla solo se hacen si el CI lo pide
            it.environment("SCREENSHOTS", System.getenv("SCREENSHOTS") ?: "")
            it.outputs.upToDateWhen { false }
            it.testLogging {
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                events("failed", "skipped")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// El .aab es para Google Play: no se genera firmado con la clave de desarrollo, cuya contraseña es pública.
tasks.matching { it.name == "bundleRelease" }.configureEach {
    doFirst {
        if (System.getenv("MIDINERO_KEYSTORE").isNullOrBlank()) {
            throw GradleException("bundleRelease necesita la clave de subida (MIDINERO_KEYSTORE…): mira PLAY_STORE.md")
        }
    }
}

// Esquemas de la base de datos por versión (necesarios para comprobar migraciones).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    // Leer los tickets: reconocimiento de texto con el modelo dentro de la app (sin Internet ni Play Services)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.biometric:biometric:1.1.0")
    // biometric 1.1.0 trae fragment 1.2.5, cuyo FragmentActivity rechaza los requestCode de más de 16 bits que usan
    // los selectores de archivos de activity 1.11 (Restaurar, Importar, foto del ticket…): se fuerza una versión actual.
    implementation("androidx.fragment:fragment-ktx:1.9.0")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.work:work-testing:2.12.0")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

