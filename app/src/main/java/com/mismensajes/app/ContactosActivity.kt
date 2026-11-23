package com.mismensajes.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mis.nucleo.R as RN

/** Lista propia de contactos: se cargan escribiendo el número a mano y se valida en WhatsApp (como Recordatorios). */
class ContactosActivity : Activity() {
    private lateinit var lista: LinearLayout

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        title = "Contactos"
        actionBar?.setDisplayHomeAsUpEnabled(true)
        val dp = resources.displayMetrics.density
        val m = (24 * dp).toInt()
        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val agregar = Button(this, null, 0, RN.style.Boton_Principal).apply {
            text = "Agregar contacto"
            setOnClickListener { editarContacto(null) { armar() } }
        }
        val ayuda = TextView(this).apply {
            text = "Los contactos se cargan escribiendo el número a mano. Con «Validar en WA» se busca solo ese " +
                "número en la agenda del celular para traer el nombre y ver si tiene WhatsApp."
            textSize = 13f
            setTextColor(getColor(RN.color.texto2))
            setPadding(0, (12 * dp).toInt(), 0, (8 * dp).toInt())
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(LinearLayout(this@ContactosActivity).apply {
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
        val todos = Contactos.ordenados(this)
        if (todos.isEmpty()) {
            lista.addView(TextView(this).apply { text = "Todavía no hay contactos cargados."; textSize = 15f })
            return
        }
        val dp = resources.displayMetrics.density
        todos.forEach { c ->
            lista.addView(TextView(this).apply {
                text = "${c.etiqueta}\n${c.numero}"
                textSize = 15f
                setTextColor(getColor(RN.color.texto))
                setBackgroundResource(RN.drawable.campo)
                setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
                setOnClickListener { editarContacto(c) { armar() } }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * dp).toInt() })
        }
    }
}

/** Alta ([actual] = null) o edición de un contacto, con validación del número en WhatsApp. */
fun Activity.editarContacto(actual: Contacto?, alGuardar: (Contacto?) -> Unit) {
    val c = ContextThemeWrapper(this, temaDialogo())
    val m = (20 * resources.displayMetrics.density).toInt()
    val nombre = EditText(c).apply { hint = "Nombre (opcional)"; setText(actual?.nombre ?: "") }
    val numero = EditText(c).apply {
        hint = "Número, ej.: 5491122334455"
        inputType = InputType.TYPE_CLASS_PHONE
        setText(actual?.numero ?: "")
    }
    val ayuda = TextView(c).apply {
        text = "Para WhatsApp usá el formato internacional: código de país + número (en Argentina, 549 + característica + número)."
        textSize = 12f
    }
    val resultadoWa = TextView(c).apply { textSize = 13f; visibility = View.GONE }
    val validar = Button(c).apply {
        text = "Validar en WA y recuperar"
        setOnClickListener {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 3)
                avisar("Concedé el permiso de contactos y volvé a tocar «Validar en WA»")
                return@setOnClickListener
            }
            val res = Agenda.buscar(this@editarContacto, numero.text.toString())
            resultadoWa.visibility = View.VISIBLE
            resultadoWa.text = when {
                res == null -> "No se encontró ese número en la agenda del celular (WhatsApp tampoco tendrá nombre)."
                res.tieneWhatsapp -> "✓ Tiene WhatsApp · nombre recuperado: ${res.nombre}"
                else -> "Está en la agenda como «${res.nombre}», pero no figura en WhatsApp."
            }
            if (res != null && res.nombre.isNotBlank()) nombre.setText(res.nombre)
        }
    }
    val caja = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(m, m / 2, m, 0)
        addView(nombre); addView(numero); addView(validar); addView(resultadoWa); addView(ayuda)
    }
    val b = AlertDialog.Builder(this, temaDialogo())
        .setTitle(if (actual == null) "Agregar contacto" else "Editar contacto")
        .setView(caja)
        .setPositiveButton("Guardar") { _, _ ->
            val num = numero.text.toString().trim()
            if (num.filter { it.isDigit() }.length < 6) { avisar("Falta el número (con código de país)"); return@setPositiveButton }
            val id = actual?.id ?: Contactos.nuevoId(this)
            Contactos.duplicado(this, num, id)?.let {
                avisar("Ya existe un contacto con ese número: ${it.etiqueta}")
                return@setPositiveButton
            }
            val nuevo = Contacto(id, nombre.text.toString().trim(), num)
            Contactos.guardar(this, nuevo)
            alGuardar(nuevo)
        }
        .setNegativeButton("Cancelar", null)
    if (actual != null) b.setNeutralButton("Borrar") { _, _ ->
        val usos = Envios.usosDe(this, actual.id)
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Borrar contacto")
            .setMessage("¿Eliminar definitivamente a «${actual.etiqueta}» (${actual.numero})?" +
                if (usos > 0) " Se quita de $usos envío(s) programado(s)." else "")
            .setPositiveButton("Eliminar") { _, _ ->
                Envios.quitarContacto(this, actual.id)
                Contactos.borrar(this, actual.id)
                avisar("Contacto eliminado")
                alGuardar(null)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    b.show()
}

/** Ventana para elegir uno o más contactos de la lista propia (con alta rápida de uno nuevo). */
fun Activity.elegirContactos(seleccion: MutableList<Int>, alCambiar: () -> Unit) {
    val todos = Contactos.ordenados(this)
    if (todos.isEmpty()) {
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Sin contactos")
            .setMessage("Todavía no hay contactos cargados. ¿Agregar uno ahora?")
            .setPositiveButton("Agregar contacto") { _, _ ->
                editarContacto(null) { nuevo ->
                    if (nuevo != null) { seleccion += nuevo.id; alCambiar() }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
        return
    }
    val marcados = BooleanArray(todos.size) { todos[it].id in seleccion }
    val textos = todos.map { "${it.etiqueta} · ${it.numero}" }.toTypedArray()
    fun aplicar() {
        // Conserva el orden de la lista de contactos.
        seleccion.clear()
        todos.forEachIndexed { i, c -> if (marcados[i]) seleccion += c.id }
    }
    AlertDialog.Builder(this, temaDialogo())
        .setTitle("Contactos del envío")
        .setMultiChoiceItems(textos, marcados) { _, i, v -> marcados[i] = v }
        .setPositiveButton("Aceptar") { _, _ -> aplicar(); alCambiar() }
        .setNeutralButton("Nuevo contacto") { _, _ ->
            aplicar()
            editarContacto(null) { nuevo ->
                if (nuevo != null) seleccion += nuevo.id
                alCambiar()
                elegirContactos(seleccion, alCambiar)
            }
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

/** Subtítulo en negrita para pantallas armadas por código. */
fun Activity.subtitulo(t: String) = TextView(this).apply {
    text = t
    textSize = 17f
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(getColor(RN.color.texto))
    setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
}
