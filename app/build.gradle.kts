plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.calculadoraflutuante"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.calculadoraflutuante"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Assinatura fixa, vinda dos segredos do GitHub (ver .github/workflows/build-apk.yml).
    // Sem ela cada APK sai com uma chave nova e o Android recusa instalar por cima.
    val arquivoAssinatura = System.getenv("ASSINATURA_ARQUIVO")
    if (arquivoAssinatura != null) {
        signingConfigs {
            create("fixa") {
                storeFile = file(arquivoAssinatura)
                storePassword = System.getenv("ASSINATURA_SENHA")
                keyAlias = "calculadora"
                keyPassword = System.getenv("ASSINATURA_SENHA")
            }
        }
    }

    buildTypes {
        debug {
            if (arquivoAssinatura != null) signingConfig = signingConfigs.getByName("fixa")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
