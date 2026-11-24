package com.mismensajes.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import com.mis.nucleo.R as RN

/** Texto reutilizable para los envíos (puede llevar variables, ej. "Hola {primer_nombre}"). */
data class Plantilla(val id: Int, val nombre: String, val texto: String) {
    /** Nombre para mostrar: si no tiene, el comienzo del texto. */
    val etiqueta: String get() = nombre.ifBlank {
        val t = texto.trim().replace(Regex("\\s+"), " ")
        if (t.length <= 30) t else t.take(30).trimEnd() + "…"
    }
}

/** Plantillas guardadas en el celular (preferencias "plantillas"). */
object Plantillas {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("plantillas", Context.MODE_PRIVATE)

    fun todas(ctx: Context): List<Plantilla> {
        val arr = JSONArray(prefs(ctx).getString("l", "[]") ?: "[]")
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            Plantilla(o.optInt("id", 0), o.optString("nombre"), o.optString("texto"))
        }
    }

    fun ordenadas(ctx: Context) = todas(ctx).sortedBy { it.etiqueta.lowercase() }

    fun obtener(ctx: Context, id: Int) = todas(ctx).firstOrNull { it.id == id }

    fun guardar(ctx: Context, p: Plantilla) {
        val lista = todas(ctx).toMutableList()
        val i = lista.indexOfFirst { it.id == p.id }
        if (i >= 0) lista[i] = p else lista.add(p)
        guardarTodas(ctx, lista)
    }

    fun borrar(ctx: Context, id: Int) = guardarTodas(ctx, todas(ctx).filter { it.id != id })

    /** Ids siempre nuevos (como en los envíos: nunca se reutiliza el de una plantilla borrada). */
    fun nuevoId(ctx: Context): Int {
        val p = prefs(ctx)
        val id = maxOf(p.getInt("ultimo_id", 0), todas(ctx).maxOfOrNull { it.id } ?: 0) + 1
        p.edit().putInt("ultimo_id", id).commit()
        return id
    }

    /** Otra plantilla con el mismo nombre (sin distinguir mayúsculas), o null. */
    fun mismoNombre(ctx: Context, nombre: String, excepto: Int): Plantilla? {
        val n = nombre.trim().lowercase()
        if (n.isEmpty()) return null
        return todas(ctx).firstOrNull { it.id != excepto && it.nombre.trim().lowercase() == n }
    }

    /** Plantilla con exactamente el mismo texto (sin contar espacios de los bordes), o null. */
    fun mismoTexto(ctx: Context, texto: String): Plantilla? {
        val t = texto.trim()
        if (t.isEmpty()) return null
        return todas(ctx).firstOrNull { it.texto.trim() == t }
    }

    private fun guardarTodas(ctx: Context, lista: List<Plantilla>) {
        val arr = JSONArray()
        lista.sortedBy { it.id }.forEach {
            arr.put(JSONObject().put("id", it.id).put("nombre", it.nombre).put("texto", it.texto))
        }
        prefs(ctx).edit().putString("l", arr.toString()).commit()
    }
}

/** Lista de plantillas: tocar = editar; mantener presionado = borrar. */
class PlantillasActivity : Activity() {
    private lateinit var lista: LinearLayout

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        title = "Plantillas"
        actionBar?.setDisplayHomeAsUpEnabled(true)
        val dp = resources.displayMetrics.density
        val m = (24 * dp).toInt()
        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val agregar = Button(this, null, 0, RN.style.Boton_Principal).apply {
            text = "Nueva plantilla"
            setOnClickListener { editarPlantilla(null) { armar() } }
        }
        val ayuda = TextView(this).apply {
            text = "Textos que se repiten, listos para usar en cualquier envío (botón «Usar plantilla» al " +
                "editar un envío; también se crean con «Guardar como plantilla»). Pueden llevar variables " +
                "(ej. {primer_nombre} o {saludo}), que se completan al abrir WhatsApp para cada contacto.\n" +
                "Tocá una plantilla para editarla; mantenela presionada para borrarla."
            textSize = 13f
            setTextColor(getColor(RN.color.texto2))
            setPadding(0, (12 * dp).toInt(), 0, (8 * dp).toInt())
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(LinearLayout(this@PlantillasActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(m, m / 2, m, m)
                addView(agregar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(ayuda)
                addView(lista)
            })
        })
        armar()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun armar() {
        lista.removeAllViews()
        val todas = Plantillas.ordenadas(this)
        if (todas.isEmpty()) {
            lista.addView(TextView(this).apply {
                text = "Todavía no hay plantillas. Tocá «Nueva plantilla»."
                textSize = 15f
                setTextColor(getColor(RN.color.texto3))
            })
            return
        }
        val dp = resources.displayMetrics.density
        todas.forEach { p ->
            lista.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(RN.drawable.campo)
                setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
                addView(TextView(this@PlantillasActivity).apply {
                    text = p.etiqueta
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(getColor(RN.color.texto))
                })
                addView(TextView(this@PlantillasActivity).apply {
                    text = p.texto
                    textSize = 14f
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(getColor(RN.color.texto2))
                    setPadding(0, (4 * dp).toInt(), 0, 0)
                })
                setOnClickListener {
                    editarPlantilla(Plantillas.obtener(this@PlantillasActivity, p.id)) { armar() }
                }
                setOnLongClickListener { confirmarBorrarPlantilla(p) { armar() }; true }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * dp).toInt() })
        }
    }
}

/** Contacto de ejemplo para la vista previa de una plantilla (no se guarda). */
private val CONTACTO_EJEMPLO = Contacto(0, "Ana María", "")

/**
 * Alta ([actual] = null) o edición de una plantilla: nombre, texto, "Insertar variable" y vista previa.
 * [textoInicial] precarga el texto de una plantilla nueva (ej. "Guardar como plantilla" desde un envío).
 * Con un error (falta el texto, nombre repetido) el diálogo queda abierto para corregir.
 */
fun Activity.editarPlantilla(actual: Plantilla?, textoInicial: String = "", alGuardar: (Plantilla?) -> Unit) {
    val c = ContextThemeWrapper(this, temaDialogo())
    val dp = resources.displayMetrics.density
    val m = (20 * dp).toInt()
    val nombre = EditText(c).apply {
        hint = "Nombre (opcional), ej.: Saludo de cumpleaños"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setText(actual?.nombre ?: "")
    }
    val texto = EditText(c).apply {
        hint = "Texto del mensaje"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        minLines = 3
        maxLines = 8
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        setText(actual?.texto ?: textoInicial)
    }
    val vista = TextView(c).apply {
        textSize = 13f
        setPadding(0, (6 * dp).toInt(), 0, 0)
        visibility = View.GONE
    }
    fun actualizarVista() {
        val t = texto.text.toString()
        if (!Variables.usa(t)) { vista.visibility = View.GONE; return }
        val propias = Variables.propias(this)
        val raras = Variables.desconocidas(t, propias)
        // Las variables propias se muestran con su nombre: «monto».
        vista.text = "Vista previa (ejemplo: ${CONTACTO_EJEMPLO.nombre}, ahora): " +
            Variables.aplicar(t, CONTACTO_EJEMPLO, java.time.ZonedDateTime.now(), propias.associateWith { "«$it»" }, propias) +
            if (raras.isEmpty()) "" else "\n⚠ No se reconoce ${raras.joinToString(", ")}: se envía tal cual."
        vista.visibility = View.VISIBLE
    }
    texto.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
        override fun afterTextChanged(s: Editable?) = actualizarVista()
    })
    val variable = Button(c).apply {
        text = "Insertar variable"
        isAllCaps = false
        setOnClickListener { insertarVariable(texto) { actualizarVista() } }
    }
    val caja = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(m, m / 2, m, 0)
        addView(nombre); addView(texto); addView(variable); addView(vista)
    }
    actualizarVista()
    val b = AlertDialog.Builder(this, temaDialogo())
        .setTitle(if (actual == null) "Nueva plantilla" else "Editar plantilla")
        .setView(ScrollView(c).apply { addView(caja) })
        .setPositiveButton("Guardar", null)   // se maneja abajo, para no cerrar con errores
        .setNegativeButton("Cancelar", null)
    if (actual != null) b.setNeutralButton("Borrar", null)
    val d = b.create()
    d.setOnShowListener {
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val t = texto.text.toString().trim()
            if (t.isEmpty()) { avisar("Falta el texto de la plantilla"); return@setOnClickListener }
            val id = actual?.id ?: Plantillas.nuevoId(this)
            val n = nombre.text.toString().trim()
            Plantillas.mismoNombre(this, n, id)?.let {
                avisar("Ya hay una plantilla con ese nombre")
                return@setOnClickListener
            }
            val nueva = Plantilla(id, n, t)
            Plantillas.guardar(this, nueva)
            avisar("Plantilla guardada")
            d.dismiss()
            alGuardar(nueva)
        }
        if (actual != null) d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            confirmarBorrarPlantilla(actual) { d.dismiss(); alGuardar(null) }
        }
    }
    d.show()
}

/**
 * Lista de plantillas para elegir una (con nombre: el nombre y el comienzo del texto).
 * Sin plantillas, o con "＋ Nueva plantilla", se crea una y se usa directamente.
 */
fun Activity.elegirPlantilla(alElegir: (Plantilla) -> Unit) {
    val todas = Plantillas.ordenadas(this)
    val crear: () -> Unit = { editarPlantilla(null) { p -> if (p != null) alElegir(p) } }
    if (todas.isEmpty()) {
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Usar plantilla")
            .setMessage("Todavía no hay plantillas. ¿Crear una ahora?")
            .setPositiveButton("Nueva plantilla") { _, _ -> crear() }
            .setNegativeButton("Cancelar", null)
            .show()
        return
    }
    val items = todas.map { p ->
        if (p.nombre.isBlank()) p.etiqueta
        else p.nombre + "\n" + Plantilla(0, "", p.texto).etiqueta
    } + "＋ Nueva plantilla"
    AlertDialog.Builder(this, temaDialogo())
        .setTitle("Usar plantilla")
        .setItems(items.toTypedArray()) { _, i -> if (i < todas.size) alElegir(todas[i]) else crear() }
        .setNegativeButton("Cancelar", null)
        .show()
}

/** Confirmación antes de borrar una plantilla (los envíos ya creados con ese texto no cambian). */
fun Activity.confirmarBorrarPlantilla(p: Plantilla, alBorrar: () -> Unit) {
    AlertDialog.Builder(this, temaDialogo())
        .setTitle("Borrar plantilla")
        .setMessage("¿Eliminar definitivamente la plantilla «${p.etiqueta}»? Los envíos que ya usan ese texto no cambian.")
        .setPositiveButton("Eliminar") { _, _ ->
            Plantillas.borrar(this, p.id)
            avisar("Plantilla eliminada")
            alBorrar()
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

/**
 * Lista de variables (las de la app y las propias, que vienen de la planilla); la elegida se inserta donde
 * está el cursor de [campo]. La usan los envíos y las plantillas.
 */
fun Activity.insertarVariable(campo: EditText, despues: () -> Unit = {}) {
    val claves = Variables.LISTA.map { it.clave } + Variables.propias(this)
    val items = (Variables.LISTA.map { "{${it.clave}}  ·  ${it.descripcion}" } +
        Variables.propias(this).map { "{$it}  ·  Dato propio (columna de la planilla o ficha del contacto)" }).toTypedArray()
    AlertDialog.Builder(this, temaDialogo())
        .setTitle("Insertar variable")
        .setItems(items) { _, i ->
            val v = "{${claves[i]}}"
            val largo = campo.text.length
            val ini = campo.selectionStart.takeIf { it in 0..largo } ?: largo
            val fin = campo.selectionEnd.takeIf { it in 0..largo } ?: largo
            campo.text.replace(minOf(ini, fin), maxOf(ini, fin), v)
            campo.requestFocus()
            campo.setSelection(minOf(ini, fin) + v.length)
            despues()
        }
        .setNegativeButton("Cancelar", null)
        .show()
}
