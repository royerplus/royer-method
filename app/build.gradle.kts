plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.royermethod.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.royermethod.app"
        // 26 es el minimo real: por debajo no existen los iconos adaptativos
        // y habria que mantener un juego de PNG aparte solo para el lanzador.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Solo arm64. Es lo que lleva cualquier telefono capaz de mover
        // Vulkan con computacion, que es lo que exige el modelo, y ncnn son
        // unos diez megas por arquitectura: incluir las demas doblaria el
        // peso del APK para telefonos que no podrian usar la funcion.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                // -fexceptions: rife.cpp usa vectores de la STL y ncnn lanza
                // en algunos caminos de error; sin excepciones el enlazado
                // falla al buscar __cxa_throw.
                cppFlags += listOf("-fexceptions", "-frtti", "-O3")
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Se firma con la clave de depuracion solo para poder instalarlo y
            // medir el rendimiento de verdad. Un build de depuracion lleva
            // comprobaciones extra de Compose y va notablemente mas lento, asi
            // que juzgar la fluidez con el APK debug enganaba.
            signingConfig = signingConfigs.getByName("debug")
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
    debugImplementation(libs.androidx.ui.tooling)
    // El modelo del editor (linea de tiempo, escena, audio) es Kotlin puro y
    // se prueba en el ordenador: ./gradlew test
    testImplementation(libs.junit)
}
