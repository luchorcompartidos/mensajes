package com.mismensajes.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.widget.ScrollView
import android.widget.TextView
import com.mis.nucleo.Registro
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

/**
 * Lector y escritor de CSV (etapas 0.13 y 0.14, `DEFINICIONES.md` A.4).
 * - Separador `,`, `;` o tabulación, detectado solo en la primera fila.
 * - Comillas con separadores, comillas dobles ("") y saltos de línea adentro, respetados.
 * - Archivo en UTF-8 (con o sin BOM) o, si no lo es, Windows-1252 (CSV de Excel en castellano).
 */
object Csv {
    fun decodificar(bytes: ByteArray): String {
        val sinBom = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            bytes.copyOfRange(3, bytes.size) else bytes
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(sinBom)).toString()
        } catch (e: CharacterCodingException) {
            String(sinBom, Charset.forName("windows-1252"))
        }
    }

    /** Separador más usado en la primera fila (fuera de comillas). */
    fun separador(texto: String): Char {
        val cuenta = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        var comillas = false
        for (ch in texto) {
            if (ch == '"') comillas = !comillas
            else if (!comillas && (ch == '\n' || ch == '\r')) break
            else if (!comillas && ch in cuenta) cuenta[ch] = cuenta.getValue(ch) + 1
        }
        return cuenta.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    fun leer(texto: String): List<List<String>> {
        val sep = separador(texto)
        val filas = mutableListOf<List<String>>()
        var fila = mutableListOf<String>()
        val campo = StringBuilder()
        var comillas = false
        var i = 0
        while (i < texto.length) {
            val ch = texto[i]
            if (comillas) {
                if (ch == '"') {
                    if (i + 1 < texto.length && texto[i + 1] == '"') { campo.append('"'); i++ } else comillas = false
                } else campo.append(ch)
            } else when (ch) {
                '"' -> comillas = true
                sep -> { fila.add(campo.toString()); campo.clear() }
                '\r', '\n' -> {
                    if (ch == '\r' && i + 1 < texto.length && texto[i + 1] == '\n') i++
                    fila.add(campo.toString()); campo.clear()
                    filas.add(fila); fila = mutableListOf()
                }
                else -> campo.append(ch)
            }
            i++
        }
        if (campo.isNotEmpty() || fila.isNotEmpty()) { fila.add(campo.toString()); filas.add(fila) }
        return filas
    }

    fun escribir(filas: List<List<String>>, sep: Char = ','): String = filas.joinToString("\r\n") { f ->
        f.joinToString(sep.toString()) { v ->
            if (v.any { it == sep || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
        }
    } + "\r\n"

    /** Encabezado o valor comparable: sin mayúsculas, tildes ni espacios de más. */
    fun normalizar(s: String): String =
        Normalizer.normalize(s.replace("\uFEFF", "").trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

/** Importación y exportación de contactos (columnas `numero`, `nombre`, `lista`, `default`, `modo`). */
object CsvContactos {
    private val COL_NUMERO = setOf("numero", "telefono", "celular", "whatsapp")
    private val COL_NOMBRE = setOf("nombre")
    private val COL_LISTA = setOf("lista", "listas")
    private val COL_DEFAULT = setOf("default", "grupo default")
    private val COL_MODO = setOf("modo", "canal", "enviar por")

    /** Valor de la columna `modo`: whatsapp / sms / ambos (vacío = no se indica). */
    fun leerModo(v: String): ModoEnvio? = when (Csv.normalizar(v).replace(" ", "")) {
        "" -> null
        "whatsapp", "wa", "w", "solowhatsapp" -> ModoEnvio.WHATSAPP
        "sms", "s", "solosms" -> ModoEnvio.SMS
        "ambos", "a", "whatsappysms", "whatsapp+sms", "smsywhatsapp" -> ModoEnvio.AMBOS
        else -> null
    }

    fun escribirModo(m: ModoEnvio) = when (m) { ModoEnvio.WHATSAPP -> "whatsapp"; ModoEnvio.SMS -> "sms"; ModoEnvio.AMBOS -> "ambos" }
    val SI = setOf("si", "s", "x", "1", "true", "verdadero", "yes")

    data class Fila(val fila: Int, val numero: String, val nombre: String, val listas: List<String>,
                    val porDefecto: Boolean, val existente: Contacto?, val modo: ModoEnvio? = null)

    data class Analisis(val filas: List<Fila>, val errores: List<String>, val avisos: List<String>,
                        val listasNuevas: List<String>, val errorGeneral: String? = null) {
        val nuevos get() = filas.count { it.existente == null }
        val existentes get() = filas.count { it.existente != null }
    }

    /** Lee el archivo y arma la vista previa, sin cambiar nada. */
    fun analizar(ctx: Context, texto: String): Analisis {
        val filas = Csv.leer(texto).filter { f -> f.any { it.isNotBlank() } }
        if (filas.isEmpty()) return Analisis(emptyList(), emptyList(), emptyList(), emptyList(), "El archivo está vacío.")
        val enc = filas[0].map { Csv.normalizar(it) }
        fun col(nombres: Set<String>) = enc.indexOfFirst { it in nombres }
        val iNum = col(COL_NUMERO); val iNom = col(COL_NOMBRE); val iLis = col(COL_LISTA); val iDef = col(COL_DEFAULT); val iModo = col(COL_MODO)
        if (iNum < 0) return Analisis(emptyList(), emptyList(), emptyList(), emptyList(),
            "Falta la columna «numero» en la primera fila (encabezados). Columnas encontradas: " +
                filas[0].joinToString(", ") { "«${it.trim()}»" } + ".")

        val actuales = Contactos.todos(ctx).associateBy { it.numeroWhatsapp() }
        val listasActuales = Listas.todas(ctx).map { Csv.normalizar(it.nombre) }.toSet()
        val vistos = mutableMapOf<String, Int>()
        val ok = mutableListOf<Fila>(); val errores = mutableListOf<String>(); val avisos = mutableListOf<String>()
        val listasNuevas = linkedMapOf<String, String>()
        filas.drop(1).forEachIndexed { idx, f ->
            val n = idx + 2   // fila 1 = encabezados
            fun v(i: Int) = if (i >= 0 && i < f.size) f[i].trim() else ""
            val crudo = v(iNum)
            when {
                crudo.isEmpty() -> { errores += "Fila $n: falta el número"; return@forEachIndexed }
                crudo.contains("E+", ignoreCase = true) -> {
                    errores += "Fila $n: número en formato científico ($crudo); en la planilla poné la columna como texto"
                    return@forEachIndexed
                }
            }
            val numero = Contacto(0, "", crudo).numeroWhatsapp()
            if (numero.length < 6) { errores += "Fila $n: número inválido ($crudo)"; return@forEachIndexed }
            vistos[numero]?.let { errores += "Fila $n: número repetido en el archivo (ya está en la fila $it)"; return@forEachIndexed }
            vistos[numero] = n
            if (numero.length <= 10) avisos += "Fila $n: $crudo parece no tener código de país (en Argentina, 549…)"
            val listas = v(iLis).split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { Csv.normalizar(it) }
            listas.forEach { l -> Csv.normalizar(l).let { k -> if (k !in listasActuales) listasNuevas.putIfAbsent(k, l) } }
            val modo = leerModo(v(iModo))
            if (modo == null && v(iModo).isNotEmpty()) avisos += "Fila $n: modo «${v(iModo)}» no reconocido (whatsapp, sms o ambos): queda como está"
            ok += Fila(n, numero, v(iNom), listas, Csv.normalizar(v(iDef)) in SI, actuales[numero], modo)
        }
        return Analisis(ok, errores, avisos, listasNuevas.values.toList())
    }

    /** Aplica la importación: suma y actualiza, nunca borra. Devuelve un resumen. */
    fun importar(ctx: Context, a: Analisis): String {
        val contactos = Contactos.todos(ctx).toMutableList()
        var proximo = (contactos.maxOfOrNull { it.id } ?: 0) + 1
        val idPorFila = mutableMapOf<Int, Int>()
        var actualizados = 0
        a.filas.forEach { f ->
            val i = contactos.indexOfFirst { it.numeroWhatsapp() == f.numero }
            if (i >= 0) {
                val c = contactos[i]
                val nuevo = c.copy(nombre = f.nombre.ifBlank { c.nombre }, porDefecto = c.porDefecto || f.porDefecto,
                    modo = f.modo ?: c.modo)
                if (nuevo != c) { contactos[i] = nuevo; actualizados++ }
                idPorFila[f.fila] = c.id
            } else {
                contactos += Contacto(proximo, f.nombre, f.numero, f.porDefecto, f.modo ?: ModoEnvio.WHATSAPP)
                idPorFila[f.fila] = proximo++
            }
        }
        Contactos.guardarTodos(ctx, contactos)

        // Listas: se crean las que falten y se agregan los contactos (sin sacar a nadie).
        val porNombre = Listas.todas(ctx).associateBy { Csv.normalizar(it.nombre) }.toMutableMap()
        val agregar = linkedMapOf<String, MutableList<Int>>()
        val nombreOriginal = mutableMapOf<String, String>()
        a.filas.forEach { f ->
            f.listas.forEach { l ->
                val k = Csv.normalizar(l)
                nombreOriginal.putIfAbsent(k, l)
                agregar.getOrPut(k) { mutableListOf() } += idPorFila.getValue(f.fila)
            }
        }
        var listasCreadas = 0
        agregar.forEach { (k, ids) ->
            val actual = porNombre[k]
            if (actual == null) {
                val nueva = Lista(Listas.nuevoId(ctx), nombreOriginal.getValue(k), ids.distinct())
                Listas.guardar(ctx, nueva); porNombre[k] = nueva; listasCreadas++
            } else {
                val suma = (actual.contactos + ids).distinct()
                if (suma.size != actual.contactos.size) Listas.guardar(ctx, actual.copy(contactos = suma))
            }
        }
        val resumen = "${a.nuevos} contacto(s) nuevo(s), $actualizados actualizado(s)" +
            (if (listasCreadas > 0) ", $listasCreadas lista(s) nueva(s)" else "")
        Registro.anotar(ctx, "Importar contactos (CSV): $resumen; ${a.errores.size} fila(s) con error")
        return resumen
    }

    /** CSV con todos los contactos: mismas columnas que la importación. */
    fun exportar(ctx: Context): String {
        val listas = Listas.ordenadas(ctx)
        val filas = mutableListOf(listOf("numero", "nombre", "lista", "default", "modo"))
        Contactos.ordenados(ctx).forEach { c ->
            filas += listOf(c.numero, c.nombre,
                listas.filter { c.id in it.contactos }.joinToString(", ") { it.etiqueta },
                if (c.porDefecto) "sí" else "no", escribirModo(c.modo))
        }
        return "﻿" + Csv.escribir(filas)   // BOM: Excel lo abre con tildes bien
    }
}

/** Lee el archivo elegido con el selector de Android (UTF-8 o Windows-1252); null si no se pudo. */
fun Activity.leerCsv(uri: Uri): String? =
    runCatching { contentResolver.openInputStream(uri)?.use { Csv.decodificar(it.readBytes()) } }.getOrNull()

/** Agrega "Título:\n• a\n• b" a [sb] (hasta 50 renglones); nada si [l] está vacía. */
fun bloqueCsv(sb: StringBuilder, titulo: String, l: List<String>) {
    if (l.isEmpty()) return
    sb.append("\n\n").append(titulo).append(":\n")
    l.take(50).forEach { sb.append("• ").append(it).append('\n') }
    if (l.size > 50) sb.append("… y ${l.size - 50} más\n")
}

/** Diálogo de vista previa común a las importaciones: detalle desplazable → Importar / Cancelar. */
fun Activity.dialogoImportacion(titulo: String, detalle: String, hayAlgo: Boolean,
                                importar: () -> String, alImportar: () -> Unit) {
    val dp = resources.displayMetrics.density
    val vista = ScrollView(this).apply {
        addView(TextView(this@dialogoImportacion).apply {
            text = detalle.trimEnd()
            textSize = 14f
            setPadding((22 * dp).toInt(), (12 * dp).toInt(), (22 * dp).toInt(), 0)
            setTextIsSelectable(true)
        })
    }
    val b = AlertDialog.Builder(this, temaDialogo()).setTitle(titulo).setView(vista)
    if (!hayAlgo) b.setPositiveButton("Cerrar", null)
    else b.setPositiveButton("Importar") { _, _ ->
        avisar("Importado: " + importar())
        alImportar()
    }.setNegativeButton("Cancelar", null)
    b.show()
}

/** Vista previa de la importación de contactos: "N nuevos · M ya existen · K con error" + detalle. */
fun Activity.vistaPreviaImportacion(uri: Uri, alImportar: () -> Unit) {
    val texto = leerCsv(uri) ?: run { avisar("No se pudo leer el archivo"); return }
    val a = CsvContactos.analizar(this, texto)
    val detalle = StringBuilder()
    if (a.errorGeneral != null) detalle.append(a.errorGeneral)
    else {
        detalle.append("${a.nuevos} nuevo(s) · ${a.existentes} ya existen (se actualiza el nombre) · ${a.errores.size} con error")
        if (a.listasNuevas.isNotEmpty()) detalle.append("\n\nListas nuevas: ").append(a.listasNuevas.joinToString(", "))
        bloqueCsv(detalle, "Errores (esas filas no se importan)", a.errores)
        bloqueCsv(detalle, "Revisar", a.avisos)
        detalle.append("\n\nSe suma y se actualiza; no se borra ningún contacto ni lista.")
    }
    dialogoImportacion("Importar contactos", detalle.toString(), a.filas.isNotEmpty(),
        { CsvContactos.importar(this, a) }, alImportar)
}
