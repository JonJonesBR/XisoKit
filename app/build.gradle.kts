plugins {
    id("com.android.application")
    // O Kotlin em si vem embutido no AGP 9; aqui só entra o compilador do Compose.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.jonjonesbr.xisokit"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "io.github.jonjonesbr.xisokit"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.3.1"
    }

    // A chave de assinatura NUNCA fica no repositório. No GitHub ela vem dos Secrets;
    // sem ela (build local), o APK sai com a chave de debug do próprio computador.
    val releaseStoreFile = System.getenv("SIGNING_STORE_FILE")
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "xisokit"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: System.getenv("SIGNING_STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 encolhe e ofusca o APK. O app não usa reflexão, então as regras
            // padrão bastam; as do Compose vêm nos próprios artefatos.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName(if (releaseStoreFile != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
        // O AGP 9 já gera o relatório em texto; o CI o imprime no log.
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation("junit:junit:4.13.2")
}
