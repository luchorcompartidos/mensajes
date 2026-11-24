package com.mismensajes.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MenuItem
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.mis.nucleo.Ajustes
import com.mis.nucleo.Programador
import com.mis.nucleo.Recurrencia
import com.mis.nucleo.Regla
import com.mis.nucleo.Repeticion
import com.mis.nucleo.Sms
import com.mis.nucleo.Registro
import com.mis.nucleo.Tiempo
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.mis.nucleo.R as RN

private const val PEDIDO_SMS = 6

/** Alta y edición de un envío (recibe [Programador.EXTRA_ID] al editar). */
class EditarActivity : Activity() {
    private var original: Envio? = null
    private val seleccion = mutableListOf<Int>()
    private val textos = mutableMapOf<Int, String>()          // texto por contacto
    private val canales = mutableMapOf<Int, ModoEnvio>()       // por dónde va cada destinatario (solo en este envío)
    private var detalleSms = false                             // se tocó el contador: muestra el detalle
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
    private lateinit var txtSms: TextView
    private var autoAbrir: Boolean? = null                     // apertura automática: null = según Ajustes
    private var smsAuto = false                                // SMS: false = "Preguntar antes", true = "Automático"
    private lateinit var btnSmsAuto: Button
    private var ultimoCampo: EditText? = null                  // campo de texto donde se inserta la variable

    // Repetición (igual que Recordatorios)
    private lateinit var spRep: Spinner
    private lateinit var filaDias: LinearLayout
    private val checks = mutableListOf<CheckBox>()
    private lateinit var spOrdinal: Spinner
    private lateinit var spDiaSemana: Spinner
    private lateinit var filaNSemana: LinearLayout
    private lateinit var spTerminar: Spinner
    private lateinit var edVeces: EditText
    private lateinit var filaTerminar: LinearLayout
    private lateinit var txtRep: TextView

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
        txtSms = findViewById(R.id.txtSms)
        txtSms.setOnClickListener { detalleSms = !detalleSms; actualizarSms() }
        btnSmsAuto = findViewById(R.id.btnSmsAuto)
        btnSmsAuto.setOnClickListener { elegirSmsAuto() }
        findViewById<TextView>(R.id.txtContactos).setOnClickListener { elegirCanal() }
        seguir(edTexto)
        findViewById<Button>(R.id.btnVariable).setOnClickListener { elegirVariable() }
        findViewById<Button>(R.id.btnUsarPlantilla).setOnClickListener { usarPlantilla() }
        findViewById<Button>(R.id.btnGuardarPlantilla).setOnClickListener { guardarComoPlantilla() }
        armarRepeticion()

        original = Envios.obtener(this, intent.getIntExtra(Programador.EXTRA_ID, -1))
        val e = original
        title = if (e == null) "Nuevo envío" else "Editar envío"
        if (e != null) {
            edTitulo.setText(e.titulo)
            edTexto.setText(e.texto)
            seleccion += e.destinos.map { it.contactoId }
            e.destinos.forEach { if (it.texto.isNotEmpty()) textos[it.contactoId] = it.texto }
            e.destinos.forEach { canales[it.contactoId] = it.canal }
            val z = Tiempo.zdt(e.momento)
            fecha = z.toLocalDate()
            hora = z.toLocalTime().withSecond(0).withNano(0)
            swAlarma.isChecked = e.alarma
            autoAbrir = e.autoAbrir
            smsAuto = e.smsAuto
            mostrarRegla(e.regla)
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
            spOrdinal.setSelection(((fecha.dayOfMonth - 1) / 7).coerceAtMost(3))
            spDiaSemana.setSelection(fecha.dayOfWeek.value - 1)
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
        findViewById<Button>(R.id.btnUsarLista).setOnClickListener {
            guardarCamposTexto()
            usarLista(seleccion) { mostrarContactos(); mostrarModo() }
        }
        findViewById<Button>(R.id.btnGuardarLista).setOnClickListener { guardarComoLista(seleccion) }
        findViewById<Button>(R.id.btnFecha).setOnClickListener { elegirFecha() }
        findViewById<Button>(R.id.btnHora).setOnClickListener {
            elegirHora(hora) { t -> hora = t; mostrarFechaHora() }
        }
        findViewById<Button>(R.id.btnGuardar).setOnClickListener { guardar() }
        findViewById<Button>(R.id.btnAutoAbrir).setOnClickListener { elegirAutoAbrir() }
        mostrarAutoAbrir()
        mostrarContactos()
        mostrarModo()
        mostrarFechaHora()
        actualizarRepeticion()
    }

    private fun spinner(opciones: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@EditarActivity, android.R.layout.simple_spinner_dropdown_item, opciones)
    }

    private fun alElegir(sp: Spinner, accion: (Int) -> Unit) {
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = accion(pos)
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    /** Controles de repetición: tipo, días de la semana, "el n.º día del mes" y "terminar después de X veces". */
    private fun armarRepeticion() {
        spRep = findViewById(R.id.spRepeticion)
        filaDias = findViewById(R.id.filaDias)
        txtRep = findViewById(R.id.txtRepeticion)
        Recurrencia.LETRAS_DIAS.forEachIndexed { i, letra ->
            val cb = CheckBox(this).apply { text = letra; tag = i + 1 }
            filaDias.addView(cb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            checks.add(cb)
        }
        spOrdinal = spinner(Recurrencia.ORDINALES)
        spDiaSemana = spinner(Recurrencia.DIAS_SEMANA)
        filaNSemana = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@EditarActivity).apply { text = "El"; setTextColor(getColor(RN.color.texto)) })
            addView(spOrdinal, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(spDiaSemana, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
        }
        spTerminar = spinner(listOf("Nunca", "Después de X veces"))
        edVeces = EditText(this).apply { hint = "X"; inputType = InputType.TYPE_CLASS_NUMBER; visibility = View.GONE }
        filaTerminar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@EditarActivity).apply { text = "Terminar:"; setTextColor(getColor(RN.color.texto)) })
            addView(spTerminar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
            addView(edVeces, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        findViewById<LinearLayout>(R.id.filaExtra).apply { addView(filaNSemana); addView(filaTerminar) }
        alElegir(spTerminar) { pos -> edVeces.visibility = if (pos == 1) View.VISIBLE else View.GONE }
        spRep.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            Repeticion.entries.map { it.etiqueta })
        alElegir(spRep) { actualizarRepeticion() }
    }

    private fun mostrarRegla(r: Regla) {
        spRep.setSelection(r.tipo.ordinal)
        checks.forEach { it.isChecked = (it.tag as Int) in r.dias }
        spOrdinal.setSelection((r.semanaMes - 1).coerceIn(0, 4))
        spDiaSemana.setSelection((r.diaSemana - 1).coerceIn(0, 6))
        if (r.limite > 0) { spTerminar.setSelection(1); edVeces.setText(r.limite.toString()) }
    }

    private fun repeticionElegida() = Repeticion.entries[spRep.selectedItemPosition]

    private fun actualizarRepeticion() {
        val rep = repeticionElegida()
        val semanal = rep == Repeticion.SEMANAL
        filaDias.visibility = if (semanal) View.VISIBLE else View.GONE
        if (semanal && checks.none { it.isChecked }) checks[fecha.dayOfWeek.value - 1].isChecked = true
        filaNSemana.visibility = if (rep == Repeticion.NSEMANA) View.VISIBLE else View.GONE
        filaTerminar.visibility = if (rep == Repeticion.NINGUNA) View.GONE else View.VISIBLE
        val o = original
        txtRep.visibility = if (rep == Repeticion.NINGUNA) View.GONE else View.VISIBLE
        txtRep.text = "Cada vez que llega la fecha, esa vez queda aparte en «Ya vencidos» para enviarla, " +
            "y este envío sigue programado para la próxima." +
            if (o != null && o.repite && o.regla.limite > 0) "\nYa ocurrió ${o.ocurrencias} de ${o.regla.limite} veces." else ""
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun estadoParaEditar(e: Envio): String? = when {
        e.pospuestoHasta > 0 -> "Está pospuesto hasta el ${Tiempo.completo(e.pospuestoHasta)}. Al guardar se cancela " +
            "la posposición y queda programado para la fecha y hora de abajo."
        e.origen > 0 && !e.activo -> "Es una vez (la del ${Tiempo.completo(e.momento)}) de un envío que se repite. " +
            "Los cambios acá no cambian el envío que se repite." +
            if (e.cerrado) " Ya se cerró: al guardar con una fecha futura se vuelve a programar." else ""
        e.cerrado -> "Este envío ya se cerró. Al guardar con una fecha futura se vuelve a programar."
        !e.activo -> "Este envío ya venció. Al guardar con una fecha futura se vuelve a programar."
        else -> null
    }

    private fun mostrarContactos() {
        val mapa = Contactos.todos(this).associateBy { it.id }
        seleccion.retainAll { it in mapa }   // por si se borró alguno
        // Los que se suman arrancan con el modo de su contacto.
        seleccion.forEach { id -> canales.getOrPut(id) { mapa[id]!!.modo } }
        findViewById<TextView>(R.id.txtContactos).text =
            if (seleccion.isEmpty()) "Ninguno elegido"
            else seleccion.joinToString("\n") { id ->
                mapa[id]!!.let { "• ${it.etiqueta} · ${it.numero} · ${canal(id).corto}" }
            } + "\n(tocá la lista para cambiar por dónde se envía a cada uno, solo en este envío)"
        findViewById<Button>(R.id.btnContactos).text = if (seleccion.isEmpty()) "Elegir contactos" else "Cambiar contactos"
        actualizarSms()
    }

    private fun canal(id: Int) = canales[id] ?: Contactos.obtener(this, id)?.modo ?: ModoEnvio.WHATSAPP

    /** Por dónde se envía a cada destinatario, solo en este envío (no cambia el contacto ni otros envíos). */
    private fun elegirCanal() {
        if (seleccion.isEmpty()) return
        val nombres = seleccion.map { id -> "${Contactos.obtener(this, id)?.etiqueta ?: "?"} · ${canal(id).corto}" }
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("¿A quién?")
            .setItems(nombres.toTypedArray()) { _, i ->
                val id = seleccion[i]
                val c = Contactos.obtener(this, id)
                val opciones = ModoEnvio.entries.map { it.texto } + "Quitar de este envío"
                AlertDialog.Builder(this, temaDialogo())
                    .setTitle("Enviar a ${c?.etiqueta ?: "?"} por")
                    .setSingleChoiceItems(opciones.toTypedArray(), canal(id).ordinal) { d, j ->
                        d.dismiss()
                        guardarCamposTexto()
                        if (j < ModoEnvio.entries.size) canales[id] = ModoEnvio.entries[j]
                        else { seleccion.remove(id); canales.remove(id) }
                        mostrarContactos(); mostrarModo()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /**
     * Largo del SMS (como Recordatorios): línea corta "142 caracteres · 1 SMS" debajo del texto; al tocarla
     * o si se divide, el detalle. Mide el último campo tocado, con las variables ya reemplazadas.
     */
    private fun actualizarSms() {
        if (!::txtSms.isInitialized) return
        val conSms = seleccion.filter { canal(it).sms }
        mostrarSmsAuto(conSms.isNotEmpty())
        if (conSms.isEmpty()) { txtSms.visibility = View.GONE; return }
        val campo = ultimoCampo ?: edTexto
        val contactoId = if (campo === edTexto) conSms.first()
                         else camposTexto.entries.firstOrNull { it.value === campo }?.key
        if (contactoId != null && contactoId !in conSms) {
            txtSms.text = "SMS: este contacto no va por SMS"
            txtSms.visibility = View.VISIBLE
            return
        }
        val c = contactoId?.let { Contactos.obtener(this, it) }
        val texto = Sms.paraSms(Variables.aplicar(this, campo.text.toString(), c, destinoPrevio(contactoId),
            ZonedDateTime.of(fecha, hora, ZoneId.systemDefault())))
        val divide = Sms.partes(texto) > 1
        val para = if (campo !== edTexto && c != null) " para ${c.etiqueta}" else ""
        txtSms.text = "SMS$para: " + if (detalleSms || divide) Sms.descripcionTamanio(texto) else Sms.resumenCorto(texto)
        txtSms.setTextColor(getColor(if (divide) RN.color.acento else RN.color.texto2))
        txtSms.visibility = View.VISIBLE
    }

    /** Muestra el texto común o un campo por contacto según el modo elegido. */
    private fun mostrarModo() {
        armarCamposTexto()
        ultimoCampo = if (rbPorContacto.isChecked) camposTexto.values.firstOrNull() else edTexto
        actualizarVista()
        actualizarSms()
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
        campo.setOnFocusChangeListener { _, foco -> if (foco) { ultimoCampo = campo; actualizarVista(); actualizarSms() } }
        campo.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (campo === ultimoCampo) { actualizarVista(); actualizarSms() } }
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
        val propias = Variables.propias(this)
        val raras = Variables.desconocidas(texto, propias)
        // Variables propias sin valor: en "mismo texto" se revisan todos los contactos elegidos.
        val revisar = if (campo === edTexto) seleccion.toList() else listOfNotNull(contactoId)
        val sinValor = revisar.mapNotNull { id ->
            val f = Variables.faltantes(texto, Variables.datos(Contactos.obtener(this, id), destinoPrevio(id)), propias)
            if (f.isEmpty()) null else (Contactos.obtener(this, id)?.etiqueta ?: "?") + " (" + f.joinToString(", ") + ")"
        }
        txtVista.text = "Vista previa" + (c?.let { " para ${it.etiqueta}" } ?: "") + ": " +
            Variables.aplicar(this, texto, c, destinoPrevio(contactoId), ZonedDateTime.of(fecha, hora, ZoneId.systemDefault()), propias) +
            (if (raras.isEmpty()) "" else "\n⚠ No se reconoce ${raras.joinToString(", ")}: se envía tal cual.") +
            if (sinValor.isEmpty()) "" else "\n⚠ Sin valor (se envía vacío): ${sinValor.joinToString("; ")}"
        txtVista.visibility = View.VISIBLE
    }

    /** El destinatario ya guardado de ese contacto (con sus valores propios del CSV de envíos), si el envío existía. */
    private fun destinoPrevio(contactoId: Int?): Destino? =
        contactoId?.let { id -> original?.destinos?.firstOrNull { it.contactoId == id } }

    /** Lista de variables; la elegida se inserta donde está el cursor del último campo de texto tocado. */
    private fun elegirVariable() {
        val campo = ultimoCampo ?: run { avisar("Elegí primero los contactos"); return }
        insertarVariable(campo) { actualizarVista() }
    }

    /** Para quién es [campo]: "" en el texto común, " para Ana" en un texto por contacto. */
    private fun paraQuien(campo: EditText): String {
        if (campo === edTexto) return ""
        val id = camposTexto.entries.firstOrNull { it.value === campo }?.key
        return " para " + (id?.let { Contactos.obtener(this, it)?.etiqueta } ?: "el contacto")
    }

    /**
     * Elige una plantilla y la aplica al último campo de texto tocado: si está vacío, la pone directamente;
     * si no, pregunta reemplazar o agregar al final. En "un texto por contacto" también se puede
     * reemplazar en todos los contactos. El texto se copia (no queda enlazado a la plantilla).
     */
    private fun usarPlantilla() {
        val campo = ultimoCampo ?: run { avisar("Elegí primero los contactos"); return }
        elegirPlantilla { p ->
            val varios = campo !== edTexto && camposTexto.size > 1
            if (campo.text.isBlank() && !varios) { aplicarPlantilla(campo, p.texto, agregar = false); return@elegirPlantilla }
            val opciones = mutableListOf(
                "Reemplazar el texto${paraQuien(campo)}",
                "Agregar al final del texto${paraQuien(campo)}"
            )
            if (varios) opciones += "Reemplazar en todos los contactos"
            AlertDialog.Builder(this, temaDialogo())
                .setTitle("«${p.etiqueta}»")
                .setItems(opciones.toTypedArray()) { _, i ->
                    when (i) {
                        0 -> aplicarPlantilla(campo, p.texto, agregar = false)
                        1 -> aplicarPlantilla(campo, p.texto, agregar = true)
                        else -> {
                            camposTexto.values.forEach { it.setText(p.texto) }
                            guardarCamposTexto()
                            actualizarVista()
                            avisar("Plantilla aplicada a todos los contactos")
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun aplicarPlantilla(campo: EditText, texto: String, agregar: Boolean) {
        val actual = campo.text.toString()
        val nuevo = when {
            !agregar || actual.isBlank() -> texto
            actual.endsWith("\n") -> actual + texto
            else -> actual.trimEnd() + "\n" + texto
        }
        campo.setText(nuevo)
        campo.requestFocus()
        campo.setSelection(campo.text.length)
        ultimoCampo = campo
        actualizarVista()
    }

    /** Crea una plantilla con el texto del último campo tocado (se puede ajustar nombre y texto antes de guardar). */
    private fun guardarComoPlantilla() {
        val campo = ultimoCampo ?: run { avisar("Elegí primero los contactos"); return }
        val texto = campo.text.toString().trim()
        if (texto.isEmpty()) { avisar("Escribí primero el texto del mensaje"); return }
        Plantillas.mismoTexto(this, texto)?.let {
            avisar("Ese texto ya está guardado en la plantilla «${it.etiqueta}»")
            return
        }
        editarPlantilla(null, texto) {}
    }

    private fun guardarCamposTexto() {
        camposTexto.forEach { (id, campo) -> textos[id] = campo.text.toString() }
    }

    private fun mostrarFechaHora() {
        findViewById<Button>(R.id.btnFecha).text = fecha.format(fFecha)
        findViewById<Button>(R.id.btnHora).text = "%02d:%02d".format(hora.hour, hora.minute)
        actualizarVista()   // {fecha}, {hora}, {dia} y {saludo} dependen de la hora programada
        actualizarSms()
    }

    private fun elegirFecha() {
        val dp = DatePickerDialog(this, temaDialogo(), { _, a, m, d ->
            fecha = LocalDate.of(a, m + 1, d)
            mostrarFechaHora()
        }, fecha.year, fecha.monthValue - 1, fecha.dayOfMonth)
        dp.datePicker.minDate = System.currentTimeMillis() - 1000
        dp.show()
    }

    private fun mostrarAutoAbrir() {
        findViewById<Button>(R.id.btnAutoAbrir).text =
            "Abrir WhatsApp automáticamente: " + AjustesApp.textoAutoAbrir(this, autoAbrir)
    }

    private fun mostrarSmsAuto(visible: Boolean) {
        btnSmsAuto.visibility = if (visible) View.VISIBLE else View.GONE
        btnSmsAuto.text = "SMS: " + (if (smsAuto) "Automático (se mandan solos a la hora)" else "Preguntar antes (botón «Enviar SMS»)") +
            if (!AjustesApp.smsActivo(this)) " · apagados en Ajustes" else ""
    }

    private fun elegirSmsAuto() {
        val opciones = arrayOf("Preguntar antes: aviso con el botón «Enviar SMS»",
            "Automático: a la hora se mandan solos, sin alarma (aviso «SMS enviado a N de M»)")
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("SMS de este envío")
            .setSingleChoiceItems(opciones, if (smsAuto) 1 else 0) { d, i -> smsAuto = i == 1; mostrarSmsAuto(true); d.dismiss() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private var sinPermisoSms = false   // ya se pidió el permiso de SMS al guardar y no se dio: se guarda igual

    override fun onRequestPermissionsResult(pedido: Int, permisos: Array<out String>, resultados: IntArray) {
        super.onRequestPermissionsResult(pedido, permisos, resultados)
        if (pedido != PEDIDO_SMS) return
        if (!Sms.permitido(this)) {
            sinPermisoSms = true
            avisar("Sin el permiso de SMS no se pueden mandar solos: a la hora del envío avisará con «Enviar SMS»")
        }
        guardar()
    }

    private fun elegirAutoAbrir() {
        val valores = listOf(null, true, false)
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Abrir WhatsApp automáticamente")
            .setSingleChoiceItems(valores.map { AjustesApp.textoAutoAbrir(this, it) }.toTypedArray(),
                valores.indexOf(autoAbrir)) { d, i -> autoAbrir = valores[i]; mostrarAutoAbrir(); d.dismiss() }
            .setNegativeButton("Cancelar", null)
            .show()
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
        val tipo = repeticionElegida()
        val dias = checks.filter { it.isChecked }.map { it.tag as Int }.toSet()
        if (tipo == Repeticion.SEMANAL && dias.isEmpty()) { avisar("Elegí al menos un día de la semana"); return }
        var limite = 0
        if (tipo != Repeticion.NINGUNA && spTerminar.selectedItemPosition == 1) {
            limite = edVeces.text.toString().toIntOrNull() ?: 0
            if (limite < 1) { avisar("Indicá después de cuántas veces termina"); return }
        }
        val regla = Regla(tipo = tipo, dias = if (tipo == Repeticion.SEMANAL) dias else emptySet(),
            diaMes = fecha.dayOfMonth, semanaMes = spOrdinal.selectedItemPosition + 1,
            diaSemana = spDiaSemana.selectedItemPosition + 1, limite = limite)
        val ahora = System.currentTimeMillis()
        val elegido = ZonedDateTime.of(fecha, hora, ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (!regla.repite && elegido <= ahora) { avisar("Esa fecha y hora ya pasaron. Elegí un momento futuro."); return }
        // Con repetición: la primera vez válida (día elegido y en el futuro), como Recordatorios.
        val momento = Recurrencia.ajustarInicio(regla, elegido, ahora)
        val o = original
        // Si sigue repitiéndose, se conserva cuántas veces ya ocurrió; si se reactiva un vencido, vuelve a cero.
        val ocurrencias = if (o != null && o.repite && regla.repite) o.ocurrencias else 0
        if (regla.limite in 1..ocurrencias) {
            avisar("Ya ocurrió $ocurrencias veces: indicá un límite mayor o «Nunca»"); return
        }

        val id = original?.id ?: Envios.nuevoId(this)
        // Se conservan las marcas de "enviado" (para reprogramar solo los pendientes), salvo que ya se
        // hubiera enviado a todos: entonces es una vuelta nueva y se empieza de cero.
        val previos = original?.destinos?.associateBy { it.contactoId } ?: emptyMap()
        val deCero = previos.isNotEmpty() && previos.values.all { it.listo }
        val e = Envio(
            id = id,
            titulo = edTitulo.text.toString().trim(),
            modo = if (porContacto) ModoTexto.POR_CONTACTO else ModoTexto.MISMO,
            texto = textoComun,
            destinos = seleccion.map {
                Destino(it, if (porContacto) textos[it] ?: "" else "", enviado = !deCero && previos[it]?.enviado == true,
                    canal = canal(it), smsEnviado = !deCero && previos[it]?.smsEnviado == true,
                    datos = previos[it]?.datos ?: emptyMap())
            },
            momento = momento,
            alarma = swAlarma.isChecked,
            whatsapp = original?.whatsapp ?: "",
            regla = regla,
            ocurrencias = ocurrencias,
            origen = original?.origen ?: 0,
            autoAbrir = autoAbrir,
            smsAuto = smsAuto
        )
        // SMS automático: el permiso y el control "se dividiría en N partes" se hacen ahora,
        // porque a la hora del envío no hay nadie para responder.
        val conSms = e.destinos.filter { it.canal.sms && !it.smsEnviado }
        if (e.smsAuto && conSms.isNotEmpty() && AjustesApp.smsActivo(this)) {
            if (!Sms.permitido(this) && !sinPermisoSms) {
                requestPermissions(arrayOf(Manifest.permission.SEND_SMS), PEDIDO_SMS)
                return
            }
            controlarSms(e, conSms, Tiempo.zdt(momento)) { controlado, noEnviar ->
                if (noEnviar.isEmpty()) programar(controlado)
                else {
                    avisar("Los SMS quedan en «Preguntar antes»: a la hora avisará con el botón «Enviar SMS»")
                    programar(controlado.copy(smsAuto = false))
                }
            }
            return
        }
        programar(e)
    }

    private fun programar(e: Envio) {
        val id = e.id
        val momento = e.momento
        // Cancela alarmas, posposición y notificación anteriores; después programa la nueva fecha.
        Programador.quitar(this, id)
        Envios.guardar(this, e)
        Registro.anotar(this, "#$id envío ${if (original == null) "creado" else "editado"}")
        Programador.programar(this, ManejadorMensajes.comoAviso(this, e))
        avisar("Programado para el ${Tiempo.completo(momento)}" + if (e.regla.repite) " (se repite)" else "")
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
