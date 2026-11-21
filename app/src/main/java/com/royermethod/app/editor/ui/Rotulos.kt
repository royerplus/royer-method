package com.royermethod.app.editor.ui

import java.util.concurrent.ConcurrentHashMap

/**
 * Como se leen en pantalla los nombres que el modelo guarda sin tildes
 * ("Circulo", "Basicas", "Sacudida de camara"). Ahi no se tocan: los
 * proyectos guardados, los grupos de efectos y las pruebas los comparan letra
 * a letra. Aqui solo cambia lo que se ensena.
 */
internal object Rotulos {
    private val PALABRAS = mapOf(
        "Animacion" to "Animación", "animacion" to "animación",
        "angulo" to "ángulo",
        "arcoiris" to "arcoíris",
        "Basicas" to "Básicas",
        "Calido" to "Cálido",
        "Camara" to "Cámara", "camara" to "cámara",
        "Circulo" to "Círculo", "circulo" to "círculo",
        "Clasica" to "Clásica",
        "Combinalo" to "Combínalo",
        "conversion" to "conversión",
        "dejalo" to "déjalo",
        "despues" to "después",
        "Detras" to "Detrás", "detras" to "detrás",
        "Desvaido" to "Desvaído",
        "direccion" to "dirección", "Direccion" to "Dirección",
        "Distorsion" to "Distorsión",
        "Duracion" to "Duración", "duracion" to "duración",
        "Elastico" to "Elástico",
        "Frio" to "Frío",
        "Linea" to "Línea", "lineas" to "líneas",
        "Maquina" to "Máquina",
        "Mas" to "Más", "mas" to "más",
        "Mascaras" to "Máscaras", "mascara" to "máscara", "Mascara" to "Máscara",
        "Mia" to "Mía", "Mias" to "Mías",
        "muevela" to "muévela",
        "Neon" to "Neón",
        "musica" to "música",
        "Pendulo" to "Péndulo",
        "Rapida" to "Rápida", "rapida" to "rápida", "rapidas" to "rápidas",
        "Rapido" to "Rápido", "rapido" to "rápido", "rapidos" to "rápidos",
        "recortala" to "recórtala",
        "seguia" to "seguía",
        "Separacion" to "Separación", "separacion" to "separación",
        "tamano" to "tamaño",
        "Transicion" to "Transición", "transicion" to "transición",
        "Vibracion" to "Vibración"
    )
    private val PALABRA = Regex("[A-Za-z]+")
    private val vistos = ConcurrentHashMap<String, String>()

    /** [texto] con sus tildes. Los nombres salen de catalogos fijos: se recuerdan para no repetir la busqueda. */
    fun de(texto: String): String {
        vistos[texto]?.let { return it }
        val r = PALABRA.replace(texto) { PALABRAS[it.value] ?: it.value }
        if (vistos.size < 2000) vistos[texto] = r
        return r
    }
}
