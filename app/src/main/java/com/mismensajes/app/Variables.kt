package com.mismensajes.app

import android.content.Context
import java.text.Normalizer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Variables que se pueden escribir en el texto de un envío, entre llaves: "Hola {primer_nombre}, {saludo}".
 * Se reemplazan al abrir WhatsApp para cada contacto (fecha, hora, día y saludo: los de ese momento).
 * Sirve en "mismo texto" y en "un texto por contacto".
 *
 * Variables propias (desde la planilla): cada columna extra del CSV es una variable ({turno}, {monto}).
 * Del CSV de contactos quedan en el contacto ([Contacto.datos], sirven en cualquier envío); del CSV de envíos,
 * en ese destinatario de ese envío ([Destino.datos]), que pisa al del contacto. Si a un destinatario le falta
 * el valor de una variable propia, se reemplaza por nada (con aviso en la vista previa y en la importación).
 * Las llaves que no son ninguna variable conocida quedan tal cual.
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
    private val patron = Regex("\\{\\s*([\\p{L}\\p{N}_ ]+?)\\s*\\}")

    /**
     * "{Día}", "{ dia }" y "{DIA}" valen lo mismo que "{dia}"; "{primer nombre}" lo mismo que "{primer_nombre}".
     * También se usa para el nombre de las columnas extra del CSV ("Monto a pagar" → "monto_a_pagar").
     */
    fun normalizar(clave: String) =
        Normalizer.normalize(clave.lowercase(loc), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").trim().replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_')

    /** Nombres de columnas que el lector de CSV ya usa (no pueden ser variables propias). */
    val RESERVADAS = setOf("numero", "telefono", "celular", "whatsapp", "nombre", "contacto", "lista", "listas", "default",
        "grupo_default", "modo", "canal", "enviar_por", "titulo", "asunto", "texto", "mensaje", "alarma", "repeticion",
        "repetir", "se_repite", "veces", "cantidad_de_veces", "terminar_despues_de")

    /** Es el nombre de una variable de la app (no puede usarse como variable propia). */
    fun esDeLaApp(clave: String) = LISTA.any { it.clave == clave }

    /** Valores propios de ese destinatario: los del contacto, pisados por los del envío (vacíos no pisan). */
    fun datos(c: Contacto?, d: Destino?): Map<String, String> =
        (c?.datos ?: emptyMap()) + (d?.datos ?: emptyMap()).filterValues { it.isNotBlank() }

    /** Todas las variables propias que existen (en algún contacto o envío), ordenadas. */
    fun propias(ctx: Context): List<String> =
        (Contactos.todos(ctx).flatMap { it.datos.keys } +
            Envios.todos(ctx).flatMap { e -> e.destinos.flatMap { it.datos.keys } })
            .distinct().sorted()

    /** Variables propias que existen en los contactos (para la ficha del contacto). */
    fun propiasDeContactos(ctx: Context): List<String> =
        Contactos.todos(ctx).flatMap { it.datos.keys }.distinct().sorted()

    private fun valor(clave: String, c: Contacto?, ahora: ZonedDateTime, datos: Map<String, String>,
                      propias: Collection<String>): String? = when (clave) {
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
        else -> datos[clave]?.trim() ?: if (clave in propias) "" else null
    }

    /**
     * El texto con las variables reemplazadas para el contacto [c] en el momento [ahora].
     * [datos]: valores propios de ese destinatario ([datos]); [propias]: todas las variables propias que existen
     * (las que falten en [datos] quedan vacías; las llaves que no están en ninguna lista quedan tal cual).
     */
    fun aplicar(texto: String, c: Contacto?, ahora: ZonedDateTime = ZonedDateTime.now(),
                datos: Map<String, String> = c?.datos ?: emptyMap(), propias: Collection<String> = emptyList()): String {
        if (!texto.contains('{')) return texto
        var huboVacia = false
        val r = patron.replace(texto) { m ->
            val v = valor(normalizar(m.groupValues[1]), c, ahora, datos, propias) ?: return@replace m.value
            if (v.isEmpty()) huboVacia = true
            v
        }
        // Si una variable quedó vacía (ej. contacto sin nombre), se acomodan los espacios: "Hola {nombre}, ¿cómo…" → "Hola, ¿cómo…"
        return if (!huboVacia) r
               else r.replace(Regex(" {2,}"), " ").replace(Regex(" +([,.;:!?])"), "$1").trim()
    }

    /** [aplicar] para un destinatario de un envío: con sus valores propios y los del contacto. */
    fun aplicar(ctx: Context, texto: String, c: Contacto?, d: Destino?, ahora: ZonedDateTime = ZonedDateTime.now(),
                propias: Collection<String> = Variables.propias(ctx)): String =
        aplicar(texto, c, ahora, Variables.datos(c, d), propias)

    /** Variables propias que usa el texto y a ese destinatario le faltan (quedan vacías al enviar). */
    fun faltantes(texto: String, datos: Map<String, String>, propias: Collection<String>): List<String> =
        patron.findAll(texto).map { normalizar(it.groupValues[1]) }
            .filter { !esDeLaApp(it) && it in propias && datos[it].isNullOrBlank() }
            .distinct().map { "{$it}" }.toList()

    /** Las llaves del texto que no son variables conocidas (para avisar en la vista previa). */
    fun desconocidas(texto: String, propias: Collection<String> = emptyList()): List<String> =
        patron.findAll(texto).filter {
            val k = normalizar(it.groupValues[1])
            !esDeLaApp(k) && k !in propias
        }.map { it.value }.distinct().toList()

    fun usa(texto: String) = patron.containsMatchIn(texto)
}
