package com.mismensajes.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mis.nucleo.Registro
import java.time.LocalDate
import com.mis.nucleo.R as RN

/**
 * Lista propia de contactos: se cargan escribiendo el número a mano y se valida en WhatsApp (como Recordatorios).
 * Abajo, las listas de contactos con nombre (ej. "Familia") para elegir varios de una vez en un envío.
 */
class ContactosActivity : Activity() {
    private lateinit var lista: LinearLayout
    private lateinit var cajaListas: LinearLayout

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        title = "Contactos y listas"
        actionBar?.setDisplayHomeAsUpEnabled(true)
        val dp = resources.displayMetrics.density
        val m = (24 * dp).toInt()
        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cajaListas = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val nuevaLista = Button(this, null, 0, RN.style.Boton_Principal).apply {
            text = "Nueva lista"
            setOnClickListener { editarLista(null) { armar() } }
        }
        val ayudaListas = TextView(this).apply {
            text = "Grupos con nombre (ej. «Familia») para elegir varios contactos de una vez al armar un envío " +
                "(botón «Usar lista»). Tocá una lista para editarla; mantenela presionada para borrarla."
            textSize = 13f
            setTextColor(getColor(RN.color.texto2))
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        val agregar = Button(this, null, 0, RN.style.Boton_Principal).apply {
            text = "Agregar contacto"
            setOnClickListener { editarContacto(null) { armar() } }
        }
        val ayuda = TextView(this).apply {
            text = "Los contactos se cargan escribiendo el número a mano. Con «Validar en WA» se busca solo ese " +
                "número en la agenda del celular para traer el nombre y ver si tiene WhatsApp.\n" +
                "Casilla = forma parte del grupo Default: esos contactos se eligen solos en cada envío nuevo.\n" +
                "Menú ⋮ → «Importar…» / «Exportar…»: contactos desde o hacia un archivo CSV (Google Sheets: " +
                "Archivo > Descargar > CSV). Columnas: numero, nombre, lista, default, modo (whatsapp, sms o ambos)."
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
                addView(subtitulo("Listas"))
                addView(ayudaListas)
                addView(nuevaLista, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(cajaListas)
            })
        })
        armar()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_IMPORTAR, 0, "Importar…")
        menu.add(0, MENU_EXPORTAR, 1, "Exportar…")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { finish(); return true }
            MENU_IMPORTAR -> {
                // Selector de archivos de Android (sin permisos): CSV de Sheets, Excel o Drive.
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values",
                        "text/plain", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                }, PEDIDO_IMPORTAR)
                return true
            }
            MENU_EXPORTAR -> {
                if (Contactos.todos(this).isEmpty()) { avisar("No hay contactos para exportar"); return true }
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/csv"
                    putExtra(Intent.EXTRA_TITLE, "contactos-mensajes-${LocalDate.now()}.csv")
                }, PEDIDO_EXPORTAR)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(pedido: Int, resultado: Int, datos: Intent?) {
        super.onActivityResult(pedido, resultado, datos)
        val uri = datos?.data
        if (resultado != RESULT_OK || uri == null) return
        when (pedido) {
            PEDIDO_IMPORTAR -> vistaPreviaImportacion(uri) { armar() }
            PEDIDO_EXPORTAR -> {
                val n = Contactos.todos(this).size
                val ok = runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(CsvContactos.exportar(this).toByteArray()) }
                        ?: error("sin salida")
                }.isSuccess
                if (ok) Registro.anotar(this, "Exportar contactos (CSV): $n contacto(s)")
                avisar(if (ok) "Exportados $n contacto(s)" else "No se pudo guardar el archivo")
            }
        }
    }

    private companion object {
        const val MENU_IMPORTAR = 1
        const val MENU_EXPORTAR = 2
        const val PEDIDO_IMPORTAR = 11
        const val PEDIDO_EXPORTAR = 12
    }

    private fun armar() {
        armarListas()
        lista.removeAllViews()
        val todos = Contactos.ordenados(this)
        if (todos.isEmpty()) {
            lista.addView(TextView(this).apply { text = "Todavía no hay contactos cargados."; textSize = 15f })
            return
        }
        val dp = resources.displayMetrics.density
        todos.forEach { c ->
            lista.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false   // si no, el texto de 2 líneas baja y se corta
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(RN.drawable.campo)
                setPadding((6 * dp).toInt(), (4 * dp).toInt(), (14 * dp).toInt(), (4 * dp).toInt())
                addView(CheckBox(this@ContactosActivity).apply {
                    isChecked = c.porDefecto
                    contentDescription = "Grupo Default"
                    setOnCheckedChangeListener { _, v -> Contactos.marcarDefault(this@ContactosActivity, c.id, v) }
                })
                addView(TextView(this@ContactosActivity).apply {
                    text = "${c.etiqueta}\n${c.numero}" + if (c.modo != ModoEnvio.WHATSAPP) " · ${c.modo.corto}" else ""
                    textSize = 15f
                    setTextColor(getColor(RN.color.texto))
                    setPadding((4 * dp).toInt(), (6 * dp).toInt(), 0, (6 * dp).toInt())
                    setOnClickListener { editarContacto(Contactos.obtener(this@ContactosActivity, c.id)) { armar() } }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * dp).toInt() })
        }
    }

    /** Listas de contactos: tocar = editar; mantener presionado = borrar. */
    private fun armarListas() {
        cajaListas.removeAllViews()
        val todas = Listas.ordenadas(this)
        val dp = resources.displayMetrics.density
        if (todas.isEmpty()) {
            cajaListas.addView(TextView(this).apply {
                text = "Todavía no hay listas."
                textSize = 15f
                setPadding(0, (8 * dp).toInt(), 0, 0)
            })
            return
        }
        todas.forEach { l ->
            cajaListas.addView(TextView(this).apply {
                text = "${l.etiqueta} (${Listas.contactosDe(this@ContactosActivity, l).size})\n" +
                    Listas.nombres(this@ContactosActivity, l)
                textSize = 15f
                setTextColor(getColor(RN.color.texto))
                setBackgroundResource(RN.drawable.campo)
                setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
                setOnClickListener { Listas.obtener(this@ContactosActivity, l.id)?.let { editarLista(it) { armar() } } }
                setOnLongClickListener { confirmarBorrarLista(l) { armar() }; true }
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
    val porDefecto = CheckBox(c).apply {
        text = "Forma parte del grupo Default (se elige solo en cada envío nuevo)"
        isChecked = actual?.porDefecto ?: false
    }
    // Por dónde se le manda (en cada envío se puede cambiar solo para ese envío).
    val tituloModo = TextView(c).apply { text = "Enviarle por:"; textSize = 13f; setPadding(0, m / 2, 0, 0) }
    val modos = android.widget.RadioGroup(c).apply {
        orientation = android.widget.RadioGroup.VERTICAL
        ModoEnvio.entries.forEachIndexed { i, modo ->
            addView(android.widget.RadioButton(c).apply { id = 9100 + i; text = modo.texto })
        }
        check(9100 + (actual?.modo ?: ModoEnvio.WHATSAPP).ordinal)
    }
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
        addView(nombre); addView(numero); addView(validar); addView(resultadoWa); addView(tituloModo); addView(modos)
        addView(porDefecto); addView(ayuda)
    }
    val b = AlertDialog.Builder(this, temaDialogo())
        .setTitle(if (actual == null) "Agregar contacto" else "Editar contacto")
        .setView(ScrollView(c).apply { addView(caja) })
        .setPositiveButton("Guardar") { _, _ ->
            val num = numero.text.toString().trim()
            if (num.filter { it.isDigit() }.length < 6) { avisar("Falta el número (con código de país)"); return@setPositiveButton }
            val id = actual?.id ?: Contactos.nuevoId(this)
            Contactos.duplicado(this, num, id)?.let {
                avisar("Ya existe un contacto con ese número: ${it.etiqueta}")
                return@setPositiveButton
            }
            val modo = ModoEnvio.entries.getOrElse(modos.checkedRadioButtonId - 9100) { ModoEnvio.WHATSAPP }
            val nuevo = Contacto(id, nombre.text.toString().trim(), num, porDefecto.isChecked, modo)
            Contactos.guardar(this, nuevo)
            alGuardar(nuevo)
        }
        .setNegativeButton("Cancelar", null)
    if (actual != null) b.setNeutralButton("Borrar") { _, _ ->
        val usos = Envios.usosDe(this, actual.id)
        val enListas = Listas.usosDe(this, actual.id)
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Borrar contacto")
            .setMessage("¿Eliminar definitivamente a «${actual.etiqueta}» (${actual.numero})?" +
                (if (usos > 0) " Se quita de $usos envío(s) programado(s)." else "") +
                (if (enListas > 0) " Se quita de $enListas lista(s)." else ""))
            .setPositiveButton("Eliminar") { _, _ ->
                Envios.quitarContacto(this, actual.id)
                Listas.quitarContacto(this, actual.id)
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
    val textos = todos.map { (if (it.porDefecto) "★ " else "") + "${it.etiqueta} · ${it.numero}" }.toTypedArray()
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
