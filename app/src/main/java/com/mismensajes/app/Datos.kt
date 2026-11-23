package com.mismensajes.app

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.mis.nucleo.Registro
import org.json.JSONArray
import org.json.JSONObject

/** Modo de texto de un envío (se elige en cada envío). */
enum class ModoTexto(val codigo: String, val texto: String) {
    MISMO("M", "Mismo texto para todos"),
    POR_CONTACTO("C", "Un texto por contacto");

    companion object {
        fun de(c: String) = entries.firstOrNull { it.codigo == c } ?: MISMO
    }
}

/** Un destinatario de un envío. [texto] solo se usa en modo "un texto por contacto". */
data class Destino(val contactoId: Int, val texto: String = "", val enviado: Boolean = false)

/** Un envío programado: uno o más contactos, un momento y el texto (común o por contacto). */
data class Envio(
    val id: Int,
    val titulo: String = "",            // opcional, para reconocerlo en la lista
    val modo: ModoTexto = ModoTexto.MISMO,
    val texto: String = "",             // texto común (modo MISMO)
    val destinos: List<Destino> = emptyList(),
    val momento: Long,
    val alarma: Boolean = false,
    val activo: Boolean = true,         // false = ya venció (sonó)
    val cerrado: Boolean = false,       // true = se tocó "Hecho" después de vencer: sale del núcleo
    val pospuestoHasta: Long = 0
) {
    /** Cuándo vuelve a sonar (la posposición, si hay); 0 = ya no suena. */
    val proxima: Long get() = if (pospuestoHasta > 0) pospuestoHasta else if (activo) momento else 0L

    fun textoPara(d: Destino) = if (modo == ModoTexto.MISMO) texto else d.texto

    /** Destinos a los que todavía no se les envió. */
    val pendientes: List<Destino> get() = destinos.filter { !it.enviado }

    /** Se puede enviar: ya venció (o está pospuesto), no se cerró y queda alguien sin enviar. */
    val paraEnviar: Boolean get() = !cerrado && !activo && pendientes.isNotEmpty()
}

/** Envíos guardados en el celular (preferencias "envios"). */
object Envios {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("envios", Context.MODE_PRIVATE)

    fun todos(ctx: Context): List<Envio> {
        val arr = JSONArray(prefs(ctx).getString("l", "[]") ?: "[]")
        return List(arr.length()) { desdeJson(arr.getJSONObject(it)) }
    }

    fun obtener(ctx: Context, id: Int) = todos(ctx).firstOrNull { it.id == id }

    fun guardar(ctx: Context, e: Envio) = guardarTodos(ctx, todos(ctx).filter { it.id != e.id } + e)

    fun borrar(ctx: Context, id: Int) = guardarTodos(ctx, todos(ctx).filter { it.id != id })

    /** Ids siempre nuevos (nunca se reutiliza el de un envío borrado, para no cruzar alarmas viejas). */
    fun nuevoId(ctx: Context): Int {
        val p = prefs(ctx)
        val id = maxOf(p.getInt("ultimo_id", 0), todos(ctx).maxOfOrNull { it.id } ?: 0) + 1
        p.edit().putInt("ultimo_id", id).commit()
        return id
    }

    /** Programados (por próxima fecha) y vencidos (el más reciente primero). */
    fun ordenados(ctx: Context): Pair<List<Envio>, List<Envio>> {
        val (prog, venc) = todos(ctx).partition { it.proxima > 0 }
        return prog.sortedBy { it.proxima } to venc.sortedByDescending { it.momento }
    }

    /** Al borrar un contacto: se lo quita de todos los envíos. Devuelve cuántos envíos cambiaron. */
    fun quitarContacto(ctx: Context, contactoId: Int): Int {
        var cambiados = 0
        val nueva = todos(ctx).map { e ->
            if (e.destinos.none { it.contactoId == contactoId }) e
            else { cambiados++; e.copy(destinos = e.destinos.filter { it.contactoId != contactoId }) }
        }
        if (cambiados > 0) guardarTodos(ctx, nueva)
        return cambiados
    }

    fun usosDe(ctx: Context, contactoId: Int) = todos(ctx).count { e -> e.destinos.any { it.contactoId == contactoId } }

    /** "Ana, Juan y 2 más". */
    fun nombres(ctx: Context, e: Envio, max: Int = 3): String {
        val mapa = Contactos.todos(ctx).associateBy { it.id }
        val n = e.destinos.map { mapa[it.contactoId]?.etiqueta ?: "(contacto borrado)" }
        if (n.isEmpty()) return "(sin contactos)"
        return if (n.size <= max) n.joinToString(", ") else n.take(max).joinToString(", ") + " y ${n.size - max} más"
    }

    fun titulo(ctx: Context, e: Envio) = e.titulo.ifBlank { "Mensaje para " + nombres(ctx, e) }

    /** Marca (o desmarca) como enviado el destino de ese contacto. Devuelve el envío actualizado. */
    fun marcarEnviado(ctx: Context, id: Int, contactoId: Int, enviado: Boolean = true): Envio? {
        val e = obtener(ctx, id) ?: return null
        val nuevo = e.copy(destinos = e.destinos.map { if (it.contactoId == contactoId) it.copy(enviado = enviado) else it })
        guardar(ctx, nuevo)
        return nuevo
    }

    /** "Enviado a 1 de 3" ("" si todavía no se envió a nadie). */
    fun progreso(e: Envio): String {
        val n = e.destinos.count { it.enviado }
        return if (n == 0) "" else "Enviado a $n de ${e.destinos.size}"
    }

    fun resumenTexto(e: Envio) =
        if (e.modo == ModoTexto.MISMO) e.texto else "Un texto por contacto (${e.destinos.size})"

    private fun guardarTodos(ctx: Context, lista: List<Envio>) {
        val arr = JSONArray()
        lista.sortedBy { it.id }.forEach { arr.put(aJson(it)) }
        prefs(ctx).edit().putString("l", arr.toString()).commit()   // commit: también se usa desde receivers
    }

    private fun aJson(e: Envio): JSONObject {
        val d = JSONArray()
        e.destinos.forEach { d.put(JSONObject().put("c", it.contactoId).put("t", it.texto).put("e", it.enviado)) }
        return JSONObject().put("id", e.id).put("titulo", e.titulo).put("modo", e.modo.codigo).put("texto", e.texto)
            .put("dest", d).put("momento", e.momento).put("alarma", e.alarma).put("activo", e.activo)
            .put("cerrado", e.cerrado).put("posp", e.pospuestoHasta)
    }

    private fun desdeJson(o: JSONObject): Envio {
        val d = o.optJSONArray("dest") ?: JSONArray()
        return Envio(
            id = o.getInt("id"), titulo = o.optString("titulo"), modo = ModoTexto.de(o.optString("modo", "M")),
            texto = o.optString("texto"),
            destinos = List(d.length()) {
                val x = d.getJSONObject(it)
                Destino(x.getInt("c"), x.optString("t"), x.optBoolean("e", false))
            },
            momento = o.getLong("momento"), alarma = o.optBoolean("alarma", false),
            activo = o.optBoolean("activo", true), cerrado = o.optBoolean("cerrado", false),
            pospuestoHasta = o.optLong("posp", 0)
        )
    }
}

/** Contacto de la lista propia de la app (se carga a mano, como en Recordatorios). */
data class Contacto(val id: Int, val nombre: String, val numero: String) {
    val etiqueta get() = nombre.ifBlank { numero }
    /** WhatsApp pide formato internacional solo con dígitos (sin "+", espacios ni guiones). */
    fun numeroWhatsapp() = numero.filter { it.isDigit() }
}

/** Contactos guardados (preferencias "contactos", mismas claves que Recordatorios). */
object Contactos {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("contactos", Context.MODE_PRIVATE)

    fun todos(ctx: Context): List<Contacto> {
        val arr = JSONArray(prefs(ctx).getString("l", "[]") ?: "[]")
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            Contacto(o.optInt("id", 0), o.optString("nombre"), o.optString("numero"))
        }
    }

    fun ordenados(ctx: Context) = todos(ctx).sortedBy { it.etiqueta.lowercase() }

    fun obtener(ctx: Context, id: Int) = todos(ctx).firstOrNull { it.id == id }

    fun guardar(ctx: Context, c: Contacto) {
        val lista = todos(ctx).toMutableList()
        val i = lista.indexOfFirst { it.id == c.id }
        if (i >= 0) lista[i] = c else lista.add(c)
        guardarTodos(ctx, lista)
    }

    fun borrar(ctx: Context, id: Int) = guardarTodos(ctx, todos(ctx).filter { it.id != id })

    fun nuevoId(ctx: Context) = (todos(ctx).maxOfOrNull { it.id } ?: 0) + 1

    /** Otro contacto con el mismo número (comparando solo dígitos), o null. */
    fun duplicado(ctx: Context, numero: String, excepto: Int): Contacto? {
        val d = numero.filter { it.isDigit() }
        return todos(ctx).firstOrNull { it.id != excepto && it.numeroWhatsapp() == d }
    }

    private fun guardarTodos(ctx: Context, lista: List<Contacto>) {
        val arr = JSONArray()
        lista.forEach { arr.put(JSONObject().put("id", it.id).put("nombre", it.nombre).put("numero", it.numero)) }
        prefs(ctx).edit().putString("l", arr.toString()).commit()
    }
}

/**
 * Validación de un número escrito a mano (igual que Recordatorios): lo busca en la agenda del celular
 * (solo ese número, no se lista la agenda) y se fija si WhatsApp lo marca con su cuenta ("com.whatsapp").
 * Necesita READ_CONTACTS.
 */
object Agenda {
    data class Resultado(val nombre: String, val tieneWhatsapp: Boolean)

    fun buscar(ctx: Context, numero: String): Resultado? {
        val digitos = numero.filter { it.isDigit() }
        if (digitos.length < 6) return null
        // Variantes del número: como se escribió, con "+", solo dígitos y los últimos 10 dígitos.
        val variantes = listOf(numero.trim(), "+$digitos", digitos, digitos.takeLast(10)).distinct()
        for (v in variantes) {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(v))
            val encontrado = runCatching {
                ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getLong(0) to (c.getString(1) ?: "") else null
                }
            }.getOrNull() ?: continue
            val (idContacto, nombre) = encontrado
            Registro.anotar(ctx, "Validación WA: «$nombre» encontrado en la agenda")
            return Resultado(nombre, tieneWhatsapp(ctx, idContacto))
        }
        return null
    }

    private fun tieneWhatsapp(ctx: Context, idContacto: Long): Boolean = runCatching {
        ctx.contentResolver.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND " +
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} IN ('com.whatsapp', 'com.whatsapp.w4b')",
            arrayOf(idContacto.toString()), null)?.use { it.count > 0 } ?: false
    }.getOrDefault(false)
}
