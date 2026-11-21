// La version para PC (Windows) de Royer Method. Es una compilacion aparte de
// la del telefono (no se incluye en ../settings.gradle.kts): asi el APK no
// depende de nada de aqui. Comparte el codigo del editor con la app, ver
// build.gradle.kts.
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo1.maven.org/maven2")
        if (providers.gradleProperty("nube").orNull != "true") google()
    }
}

dependencyResolutionManagement {
    repositories {
        // repo1: en la nube de Claude repo.maven.apache.org no entrega los .jar
        maven("https://repo1.maven.org/maven2")
        // En la nube de Claude el repositorio de Google esta bloqueado (ver build.gradle.kts)
        if (providers.gradleProperty("nube").orNull != "true") google()
    }
}

rootProject.name = "RoyerMethodPC"
