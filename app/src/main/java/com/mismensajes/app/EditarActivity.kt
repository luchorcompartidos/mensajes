package com.mismensajes.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import com.mis.nucleo.Ajustes
import com.mis.nucleo.Programador
import com.mis.nucleo.Registro
import com.mis.nucleo.Tiempo
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.mis.nucleo.R as RN

/** Alta y edición de un envío (recibe [Programador.EXTRA_ID] al editar). */
class EditarActivity : Activity() {
    private var original: Envio? = null
    private val seleccion = mutableListOf<Int>()
    private val textos = mutableMapOf<Int, String>()          // texto por contacto
    private val camposTexto = mutableMapOf<Int, EditText>()
    private var fecha: LocalDate = LocalDate.now()
    private var hora: LocalTime = LocalTime.now()

    private val fFecha = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.forLanguageTag("es-AR"))

    private lateinit var edTitulo: EditText
    private lateinit var edTexto: EditText
    private lateinit var rbPorContacto: RadioButton
    private lateinit var cajaTextos: LinearLayout
    private lateinit var swAlarma: Switch
    private lateinit var txtVista: TextView
    private var ultimoCampo: EditText? = null                  // campo de texto donde se inserta la variable

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        setContentView(R.layout.activity_editar)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        edTitulo = findViewById(R.id.edTitulo)
        edTexto = findViewById(R.id.edTexto)
        rbPorContacto = findViewById(R.id.rbPorContacto)
        cajaTextos = findViewById(R.id.cajaTextos)
        swAlarma = findViewById(R.id.swAlarma)
        txtVista = findViewById(R.id.txtVista)
        seguir(edTexto)
        findViewById<Button>(R.id.btnVariable).setOnClickListener { elegirVariable() }

        original = Envios.obtener(this, intent.getIntExtra(Programador.EXTRA_ID, -1))
        val e = original
        title = if (e == null) "Nuevo envío" else "Editar envío"
        if (e != null) {
            edTitulo.setText(e.titulo)
            edTexto.setText(e.texto)
            seleccion += e.destinos.map { it.contactoId }
            e.destinos.forEach { if (it.texto.isNotEmpty()) textos[it.contactoId] = it.texto }
            val z = Tiempo.zdt(e.momento)
            fecha = z.toLocalDate()
            hora = z.toLocalTime().withSecond(0).withNano(0)
            swAlarma.isChecked = e.alarma
            findViewById<TextView>(R.id.estadoEnvio).apply {
                val t = estadoParaEditar(e)
                visibility = if (t == null) View.GONE else View.VISIBLE
                text = t
            }
            findViewById<Button>(R.id.btnBorrar).apply {
                visibility = View.VISIBLE
                setOnClickListener { confirmarBorrado(e) }
            }
        } else {
            // Por defecto: la próxima hora en punto.
            val prox = ZonedDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
            fecha = prox.toLocalDate()
            hora = prox.toLocalTime()
            swAlarma.isChecked = Ajustes.alarmaPorDefecto(this)
            // Envío nuevo: arranca con el grupo Default de contactos.
            seleccion += Contactos.defaults(this).map { it.id }
        }
        findViewById<RadioGroup>(R.id.rgModo).apply {
            check(if (e?.modo == ModoTexto.POR_CONTACTO) R.id.rbPorContacto else R.id.rbMismo)
            setOnCheckedChangeListener { _, _ -> guardarCamposTexto(); mostrarModo() }
        }
        findViewById<Button>(R.id.btnContactos).setOnClickListener {
            guardarCamposTexto()
            elegirContactos(seleccion) { mostrarContactos(); mostrarModo() }
        }
        findViewById<Button>(R.id.btnFecha).setOnClickListener { elegirFecha() }
        findViewById<Button>(R.id.btnHora).setOnClickListener {
            elegirHora(hora) { t -> hora = t; mostrarFechaHora() }
        }
        findViewById<Button>(R.id.btnGuardar).setOnClickListener { guardar() }
        mostrarContactos()
        mostrarModo()
        mostrarFechaHora()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun estadoParaEditar(e: Envio): String? = when {
        e.pospuestoHasta > 0 -> "Está pospuesto hasta el ${Tiempo.completo(e.pospuestoHasta)}. Al guardar se cancela " +
            "la posposición y queda programado para la fecha y hora de abajo."
        e.cerrado -> "Este envío ya se cerró. Al guardar con una fecha futura se vuelve a programar."
        !e.activo -> "Este envío ya venció. Al guardar con una fecha futura se vuelve a programar."
        else -> null
    }

    private fun mostrarContactos() {
        val mapa = Contactos.todos(this).associateBy { it.id }
        seleccion.retainAll { it in mapa }   // por si se borró alguno
        findViewById<TextView>(R.id.txtContactos).text =
            if (seleccion.isEmpty()) "Ninguno elegido"
            else seleccion.joinToString("\n") { id -> mapa[id]!!.let { "• ${it.etiqueta} · ${it.numero}" } }
        findViewById<Button>(R.id.btnContactos).text = if (seleccion.isEmpty()) "Elegir contactos" else "Cambiar contactos"
    }

    /** Muestra el texto común o un campo por contacto según el modo elegido. */
    private fun mostrarModo() {
        armarCamposTexto()
        ultimoCampo = if (rbPorContacto.isChecked) camposTexto.values.firstOrNull() else edTexto
        actualizarVista()
    }

    private fun armarCamposTexto() {
        val porContacto = rbPorContacto.isChecked
        edTexto.visibility = if (porContacto) View.GONE else View.VISIBLE
        cajaTextos.visibility = if (porContacto) View.VISIBLE else View.GONE
        cajaTextos.removeAllViews()
        camposTexto.clear()
        if (!porContacto) return
        if (seleccion.isEmpty()) {
            cajaTextos.addView(TextView(this).apply {
                text = "Elegí los contactos para escribir un texto para cada uno."
                setTextColor(getColor(RN.color.texto2))
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
            })
            return
        }
        val dp = resources.displayMetrics.density
        seleccion.forEach { id ->
            val c = Contactos.obtener(this, id) ?: return@forEach
            cajaTextos.addView(TextView(this).apply {
                text = "Para ${c.etiqueta}"
                textSize = 13f
                setTextColor(getColor(RN.color.texto2))
                setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
            })
            val campo = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 2
                gravity = android.view.Gravity.TOP
                hint = "Mensaje para ${c.etiqueta}"
                // Arranca con el texto común si todavía no tiene uno propio.
                setText(textos[id] ?: edTexto.text.toString())
            }
            camposTexto[id] = campo
            seguir(campo)
            cajaTextos.addView(campo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Recuerda el último campo de texto tocado y mantiene al día la vista previa de las variables. */
    private fun seguir(campo: EditText) {
        campo.setOnFocusChangeListener { _, foco -> if (foco) { ultimoCampo = campo; actualizarVista() } }
        campo.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (campo === ultimoCampo) actualizarVista() }
        })
    }

    /** Si el texto usa variables: cómo queda para su contacto (el primero elegido, en "mismo texto") a la hora programada. */
    private fun actualizarVista() {
        val campo = ultimoCampo
        val texto = campo?.text?.toString().orEmpty()
        if (campo == null || !Variables.usa(texto)) { txtVista.visibility = View.GONE; return }
        val contactoId = if (campo === edTexto) seleccion.firstOrNull()
                         else camposTexto.entries.firstOrNull { it.value === campo }?.key
        val c = contactoId?.let { Contactos.obtener(this, it) }
        val raras = Variables.desconocidas(texto)
        txtVista.text = "Vista previa" + (c?.let { " para ${it.etiqueta}" } ?: "") + ": " +
            Variables.aplicar(texto, c, ZonedDateTime.of(fecha, hora, ZoneId.systemDefault())) +
            if (raras.isEmpty()) "" else "\n⚠ No se reconoce ${raras.joinToString(", ")}: se envía tal cual."
        txtVista.visibility = View.VISIBLE
    }

    /** Lista de variables; la elegida se inserta donde está el cursor del último campo de texto tocado. */
    private fun elegirVariable() {
        val campo = ultimoCampo ?: run { avisar("Elegí primero los contactos"); return }
        insertarVariable(campo) { actualizarVista() }
    }

    private fun guardarCamposTexto() {
        camposTexto.forEach { (id, campo) -> textos[id] = campo.text.toString() }
    }

    private fun mostrarFechaHora() {
        findViewById<Button>(R.id.btnFecha).text = fecha.format(fFecha)
        findViewById<Button>(R.id.btnHora).text = "%02d:%02d".format(hora.hour, hora.minute)
        actualizarVista()   // {fecha}, {hora}, {dia} y {saludo} dependen de la hora programada
    }

    private fun elegirFecha() {
        val dp = DatePickerDialog(this, temaDialogo(), { _, a, m, d ->
            fecha = LocalDate.of(a, m + 1, d)
            mostrarFechaHora()
        }, fecha.year, fecha.monthValue - 1, fecha.dayOfMonth)
        dp.datePicker.minDate = System.currentTimeMillis() - 1000
        dp.show()
    }

    private fun guardar() {
        guardarCamposTexto()
        val porContacto = rbPorContacto.isChecked
        val textoComun = edTexto.text.toString()
        if (seleccion.isEmpty()) { avisar("Elegí al menos un contacto"); return }
        if (!porContacto && textoComun.isBlank()) { avisar("Falta el texto del mensaje"); return }
        if (porContacto) {
            seleccion.firstOrNull { textos[it].isNullOrBlank() }?.let { id ->
                avisar("Falta el texto para ${Contactos.obtener(this, id)?.etiqueta ?: "un contacto"}")
                return
            }
        }
        val momento = ZonedDateTime.of(fecha, hora, ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (momento <= System.currentTimeMillis()) { avisar("Esa fecha y hora ya pasaron. Elegí un momento futuro."); return }

        val id = original?.id ?: Envios.nuevoId(this)
        // Se conservan las marcas de "enviado" (para reprogramar solo los pendientes), salvo que ya se
        // hubiera enviado a todos: entonces es una vuelta nueva y se empieza de cero.
        val previos = original?.destinos?.associateBy { it.contactoId } ?: emptyMap()
        val deCero = previos.isNotEmpty() && previos.values.all { it.enviado }
        val e = Envio(
            id = id,
            titulo = edTitulo.text.toString().trim(),
            modo = if (porContacto) ModoTexto.POR_CONTACTO else ModoTexto.MISMO,
            texto = textoComun,
            destinos = seleccion.map {
                Destino(it, if (porContacto) textos[it] ?: "" else "", enviado = !deCero && previos[it]?.enviado == true)
            },
            momento = momento,
            alarma = swAlarma.isChecked,
            whatsapp = original?.whatsapp ?: ""
        )
        // Cancela alarmas, posposición y notificación anteriores; después programa la nueva fecha.
        Programador.quitar(this, id)
        Envios.guardar(this, e)
        Registro.anotar(this, "#$id envío ${if (original == null) "creado" else "editado"}")
        Programador.programar(this, ManejadorMensajes.comoAviso(this, e))
        avisar("Programado para el ${Tiempo.completo(momento)}")
        finish()
    }

    private fun confirmarBorrado(e: Envio) {
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Borrar envío")
            .setMessage("¿Borrar «${Envios.titulo(this, e)}»?")
            .setPositiveButton("Borrar") { _, _ ->
                borrarEnvio(e.id)
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

/** Borra un envío: sus alarmas, su notificación y sus datos. */
fun Activity.borrarEnvio(id: Int) {
    Programador.quitar(this, id)
    Envios.borrar(this, id)
    Registro.anotar(this, "#$id envío borrado")
    avisar("Envío borrado")
}
