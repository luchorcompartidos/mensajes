package com.mismensajes.app

import java.text.Normalizer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Variables que se pueden escribir en el texto de un envío, entre llaves: "Hola {primer_nombre}, {saludo}".
 * Se reemplazan al abrir WhatsApp para cada contacto (fecha, hora, día y saludo: los de ese momento).
 * Las llaves que no son una variable conocida quedan tal cual. Sirve en "mismo texto" y en "un texto por contacto".
 */
object Variables {
    data class Variable(val clave: String, val descripcion: String)

    val LISTA = listOf(
        Variable("nombre", "Nombre del contacto, como está guardado en la app"),
        Variable("primer_nombre", "Solo el primer nombre del contacto"),
        Variable("saludo", "Buen día / Buenas tardes / Buenas noches, según la hora"),
        Variable("dia", "Día de la semana (lunes, martes…)"),
        Variable("fecha", "Fecha del día (23/11/2026)"),
        Variable("hora", "Hora (20:30)")
    )

    private val loc = Locale.forLanguageTag("es-AR")
    private val fFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy", loc)
    private val fHora = DateTimeFormatter.ofPattern("HH:mm", loc)
    private val fDia = DateTimeFormatter.ofPattern("EEEE", loc)
    private val patron = Regex("\\{\\s*([\\p{L}_ ]+?)\\s*\\}")

    /** "{Día}", "{ dia }" y "{DIA}" valen lo mismo que "{dia}"; "{primer nombre}" lo mismo que "{primer_nombre}". */
    private fun normalizar(clave: String) =
        Normalizer.normalize(clave.lowercase(loc), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").trim().replace(' ', '_')

    private fun valor(clave: String, c: Contacto?, ahora: ZonedDateTime): String? = when (clave) {
        "nombre" -> c?.nombre?.trim() ?: ""
        "primer_nombre" -> c?.nombre?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: ""
        "saludo" -> when (ahora.hour) {
            in 5..12 -> "Buen día"
            in 13..19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        "dia" -> ahora.format(fDia)
        "fecha" -> ahora.format(fFecha)
        "hora" -> ahora.format(fHora)
        else -> null
    }

    /** El texto con las variables reemplazadas para el contacto [c] en el momento [ahora]. */
    fun aplicar(texto: String, c: Contacto?, ahora: ZonedDateTime = ZonedDateTime.now()): String {
        if (!texto.contains('{')) return texto
        var huboVacia = false
        val r = patron.replace(texto) { m ->
            val v = valor(normalizar(m.groupValues[1]), c, ahora) ?: return@replace m.value
            if (v.isEmpty()) huboVacia = true
            v
        }
        // Si una variable quedó vacía (ej. contacto sin nombre), se acomodan los espacios: "Hola {nombre}, ¿cómo…" → "Hola, ¿cómo…"
        return if (!huboVacia) r
               else r.replace(Regex(" {2,}"), " ").replace(Regex(" +([,.;:!?])"), "$1").trim()
    }

    /** Las llaves del texto que no son variables conocidas (para avisar en la vista previa). */
    fun desconocidas(texto: String): List<String> =
        patron.findAll(texto).map { it.value }.filter { valor(normalizar(it.trim('{', '}')), null, ZonedDateTime.now()) == null }
            .distinct().toList()

    fun usa(texto: String) = patron.containsMatchIn(texto)
}
