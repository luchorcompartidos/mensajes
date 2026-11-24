package com.mismensajes.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.KeyguardManager
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.mis.nucleo.Ajustes
import com.mis.nucleo.Notificador
import com.mis.nucleo.Posposicion
import com.mis.nucleo.Programador
import com.mis.nucleo.Registro
import com.mis.nucleo.Tiempo
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import com.mis.nucleo.R as RN

// Pantallas "Posponer…" y alarma a pantalla completa, traídas de Pantallas.kt de Recordatorios.

/** Diálogos con la paleta de la app (claro u oscuro según el tema activo). */
fun Activity.temaDialogo(): Int = RN.style.DialogoApp

fun Activity.avisar(texto: String) = Toast.makeText(this, texto, Toast.LENGTH_LONG).show()

fun Activity.conDesbloqueo(accion: () -> Unit) {
    val km = getSystemService(KeyguardManager::class.java)
    if (!km.isKeyguardLocked) { accion(); return }
    km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
        override fun onDismissSucceeded() { accion() }
        override fun onDismissCancelled() { avisar("Desbloqueá el celular para continuar") }
        override fun onDismissError() { avisar("Desbloqueá el celular para continuar") }
    })
}

/** Aplica la posposición y avisa hasta cuándo quedó. */
fun Activity.aplicarPosponer(id: Int, codigo: String): Boolean {
    val momento = Programador.posponer(this, id, codigo)
    avisar(if (momento != null) "Pospuesto hasta el " + Tiempo.completo(momento)
           else "No se pudo posponer: " + Programador.motivo)
    return momento != null
}

/** Set completo de opciones + "Personalizado…". [alCerrar] recibe true si se pospuso. */
fun Activity.elegirPosponer(id: Int, alCerrar: (Boolean) -> Unit) {
    val opciones = Posposicion.OPCIONES
    val textos = (opciones.map { it.texto } + "Personalizado…").toTypedArray()
    var pospuesto = false
    var sigue = false
    AlertDialog.Builder(this, temaDialogo())
        .setTitle("Posponer")
        .setItems(textos) { _, i ->
            if (i == opciones.size) {
                sigue = true
                elegirPersonalizado(id, alCerrar)
            } else {
                pospuesto = aplicarPosponer(id, opciones[i].codigo)
            }
        }
        .setNegativeButton("Cancelar", null)
        .setOnDismissListener { if (!sigue) alCerrar(pospuesto) }
        .show()
}

fun Activity.elegirPersonalizado(id: Int, alCerrar: (Boolean) -> Unit) {
    var sigue = false
    AlertDialog.Builder(this, temaDialogo())
        .setTitle("Personalizado")
        .setItems(arrayOf("Fecha y hora puntuales", "Plazo (número y unidad)")) { _, i ->
            sigue = true
            if (i == 0) elegirFechaHora(id, alCerrar) else elegirPlazo(id, alCerrar)
        }
        .setNegativeButton("Cancelar", null)
        .setOnDismissListener { if (!sigue) alCerrar(false) }
        .show()
}

private fun Activity.elegirFechaHora(id: Int, alCerrar: (Boolean) -> Unit) {
    val ahora = ZonedDateTime.now()
    var fechaElegida = false
    val dp = DatePickerDialog(this, temaDialogo(), { _, a, m, d ->
        fechaElegida = true
        // La hora arranca en la última usada en "Personalizado"; si es hoy y esa hora ya pasó, en la actual.
        val fecha = LocalDate.of(a, m + 1, d)
        val horaActual = LocalTime.now().withSecond(0).withNano(0)
        val ultima = Ajustes.ultimaHora(this)
        val inicial = if (ultima == null || (fecha == LocalDate.now() && !ultima.isAfter(horaActual))) horaActual else ultima
        fun pedirHora(desde: LocalTime) {
            elegirHora(desde, alCancelar = { alCerrar(false) }) { t ->
                val ms = ZonedDateTime.of(fecha, t, ahora.zone).toInstant().toEpochMilli()
                if (ms <= System.currentTimeMillis()) {
                    avisar("Esa hora ya pasó. Elegí una hora futura.")
                    pedirHora(t)   // vuelve a mostrar el selector en lugar de fallar
                } else {
                    Ajustes.guardarUltimaHora(this, t)
                    alCerrar(aplicarPosponer(id, "t:$ms"))
                }
            }
        }
        pedirHora(inicial)
    }, ahora.year, ahora.monthValue - 1, ahora.dayOfMonth)
    dp.datePicker.minDate = System.currentTimeMillis() - 1000
    dp.setOnDismissListener { if (!fechaElegida) alCerrar(false) }
    dp.show()
}

private fun Activity.elegirPlazo(id: Int, alCerrar: (Boolean) -> Unit) {
    val c = ContextThemeWrapper(this, temaDialogo())
    val margen = (20 * resources.displayMetrics.density).toInt()
    val unidades = listOf("Minutos" to "m", "Horas" to "h", "Días" to "d",
        "Semanas" to "w", "Meses" to "M", "Años" to "y")
    val (ultimoN, ultimaU) = Ajustes.ultimoPlazo(this)
    val numero = EditText(c).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(ultimoN.toString())
        setSelectAllOnFocus(true)
    }
    val unidad = Spinner(c).apply {
        adapter = ArrayAdapter(c, android.R.layout.simple_spinner_dropdown_item, unidades.map { it.first })
        setSelection(unidades.indexOfFirst { it.second == ultimaU }.coerceAtLeast(0))
    }
    val caja = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(margen, margen / 2, margen, 0)
        addView(numero, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(unidad, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
    }
    var listo = false
    AlertDialog.Builder(this, temaDialogo())
        .setTitle("Posponer durante")
        .setView(caja)
        .setPositiveButton("Posponer") { _, _ ->
            listo = true
            val n = numero.text.toString().toIntOrNull()
            if (n == null || n <= 0 || n > 10_000) {
                avisar("Ingresá un número entre 1 y 10000")
                alCerrar(false)
            } else {
                val u = unidades[unidad.selectedItemPosition].second
                Ajustes.guardarUltimoPlazo(this, n, u)
                alCerrar(aplicarPosponer(id, "$u:$n"))
            }
        }
        .setNegativeButton("Cancelar", null)
        .setOnDismissListener { if (!listo) alCerrar(false) }
        .show()
}

/** Repite la acción mientras se mantiene presionado el botón. */
private fun repetirAlMantener(v: View, accion: () -> Unit) {
    val h = Handler(Looper.getMainLooper())
    val repetir = object : Runnable {
        override fun run() { accion(); h.postDelayed(this, 110) }
    }
    v.setOnTouchListener { vista, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { accion(); h.postDelayed(repetir, 450) }
            MotionEvent.ACTION_UP -> { h.removeCallbacks(repetir); vista.performClick() }
            MotionEvent.ACTION_CANCEL -> h.removeCallbacks(repetir)
        }
        true
    }
}

/** Selector de hora con flechas ▲▼, teclado y acceso al reloj del sistema (sincronizados). */
fun Activity.elegirHora(inicial: LocalTime, alCancelar: () -> Unit = {}, alElegir: (LocalTime) -> Unit) {
    val c = ContextThemeWrapper(this, temaDialogo())
    val dp = resources.displayMetrics.density
    fun dos(v: Int) = "%02d".format(v)
    fun campo(valor: Int) = EditText(c).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(2))
        gravity = Gravity.CENTER
        textSize = 34f
        imeOptions = EditorInfo.IME_ACTION_NEXT
        setSelectAllOnFocus(true)
        setText(dos(valor))
    }
    val hh = campo(inicial.hour)
    val mm = campo(inicial.minute)
    fun leer(e: EditText, max: Int) = (e.text.toString().toIntOrNull() ?: 0).coerceIn(0, max)
    fun mover(e: EditText, max: Int, delta: Int) { e.setText(dos((leer(e, max) + delta + max + 1) % (max + 1))) }
    fun columna(e: EditText, max: Int) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val arriba = Button(c).apply { text = "▲" }
        val abajo = Button(c).apply { text = "▼" }
        repetirAlMantener(arriba) { mover(e, max, +1) }
        repetirAlMantener(abajo) { mover(e, max, -1) }
        addView(arriba)
        addView(e, LinearLayout.LayoutParams((96 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(abajo)
    }
    val fila = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, (12 * dp).toInt(), 0, 0)
        addView(columna(hh, 23))
        addView(TextView(c).apply { text = ":"; textSize = 34f; setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0) })
        addView(columna(mm, 59))
    }
    var aceptado = false
    val dialogo = AlertDialog.Builder(this, temaDialogo())
        .setTitle("Hora")
        .setView(fila)
        .setPositiveButton("Aceptar") { _, _ -> aceptado = true; alElegir(LocalTime.of(leer(hh, 23), leer(mm, 59))) }
        .setNeutralButton("Ver reloj", null)
        .setNegativeButton("Cancelar", null)
        .create()
    dialogo.setOnShowListener {
        dialogo.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            TimePickerDialog(this, temaDialogo(), { _, h, m -> hh.setText(dos(h)); mm.setText(dos(m)) },
                leer(hh, 23), leer(mm, 59), true).show()
        }
    }
    dialogo.setOnDismissListener { if (!aceptado) alCancelar() }
    dialogo.show()
}

/** Ventanita que se abre desde "Posponer…" (o "Posponer personalizado…") en la notificación. */
class PosponerActivity : Activity() {
    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        val id = intent.getIntExtra(Programador.EXTRA_ID, -1)
        if (intent.getBooleanExtra(Programador.EXTRA_PERSONALIZADO, false)) elegirPersonalizado(id) { finish() }
        else elegirPosponer(id) { finish() }
    }
}

/** Pantalla de alarma: aparece sobre la pantalla bloqueada. */
class AlarmaActivity : Activity() {
    private var envioId = -1

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.activity_alarma)
        cargar(intent)
    }

    override fun onNewIntent(nuevo: Intent) {
        super.onNewIntent(nuevo)
        setIntent(nuevo)
        cargar(nuevo)
    }

    private fun cargar(i: Intent) {
        envioId = i.getIntExtra(Programador.EXTRA_ID, -1)
        val a = ManejadorMensajes.aviso(this, envioId) ?: run { finish(); return }
        findViewById<TextView>(R.id.alHora).text = Tiempo.hora(System.currentTimeMillis())
        findViewById<TextView>(R.id.alTitulo).text = a.titulo
        val pospuesta = Notificador.esPospuesta(this, envioId)
        val nota = if (pospuesta) "{\"Original Vencido\"}" + (if (a.texto.isBlank()) "" else "\n" + a.texto) else a.texto
        findViewById<TextView>(R.id.alNota).apply {
            text = nota
            visibility = if (nota.isBlank()) View.GONE else View.VISIBLE
        }
        // "Enviar" mientras quede alguien sin enviar: calla la alarma y abre la pantalla de envío
        // (que también tiene "Enviar SMS"). Si solo quedan SMS: "Enviar SMS" los manda directo.
        val envio = Envios.obtener(this, envioId)
        val pendientes = envio?.pendientes?.size ?: 0
        val sms = envio?.pendientesSms?.size ?: 0
        findViewById<Button>(R.id.alEnviar).apply {
            visibility = if (pendientes > 0 || sms > 0) View.VISIBLE else View.GONE
            text = when {
                pendientes == 0 -> if (sms > 1) "Enviar SMS ($sms contactos)" else "Enviar SMS"
                pendientes > 1 -> "Enviar ($pendientes contactos)"
                else -> "Enviar"
            }
            setOnClickListener {
                Notificador.silenciar(this@AlarmaActivity, envioId)
                if (pendientes == 0 && com.mis.nucleo.Sms.permitido(this@AlarmaActivity)) {
                    avisar(SmsApp.enviarTodos(this@AlarmaActivity, envioId))
                    Notificador.actualizar(this@AlarmaActivity, envioId)
                    finish()
                    return@setOnClickListener
                }
                conDesbloqueo {
                    startActivity(Intent(this@AlarmaActivity, EnviarActivity::class.java)
                        .putExtra(Programador.EXTRA_ID, envioId).putExtra(EnviarActivity.EXTRA_SMS, pendientes == 0)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                }
            }
        }
        val callar = findViewById<Button>(R.id.alCallar)
        callar.isEnabled = true
        callar.text = "Callar"
        callar.setOnClickListener {
            Notificador.silenciar(this, envioId)
            callar.isEnabled = false
            callar.text = "Silenciada"
        }
        findViewById<Button>(R.id.alHecho).setOnClickListener {
            Notificador.quitar(this, envioId)
            Registro.anotar(this, "#$envioId marcado «Hecho» (pantalla de alarma)")
            ManejadorMensajes.alHecho(this, envioId)
            finish()
        }
        val rapido = Ajustes.posponerRapido(this)
        findViewById<Button>(R.id.alPosponerRapido).apply {
            text = Posposicion.textoBoton(rapido)
            setOnClickListener {
                if (rapido == Posposicion.PERSONALIZADO) elegirPersonalizado(envioId) { pospuesto -> if (pospuesto) finish() }
                else if (aplicarPosponer(envioId, rapido)) finish()
            }
        }
        findViewById<Button>(R.id.alPosponerOtro).setOnClickListener {
            elegirPosponer(envioId) { pospuesto -> if (pospuesto) finish() }
        }
        findViewById<Button>(R.id.alEditar).setOnClickListener {
            Notificador.silenciar(this, envioId)
            conDesbloqueo {
                startActivity(Intent(this, EditarActivity::class.java).putExtra(Programador.EXTRA_ID, envioId))
                finish()
            }
        }
        findViewById<Button>(R.id.alCerrar).setOnClickListener {
            Notificador.silenciar(this, envioId)   // la notificación queda en la bandeja, sin sonido
            finish()
        }
    }
}
