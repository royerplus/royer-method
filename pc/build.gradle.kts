import org.gradle.api.tasks.testing.logging.TestExceptionFormat

// Royer Method para PC.
//
// La interfaz del editor, el modelo, los sombreadores y el compositor son los
// mismos archivos que usa el telefono: se copian de ../app/src/main/java en
// cada compilacion (tarea "compartido"), asi que un cambio alli sale tambien
// aqui. Lo que en el telefono es de Android (MediaCodec, EGL, AudioTrack,
// android.graphics) tiene aqui su version de PC con el mismo nombre, en
// src/main/kotlin: FFmpeg para leer y escribir video, LWJGL para OpenGL,
// javax.sound para el sonido y Skia/AWT para el texto.
//
// -Pnube=true compila con Compose de escritorio 1.5.12 para Linux, sin el
// plugin de empaquetado: es lo que se puede bajar en la nube de Claude, donde
// el repositorio de Google esta bloqueado. Lo normal (CI y el PC) es 1.7.3.

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3" apply false
}

val nube = (findProperty("nube") as String?) == "true"
val compose = if (nube) "1.5.12" else "1.7.3"
val lwjgl = "3.3.4"
val bytedeco = "1.5.11"
val ffmpeg = "7.1-$bytedeco"
// Las bibliotecas nativas de Windows siempre; las de Linux para probar en la nube
val plataformas = listOf("windows-x86_64", "linux-x86_64")

group = "com.royermethod"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn", "-Xskip-prerelease-check")
        suppressWarnings.set(true)
    }
}

/** Los archivos del telefono que sirven tal cual en el PC. */
val compartido = tasks.register<Sync>("compartido") {
    from("../app/src/main/java") {
        include(
            "com/royermethod/app/editor/modelo/**",
            "com/royermethod/app/editor/ui/**",
            "com/royermethod/app/editor/Persistencia.kt",
            "com/royermethod/app/editor/PersistenciaLuces.kt",
            "com/royermethod/app/editor/PaqueteProyecto.kt",
            "com/royermethod/app/editor/ImportadosProyecto.kt",
            "com/royermethod/app/editor/audio/**",
            "com/royermethod/app/editor/motor/Gles.kt",
            "com/royermethod/app/editor/motor/Sombreadores.kt",
            "com/royermethod/app/editor/motor/VfxPantallaShader.kt",
            "com/royermethod/app/editor/motor/FiguraShader.kt",
            "com/royermethod/app/editor/motor/CampoFigura.kt",
            "com/royermethod/app/editor/motor/PintorLuces.kt",
            "com/royermethod/app/editor/motor/Reduccion.kt",
            "com/royermethod/app/editor/motor/EntradaVideo.kt",
            "com/royermethod/app/editor/motor/Compositor.kt",
            "com/royermethod/app/editor/motor/Estela.kt",
            "com/royermethod/app/editor/exportar/SincronizacionVideo.kt",
            "com/royermethod/app/editor/vista/RelojAudio.kt",
            "com/royermethod/app/ui/components/Controles.kt",
            "com/royermethod/app/ui/components/Glifos.kt",
            "com/royermethod/app/ui/theme/**",
            "com/royermethod/app/Preferencias.kt",
        )
        // Tienen su version de PC en src/main/kotlin
        exclude("com/royermethod/app/editor/audio/DecodificadorAudio.kt")
    }
    into(layout.buildDirectory.dir("compartido"))
}

sourceSets["main"].kotlin.srcDir(compartido)

dependencies {
    if (nube) {
        implementation("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:$compose")
    } else {
        implementation("org.jetbrains.compose.desktop:desktop-jvm-windows-x64:$compose")
        implementation("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:$compose")
    }
    implementation("org.jetbrains.compose.material3:material3-desktop:$compose")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.json:json:20240303")

    implementation("org.lwjgl:lwjgl:$lwjgl")
    implementation("org.lwjgl:lwjgl-opengl:$lwjgl")
    implementation("org.lwjgl:lwjgl-glfw:$lwjgl")
    for (p in listOf("natives-windows", "natives-linux")) {
        runtimeOnly("org.lwjgl:lwjgl:$lwjgl:$p")
        runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjgl:$p")
        runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjgl:$p")
    }

    // FFmpeg (version GPL: trae libx264 y libx265) con JavaCV por encima
    implementation("org.bytedeco:javacv:$bytedeco") { isTransitive = false }
    implementation("org.bytedeco:javacpp:$bytedeco")
    implementation("org.bytedeco:ffmpeg:$ffmpeg")
    for (p in plataformas) {
        runtimeOnly("org.bytedeco:javacpp:$bytedeco:$p")
        runtimeOnly("org.bytedeco:ffmpeg:$ffmpeg:$p-gpl")
    }

    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    testLogging { events("failed"); exceptionFormat = TestExceptionFormat.FULL }
}

tasks.register<JavaExec>("correr") {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.royermethod.pc.MainKt")
    jvmArgs("-Xmx3g")
    args((findProperty("args") as String? ?: "").split(" ").filter { it.isNotBlank() })
}

if (!nube) {
    apply(plugin = "org.jetbrains.compose")
    extensions.configure<org.jetbrains.compose.ComposeExtension> {
        (this as ExtensionAware).extensions.configure<org.jetbrains.compose.desktop.DesktopExtension> {
            application {
                mainClass = "com.royermethod.pc.MainKt"
                jvmArgs += listOf("-Xmx4g", "-Dfile.encoding=UTF-8")
                nativeDistributions {
                    targetFormats(
                        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                    )
                    packageName = "Royer Method"
                    packageVersion = "1.0.0"
                    description = "Editor de video Royer Method"
                    vendor = "Royer"
                    modules("java.desktop", "java.logging", "jdk.unsupported", "java.management", "java.naming")
                    windows {
                        menu = true
                        shortcut = true
                        dirChooser = true
                        perUserInstall = true
                        menuGroup = "Royer Method"
                        upgradeUuid = "6c0f2a58-3b1e-4d43-9c55-2f4f6b1d7a11"
                        iconFile.set(project.file("icono.ico"))
                    }
                }
            }
        }
    }
}

tasks.register("cp") {
    dependsOn("classes")
    doLast { file("build/cp.txt").writeText(sourceSets["main"].runtimeClasspath.files.joinToString(File.pathSeparator)) }
}
