package com.mismensajes.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lista de contactos con nombre (ej. "Familia", "Equipo"), para elegir varios de una vez en un envío.
 * Guarda solo los ids de los contactos: si se edita un contacto, la lista lo ve al día.
 * Al usarla en un envío, los contactos se **copian** al envío (como las plantillas): cambiar la lista
 * después no cambia envíos ya creados.
 */
data class Lista(val id: Int, val nombre: String, val contactos: List<Int>) {
    val etiqueta get() = nombre.ifBlank { "Lista $id" }
}

/** Listas guardadas en el celular (preferencias "listas"). */
object Listas {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("listas", Context.MODE_PRIVATE)

    fun todas(ctx: Context): List<Lista> {
        val arr = JSONArray(prefs(ctx).getString("l", "[]") ?: "[]")
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            val c = o.optJSONArray("c") ?: JSONArray()
            Lista(o.optInt("id", 0), o.optString("nombre"), List(c.length()) { i -> c.getInt(i) })
        }
    }

    fun ordenadas(ctx: Context) = todas(ctx).sortedBy { it.etiqueta.lowercase() }

    fun obtener(ctx: Context, id: Int) = todas(ctx).firstOrNull { it.id == id }

    fun guardar(ctx: Context, l: Lista) {
        val lista = todas(ctx).toMutableList()
        val i = lista.indexOfFirst { it.id == l.id }
        if (i >= 0) lista[i] = l else lista.add(l)
        guardarTodas(ctx, lista)
    }

    fun borrar(ctx: Context, id: Int) = guardarTodas(ctx, todas(ctx).filter { it.id != id })

    /** Ids siempre nuevos (nunca se reutiliza el de una lista borrada). */
    fun nuevoId(ctx: Context): Int {
        val p = prefs(ctx)
        val id = maxOf(p.getInt("ultimo_id", 0), todas(ctx).maxOfOrNull { it.id } ?: 0) + 1
        p.edit().putInt("ultimo_id", id).commit()
        return id
    }

    /** Otra lista con el mismo nombre (sin distinguir mayúsculas), o null. */
    fun mismoNombre(ctx: Context, nombre: String, excepto: Int): Lista? {
        val n = nombre.trim().lowercase()
        if (n.isEmpty()) return null
        return todas(ctx).firstOrNull { it.id != excepto && it.nombre.trim().lowercase() == n }
    }

    /** En cuántas listas está el contacto. */
    fun usosDe(ctx: Context, contactoId: Int) = todas(ctx).count { contactoId in it.contactos }

    /** Saca el contacto de todas las listas (al borrarlo). */
    fun quitarContacto(ctx: Context, contactoId: Int) {
        val todas = todas(ctx)
        if (todas.none { contactoId in it.contactos }) return
        guardarTodas(ctx, todas.map { it.copy(contactos = it.contactos.filter { c -> c != contactoId }) })
    }

    /** Contactos de la lista que todavía existen, en el orden de la lista de contactos. */
    fun contactosDe(ctx: Context, l: Lista): List<Contacto> = Contactos.ordenados(ctx).filter { it.id in l.contactos }

    /** "Ana, Juan y 2 más". */
    fun nombres(ctx: Context, l: Lista, max: Int = 3): String {
        val n = contactosDe(ctx, l).map { it.etiqueta }
        if (n.isEmpty()) return "(sin contactos)"
        return if (n.size <= max) n.joinToString(", ") else n.take(max).joinToString(", ") + " y ${n.size - max} más"
    }

    private fun guardarTodas(ctx: Context, lista: List<Lista>) {
        val arr = JSONArray()
        lista.sortedBy { it.id }.forEach { l ->
            arr.put(JSONObject().put("id", l.id).put("nombre", l.nombre).put("c", JSONArray(l.contactos)))
        }
        prefs(ctx).edit().putString("l", arr.toString()).commit()
    }
}

/**
 * Alta ([actual] = null) o edición de una lista: nombre + casillas con los contactos.
 * [iniciales]: contactos ya marcados en una lista nueva (ej. "Guardar como lista" desde un envío).
 */
fun Activity.editarLista(actual: Lista?, iniciales: List<Int> = emptyList(), alGuardar: (Lista?) -> Unit) {
    val todos = Contactos.ordenados(this)
    if (todos.isEmpty()) { avisar("Primero cargá contactos (botón «Agregar contacto»)"); return }
    val c = ContextThemeWrapper(this, temaDialogo())
    val dp = resources.displayMetrics.density
    val m = (20 * dp).toInt()
    val marcados = actual?.contactos ?: iniciales
    val nombre = EditText(c).apply { hint = "Nombre de la lista, ej.: Familia"; setText(actual?.nombre ?: "") }
    val casillas = todos.map { k ->
        CheckBox(c).apply { text = "${k.etiqueta} · ${k.numero}"; isChecked = k.id in marcados }
    }
    val caja = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(m, m / 2, m, 0)
        addView(nombre)
        addView(TextView(c).apply {
            text = "Contactos de la lista"
            textSize = 13f
            setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
        })
        casillas.forEach { addView(it) }
    }
    val b = AlertDialog.Builder(this, temaDialogo())
        .setTitle(if (actual == null) "Nueva lista" else "Editar lista")
        .setView(ScrollView(c).apply { addView(caja) })
        .setPositiveButton("Guardar") { _, _ ->
            val n = nombre.text.toString().trim()
            val elegidos = todos.filterIndexed { i, _ -> casillas[i].isChecked }.map { it.id }
            if (n.isEmpty()) { avisar("Falta el nombre de la lista"); return@setPositiveButton }
            if (elegidos.isEmpty()) { avisar("Marcá al menos un contacto"); return@setPositiveButton }
            val id = actual?.id ?: Listas.nuevoId(this)
            Listas.mismoNombre(this, n, id)?.let {
                avisar("Ya existe una lista llamada «${it.etiqueta}»")
                return@setPositiveButton
            }
            val nueva = Lista(id, n, elegidos)
            Listas.guardar(this, nueva)
            avisar("Lista «${nueva.etiqueta}» guardada (${elegidos.size} contacto(s))")
            alGuardar(nueva)
        }
        .setNegativeButton("Cancelar", null)
    if (actual != null) b.setNeutralButton("Borrar") { _, _ -> confirmarBorrarLista(actual) { alGuardar(null) } }
    b.show()
}

/** Pide confirmación y borra la lista (los contactos y los envíos no se tocan). */
fun Activity.confirmarBorrarLista(l: Lista, alBorrar: () -> Unit) {
    AlertDialog.Builder(this, temaDialogo())
        .setTitle("Borrar lista")
        .setMessage("¿Borrar la lista «${l.etiqueta}»? Los contactos y los envíos ya creados no se tocan.")
        .setPositiveButton("Borrar") { _, _ ->
            Listas.borrar(this, l.id)
            avisar("Lista borrada")
            alBorrar()
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

/**
 * Elige una lista y la suma a los contactos del envío ([seleccion]): si no hay ninguno elegido, la usa
 * directamente; si no, pregunta agregar o reemplazar. Los contactos se copian al envío.
 */
fun Activity.usarLista(seleccion: MutableList<Int>, alCambiar: () -> Unit) {
    val listas = Listas.ordenadas(this).filter { Listas.contactosDe(this, it).isNotEmpty() }
    if (listas.isEmpty()) {
        val b = AlertDialog.Builder(this, temaDialogo())
            .setTitle("Sin listas")
            .setNegativeButton("Cerrar", null)
        if (seleccion.isNotEmpty()) {
            b.setMessage("Todavía no hay listas. ¿Crear una con los contactos elegidos?")
                .setPositiveButton("Guardar como lista") { _, _ -> editarLista(null, seleccion.toList()) {} }
        } else {
            b.setMessage("Todavía no hay listas. Se crean en «Contactos» (pantalla principal) o con " +
                "«Guardar como lista» después de elegir los contactos del envío.")
        }
        b.show()
        return
    }
    val textos = listas.map { "${it.etiqueta} (${Listas.contactosDe(this, it).size}): ${Listas.nombres(this, it)}" }
    AlertDialog.Builder(this, temaDialogo())
        .setTitle("Usar lista")
        .setItems(textos.toTypedArray()) { _, i ->
            val l = listas[i]
            val ids = Listas.contactosDe(this, l).map { it.id }
            fun aplicar(reemplazar: Boolean) {
                val juntos = if (reemplazar) ids.toSet() else (seleccion + ids).toSet()
                // Conserva el orden de la lista de contactos (como al elegirlos a mano).
                val orden = Contactos.ordenados(this).map { it.id }.filter { it in juntos }
                val antes = seleccion.size
                seleccion.clear(); seleccion += orden
                alCambiar()
                avisar(if (reemplazar) "Contactos de «${l.etiqueta}»: ${orden.size}"
                       else "Se sumaron ${orden.size - antes} contacto(s) de «${l.etiqueta}»")
            }
            if (seleccion.isEmpty() || seleccion.toSet() == ids.toSet()) { aplicar(reemplazar = true); return@setItems }
            AlertDialog.Builder(this, temaDialogo())
                .setTitle("«${l.etiqueta}»")
                .setItems(arrayOf("Agregar a los ya elegidos", "Reemplazar los ya elegidos")) { _, j ->
                    aplicar(reemplazar = j == 1)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

/** Crea una lista con los contactos elegidos en el envío (se puede ajustar antes de guardar). */
fun Activity.guardarComoLista(seleccion: List<Int>) {
    if (seleccion.isEmpty()) { avisar("Elegí primero los contactos"); return }
    Listas.todas(this).firstOrNull { it.contactos.toSet() == seleccion.toSet() }?.let {
        avisar("Esos contactos ya forman la lista «${it.etiqueta}»")
        return
    }
    editarLista(null, seleccion) {}
}
