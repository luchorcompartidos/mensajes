package com.mismensajes.app

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.mis.nucleo.Ajustes
import com.mis.nucleo.Programador
import com.mis.nucleo.Recurrencia
import com.mis.nucleo.Regla
import com.mis.nucleo.Registro
import com.mis.nucleo.Repeticion
import com.mis.nucleo.Tiempo
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Importación de envíos desde CSV (etapa 0.14, `DEFINICIONES.md` A.3).
 * Una fila por destinatario; las filas con el mismo `titulo` + `fecha` + `hora` forman un envío.
 * Columnas: `titulo`, `fecha`, `hora`, `numero` o `nombre`, `texto` y, opcionales, `alarma`, `repeticion`, `veces`.
 * Cualquier otra columna es una variable propia de ese destinatario en ese envío ({turno}, {monto}):
 * se guarda en [Destino.datos] y pisa al valor del contacto. Suma y actualiza; nunca borra.
 */
object CsvEnvios {
    private val COL_TITULO = setOf("titulo", "asunto")
    private val COL_FECHA = setOf("fecha")
    private val COL_HORA = setOf("hora")
    private val COL_NUMERO = setOf("numero", "telefono", "celular", "whatsapp")
    private val COL_NOMBRE = setOf("nombre", "contacto")
    private val COL_TEXTO = setOf("texto", "mensaje")
    private val COL_ALARMA = setOf("alarma")
    private val COL_REPETICION = setOf("repeticion", "repetir", "se repite")
    private val COL_VECES = setOf("veces", "cantidad de veces", "terminar despues de")
    private val NO = setOf("no", "n", "0", "false", "falso")

    /** Destinatario de una fila: contacto existente ([contactoId] > 0) o número nuevo a crear como contacto. */
    data class Dest(val fila: Int, val contactoId: Int, val numeroNuevo: String, val texto: String,
                    val datos: Map<String, String> = emptyMap()) {
        val clave get() = if (contactoId > 0) "c$contactoId" else "n$numeroNuevo"
    }

    /**
     * Un envío del archivo. [alarma] null = la columna vino vacía (nuevo: Ajustes; existente: se conserva).
     * [reglaIndicada] false = sin repetición en el archivo (nuevo: no se repite; existente: se conserva).
     */
    data class Grupo(val filas: List<Int>, val titulo: String, val momento: Long, val alarma: Boolean?,
                     val regla: Regla, val reglaIndicada: Boolean, val dests: List<Dest>, val existente: Envio?)

    data class Analisis(val grupos: List<Grupo>, val contactosNuevos: Map<String, String>,
                        val errores: List<String>, val avisos: List<String>, val errorGeneral: String? = null,
                        val variables: List<String> = emptyList()) {
        val nuevos get() = grupos.count { it.existente == null }
        val existentes get() = grupos.count { it.existente != null }
    }

    private class FilaLeida(val n: Int, val titulo: String, val fecha: LocalDate, val elegido: Long,
                            val alarma: String, val repeticion: String, val veces: String, val dest: Dest)

    fun fecha(s: String): LocalDate? {
        val t = s.trim()
        Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})""").matchEntire(t)?.let { m ->
            val (d, mes, a) = m.destructured
            val anio = if (a.length == 2) 2000 + a.toInt() else a.toInt()
            return runCatching { LocalDate.of(anio, mes.toInt(), d.toInt()) }.getOrNull()
        }
        Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").matchEntire(t)?.let { m ->   // formato ISO (algunas planillas)
            val (a, mes, d) = m.destructured
            return runCatching { LocalDate.of(a.toInt(), mes.toInt(), d.toInt()) }.getOrNull()
        }
        return null
    }

    /** "18:30", "8:05", "18:30:00", "18.30" o "6:30 p. m.". */
    fun hora(s: String): LocalTime? {
        val m = Regex("""(\d{1,2})[:.](\d{2})(?:[:.]\d{2})?\s*(?:([ap])\.?\s*m\.?)?""", RegexOption.IGNORE_CASE)
            .matchEntire(s.trim()) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        when (m.groupValues[3].lowercase()) {
            "a" -> { if (h == 12) h = 0 }
            "p" -> { if (h < 12) h += 12 }
        }
        return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
    }

    /** Regla de la columna `repeticion` (vacía = null). Semanal, mensual y anual toman el día de [f]. */
    private fun regla(valor: String, f: LocalDate): Result<Regla?> {
        val v = Csv.normalizar(valor)
        return when {
            v.isEmpty() -> Result.success(null)
            v in setOf("no", "n", "ninguna", "no se repite", "nunca") -> Result.success(Regla.NINGUNA)
            v.startsWith("diari") || v == "todos los dias" || v == "cada dia" -> Result.success(Regla(Repeticion.DIARIA))
            v.startsWith("semanal") || v == "cada semana" ->
                Result.success(Regla(Repeticion.SEMANAL, dias = setOf(f.dayOfWeek.value)))
            v.startsWith("mensual") || v == "cada mes" -> Result.success(Regla(Repeticion.MENSUAL, diaMes = f.dayOfMonth))
            v.startsWith("anual") || v == "cada ano" -> Result.success(Regla(Repeticion.ANUAL, diaMes = f.dayOfMonth))
            else -> Result.failure(IllegalArgumentException(valor))
        }
    }

    /** Lee el archivo y arma la vista previa, sin cambiar nada. */
    fun analizar(ctx: Context, texto: String): Analisis {
        val filas = Csv.leer(texto).filter { f -> f.any { it.isNotBlank() } }
        if (filas.isEmpty()) return Analisis(emptyList(), emptyMap(), emptyList(), emptyList(), "El archivo está vacío.")
        val enc = filas[0].map { Csv.normalizar(it) }
        fun col(nombres: Set<String>) = enc.indexOfFirst { it in nombres }
        val iTit = col(COL_TITULO); val iFec = col(COL_FECHA); val iHor = col(COL_HORA); val iNum = col(COL_NUMERO)
        val iNom = col(COL_NOMBRE); val iTex = col(COL_TEXTO); val iAla = col(COL_ALARMA); val iRep = col(COL_REPETICION)
        val iVec = col(COL_VECES)
        val faltan = listOfNotNull("fecha".takeIf { iFec < 0 }, "hora".takeIf { iHor < 0 }, "texto".takeIf { iTex < 0 },
            "numero o nombre".takeIf { iNum < 0 && iNom < 0 })
        if (faltan.isNotEmpty()) return Analisis(emptyList(), emptyMap(), emptyList(), emptyList(),
            "Faltan columnas en la primera fila (encabezados): " + faltan.joinToString(", ") { "«$it»" } +
                ". Columnas encontradas: " + filas[0].joinToString(", ") { "«${it.trim()}»" } + ".")

        val contactos = Contactos.todos(ctx)
        val porNumero = contactos.associateBy { it.numeroWhatsapp() }
        val nuevos = linkedMapOf<String, String>()   // número normalizado → nombre (para crear el contacto)
        val errores = mutableListOf<String>(); val avisos = mutableListOf<String>()
        val vars = Csv.columnasVariables(filas[0], COL_TITULO + COL_FECHA + COL_HORA + COL_NUMERO + COL_NOMBRE + COL_TEXTO +
            COL_ALARMA + COL_REPETICION + COL_VECES, avisos)
        val leidas = mutableListOf<FilaLeida>()
        val zona = ZoneId.systemDefault()

        filas.drop(1).forEachIndexed { idx, f ->
            val n = idx + 2   // fila 1 = encabezados
            fun v(i: Int) = if (i >= 0 && i < f.size) f[i].trim() else ""
            val fe = fecha(v(iFec)) ?: run {
                errores += "Fila $n: fecha inválida («${v(iFec)}»); usá dd/mm/aaaa"; return@forEachIndexed
            }
            val ho = hora(v(iHor)) ?: run {
                errores += "Fila $n: hora inválida («${v(iHor)}»); usá hh:mm (24 h)"; return@forEachIndexed
            }
            val txt = if (iTex < f.size) f[iTex].trim('\r', '\n') else ""
            if (txt.isBlank()) { errores += "Fila $n: falta el texto"; return@forEachIndexed }
            val crudo = v(iNum); val nombre = v(iNom)
            val datos = Csv.valoresVariables(f, vars)
            val dest: Dest = when {
                crudo.isNotEmpty() -> {
                    if (crudo.contains("E+", ignoreCase = true)) {
                        errores += "Fila $n: número en formato científico ($crudo); en la planilla poné la columna como texto"
                        return@forEachIndexed
                    }
                    val num = Contacto(0, "", crudo).numeroWhatsapp()
                    if (num.length < 6) { errores += "Fila $n: número inválido ($crudo)"; return@forEachIndexed }
                    val c = porNumero[num]
                    if (c != null) Dest(n, c.id, "", txt, datos)
                    else {
                        if (num !in nuevos) {
                            if (num.length <= 10) avisos += "Fila $n: $crudo parece no tener código de país (en Argentina, 549…)"
                            nuevos[num] = nombre
                        } else if (nuevos[num].isNullOrBlank() && nombre.isNotBlank()) nuevos[num] = nombre
                        Dest(n, 0, num, txt, datos)
                    }
                }
                nombre.isNotEmpty() -> {
                    val k = Csv.normalizar(nombre)
                    val iguales = contactos.filter { Csv.normalizar(it.etiqueta) == k }
                    when (iguales.size) {
                        1 -> Dest(n, iguales[0].id, "", txt, datos)
                        0 -> { errores += "Fila $n: no hay ningún contacto llamado «$nombre» (poné el número)"; return@forEachIndexed }
                        else -> { errores += "Fila $n: hay ${iguales.size} contactos llamados «$nombre» (poné el número)"; return@forEachIndexed }
                    }
                }
                else -> { errores += "Fila $n: falta el número (o el nombre de un contacto)"; return@forEachIndexed }
            }
            val elegido = ZonedDateTime.of(fe, ho, zona).toInstant().toEpochMilli()
            leidas += FilaLeida(n, v(iTit), fe, elegido, v(iAla), v(iRep), v(iVec), dest)
        }

        // Agrupar por título + fecha + hora, en el orden del archivo.
        val ahora = System.currentTimeMillis()
        val envios = Envios.todos(ctx)
        val grupos = mutableListOf<Grupo>()
        leidas.groupBy { Csv.normalizar(it.titulo) + "|" + it.elegido }.values.forEach { fs ->
            val p = fs[0]
            val nFilas = fs.map { it.n }
            val nombreGrupo = "«${p.titulo.ifBlank { "(sin título)" }}» ${Tiempo.completo(p.elegido)}"
            /** Primer valor no vacío de la columna; avisa si otra fila del mismo envío dice otra cosa. */
            fun valor(nombreCol: String, de: (FilaLeida) -> String): String {
                val conValor = fs.filter { de(it).isNotEmpty() }
                val primero = conValor.firstOrNull() ?: return ""
                conValor.firstOrNull { Csv.normalizar(de(it)) != Csv.normalizar(de(primero)) }?.let {
                    avisos += "Fila ${it.n}: «$nombreCol» distinta de la fila ${primero.n} en el mismo envío; se usa la de la fila ${primero.n}"
                }
                return de(primero)
            }
            val ala = valor("alarma") { it.alarma }
            val alarma = when (Csv.normalizar(ala)) {
                "" -> null
                in CsvContactos.SI -> true
                in NO -> false
                else -> { avisos += "Fila ${p.n}: alarma «$ala» no reconocida (sí/no); se usa la de Ajustes"; null }
            }
            val rep = valor("repeticion") { it.repeticion }
            var reglaArch = regla(rep, p.fecha).getOrElse {
                errores += "Filas ${nFilas.joinToString()}: repetición «$rep» no reconocida (diaria, semanal, mensual, anual o no)"
                return@forEach
            }
            val vec = valor("veces") { it.veces }
            if (vec.isNotEmpty()) {
                val limite = vec.toIntOrNull()
                if (limite == null || limite < 1) {
                    errores += "Filas ${nFilas.joinToString()}: «veces» debe ser un número mayor que 0 («$vec»)"; return@forEach
                }
                val r = reglaArch
                if (r != null && r.repite) reglaArch = r.copy(limite = limite)
                else avisos += "Fila ${p.n}: «veces» se ignora porque el envío no se repite"
            }
            val candidata = reglaArch ?: Regla.NINGUNA
            val momento = Recurrencia.ajustarInicio(candidata, p.elegido, ahora)
            val existente = envios.firstOrNull { e ->
                !e.cerrado && e.origen == 0 && Csv.normalizar(e.titulo) == Csv.normalizar(p.titulo) &&
                    (e.momento == p.elegido || e.momento == momento)
            }
            if (existente == null && !candidata.repite && p.elegido <= ahora) {
                errores += "Filas ${nFilas.joinToString()}: $nombreGrupo ya pasó; poné una fecha futura"; return@forEach
            }
            if (existente == null && candidata.repite && momento != p.elegido) {
                avisos += "Fila ${p.n}: $nombreGrupo se repite; empieza el ${Tiempo.completo(momento)}"
            }
            // El mismo destinatario dos veces en el mismo envío: vale la primera fila.
            val vistos = mutableMapOf<String, Int>()
            val dests = fs.mapNotNull { fl ->
                val previa = vistos[fl.dest.clave]
                if (previa != null) { errores += "Fila ${fl.n}: ese destinatario ya está en este envío (fila $previa)"; null }
                else { vistos[fl.dest.clave] = fl.n; fl.dest }
            }
            grupos += Grupo(nFilas, p.titulo, if (existente != null) existente.momento else momento, alarma,
                if (reglaArch == null && existente != null) existente.regla else candidata,
                reglaArch != null, dests, existente)
        }
        // Variables propias que el texto usa y a ese destinatario le faltan (se envían vacías).
        val propias = (Variables.propias(ctx) + vars.values).distinct()
        val porId = contactos.associateBy { it.id }
        grupos.forEach { g ->
            g.dests.forEach { d ->
                val f = Variables.faltantes(d.texto, Variables.datos(porId[d.contactoId], null) + d.datos, propias)
                if (f.isNotEmpty()) avisos += "Fila ${d.fila}: sin valor para ${f.joinToString(", ")} (se envía vacío)"
            }
        }
        // Solo se crean los contactos nuevos que quedaron en algún envío válido.
        val usados = grupos.flatMap { g -> g.dests.map { it.numeroNuevo } }.toSet()
        return Analisis(grupos, nuevos.filterKeys { it in usados }, errores, avisos, variables = vars.values.toList())
    }

    /** Junta textos: si todos son iguales, "mismo texto"; si no, "un texto por contacto". */
    private fun armar(destinos: List<Pair<Destino, String>>): Triple<ModoTexto, String, List<Destino>> {
        val textos = destinos.map { it.second }.distinct()
        return if (textos.size <= 1) Triple(ModoTexto.MISMO, textos.firstOrNull() ?: "", destinos.map { it.first.copy(texto = "") })
        else Triple(ModoTexto.POR_CONTACTO, "", destinos.map { (d, t) -> d.copy(texto = t) })
    }

    /** Aplica la importación: crea contactos y envíos nuevos y actualiza los que ya existen. Devuelve un resumen. */
    fun importar(ctx: Context, a: Analisis): String {
        // 1. Contactos nuevos (números que no estaban en la app).
        val contactos = Contactos.todos(ctx).toMutableList()
        var proximo = (contactos.maxOfOrNull { it.id } ?: 0) + 1
        val idNuevo = mutableMapOf<String, Int>()
        a.contactosNuevos.forEach { (num, nombre) ->
            val ya = contactos.firstOrNull { it.numeroWhatsapp() == num }
            if (ya != null) idNuevo[num] = ya.id
            else { contactos += Contacto(proximo, nombre, num); idNuevo[num] = proximo++ }
        }
        if (a.contactosNuevos.isNotEmpty()) Contactos.guardarTodos(ctx, contactos)
        fun idDe(d: CsvEnvios.Dest) = if (d.contactoId > 0) d.contactoId else idNuevo.getValue(d.numeroNuevo)
        // Cada destinatario arranca con el modo de su contacto (WhatsApp / SMS / ambos).
        val modoDe = contactos.associate { it.id to it.modo }
        fun destino(id: Int, datos: Map<String, String>) = Destino(id, canal = modoDe[id] ?: ModoEnvio.WHATSAPP, datos = datos)

        // 2. Envíos.
        var creados = 0; var actualizados = 0
        a.grupos.forEach { g ->
            val desdeArchivo = g.dests.associate { idDe(it) to it.texto }
            val datosArchivo = g.dests.associate { idDe(it) to it.datos }
            val actual = g.existente?.let { Envios.obtener(ctx, it.id) }?.takeIf { !it.cerrado }
            if (actual == null) {
                val (modo, texto, destinos) = armar(desdeArchivo.map { (id, t) -> destino(id, datosArchivo[id].orEmpty()) to t })
                val e = Envio(id = Envios.nuevoId(ctx), titulo = g.titulo, modo = modo, texto = texto, destinos = destinos,
                    momento = g.momento, alarma = g.alarma ?: Ajustes.alarmaPorDefecto(ctx), regla = g.regla)
                Envios.guardar(ctx, e)
                Registro.anotar(ctx, "#${e.id} envío importado (CSV)")
                Programador.programar(ctx, ManejadorMensajes.comoAviso(ctx, e))
                creados++
            } else {
                // Los ya enviados conservan su texto y sus variables; los pendientes toman los del archivo
                // (una celda vacía no borra el valor); los nuevos se suman al final.
                val previos = actual.destinos.map { d ->
                    val pendiente = !d.listo && d.contactoId in desdeArchivo
                    val t = if (pendiente) desdeArchivo.getValue(d.contactoId) else actual.textoPara(d)
                    (if (pendiente) d.copy(datos = d.datos + datosArchivo[d.contactoId].orEmpty()) else d) to t
                }
                val sumados = desdeArchivo.filterKeys { id -> actual.destinos.none { it.contactoId == id } }
                    .map { (id, t) -> destino(id, datosArchivo[id].orEmpty()) to t }
                val (modo, texto, destinos) = armar(previos + sumados)
                var e = actual.copy(modo = modo, texto = texto, destinos = destinos)
                if (actual.activo) {
                    if (g.alarma != null) e = e.copy(alarma = g.alarma)
                    if (g.reglaIndicada && g.regla != actual.regla) {
                        e = e.copy(regla = g.regla, momento = Recurrencia.ajustarInicio(g.regla, actual.momento, System.currentTimeMillis()),
                            ocurrencias = if (actual.regla.repite && g.regla.repite) actual.ocurrencias else 0)
                    }
                }
                if (e != actual) {
                    Envios.guardar(ctx, e)
                    Registro.anotar(ctx, "#${e.id} envío actualizado (CSV)")
                    if (e.activo) Programador.programar(ctx, ManejadorMensajes.comoAviso(ctx, e))
                    actualizados++
                }
            }
        }
        val resumen = "$creados envío(s) nuevo(s), $actualizados actualizado(s)" +
            (if (a.contactosNuevos.isNotEmpty()) ", ${a.contactosNuevos.size} contacto(s) nuevo(s)" else "")
        Registro.anotar(ctx, "Importar envíos (CSV): $resumen; ${a.errores.size} error(es)")
        return resumen
    }
}

/** Vista previa de la importación de envíos: "N nuevos · M ya existen · K con error" + detalle → Importar / Cancelar. */
fun Activity.vistaPreviaEnvios(uri: Uri, alImportar: () -> Unit) {
    val texto = leerCsv(uri) ?: run { avisar("No se pudo leer el archivo"); return }
    val a = CsvEnvios.analizar(this, texto)
    val detalle = StringBuilder()
    if (a.errorGeneral != null) detalle.append(a.errorGeneral)
    else {
        detalle.append("${a.nuevos} envío(s) nuevo(s) · ${a.existentes} ya existen (se actualizan) · ${a.errores.size} con error")
        if (a.contactosNuevos.isNotEmpty()) detalle.append("\n\nContactos nuevos (se crean): ")
            .append(a.contactosNuevos.entries.joinToString(", ") { (num, nom) -> nom.ifBlank { num } })
        if (a.variables.isNotEmpty()) detalle.append("\n\nVariables propias (columnas extra): ")
            .append(a.variables.joinToString(", ") { "{$it}" })
            .append(". Valen para ese destinatario en ese envío y pisan a las del contacto.")
        bloqueCsv(detalle, "Envíos", a.grupos.map { g ->
            val e = g.existente
            val cabeza = "${g.titulo.ifBlank { "(sin título)" }} — ${Tiempo.completo(g.momento)}"
            if (e == null) {
                val textos = g.dests.map { it.texto }.distinct().size
                "$cabeza · ${g.dests.size} destinatario(s) · " + (if (textos <= 1) "mismo texto" else "un texto por contacto") +
                    (if (g.alarma ?: Ajustes.alarmaPorDefecto(this)) " · ⏰" else "") +
                    (if (g.regla.repite) " · 🔁 " + Recurrencia.texto(g.regla, g.momento) else "")
            } else {
                val nuevosDest = g.dests.count { d -> d.contactoId <= 0 || e.destinos.none { it.contactoId == d.contactoId } }
                "$cabeza · ya existe: " + (if (nuevosDest > 0) "+$nuevosDest destinatario(s), " else "") +
                    "se actualizan los textos pendientes" + if (!e.activo) " (ya venció)" else ""
            }
        })
        bloqueCsv(detalle, "Errores (esas filas no se importan)", a.errores)
        bloqueCsv(detalle, "Revisar", a.avisos)
        detalle.append("\n\nSe suma y se actualiza; no se borra ningún envío ni contacto. " +
            "Los contactos que ya existen conservan su nombre.")
    }
    dialogoImportacion("Importar envíos", detalle.toString(), a.grupos.isNotEmpty(),
        { CsvEnvios.importar(this, a) }, alImportar)
}
