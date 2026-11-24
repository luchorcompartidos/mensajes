package com.mismensajes.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mis.nucleo.Notificador
import com.mis.nucleo.Programador
import com.mis.nucleo.Recurrencia
import com.mis.nucleo.Regla
import com.mis.nucleo.Registro
import com.mis.nucleo.Sms
import com.mis.nucleo.Tiempo
import com.mis.nucleo.R as RN

/**
 * Abre WhatsApp con el número y el texto precargados (sin API ni accesibilidad: el usuario toca "Enviar"
 * en WhatsApp). Con WhatsApp común y Business instalados, se usa el elegido en el envío o en Ajustes.
 */
object WhatsApp {
    const val COMUN = "comun"
    const val BUSINESS = "business"
    const val PREGUNTAR = "preguntar"
    private val PAQUETES = mapOf(COMUN to "com.whatsapp", BUSINESS to "com.whatsapp.w4b")

    fun nombre(codigo: String) = when (codigo) {
        COMUN -> "WhatsApp"
        BUSINESS -> "WhatsApp Business"
        else -> "Preguntar cada vez"
    }

    /** Los instalados (COMUN y/o BUSINESS). */
    fun instalados(ctx: Context): List<String> = PAQUETES.filter { (_, paquete) ->
        runCatching { ctx.packageManager.getPackageInfo(paquete, 0) }.isSuccess
    }.keys.toList()

    fun instalado(ctx: Context): String? = instalados(ctx).firstOrNull()

    fun ambos(ctx: Context) = instalados(ctx).size == 2

    /** Cuál usar para este envío; null = hay que preguntar (solo si están los dos instalados). */
    fun cual(ctx: Context, e: Envio): String? {
        val inst = instalados(ctx)
        if (inst.size < 2) return inst.firstOrNull() ?: COMUN   // sin ninguno: se intenta por el navegador
        val elegido = e.whatsapp.ifBlank { AjustesApp.whatsapp(ctx) }
        return if (elegido == PREGUNTAR) null else elegido
    }

    /** Devuelve null si se abrió, o el motivo por el que no se pudo. */
    fun abrir(act: Activity, numero: String, texto: String, cual: String): String? {
        if (numero.length < 6) return "El contacto no tiene un número válido"
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$numero&text=" + Uri.encode(texto))
        val i = Intent(Intent.ACTION_VIEW, uri)
        PAQUETES[cual]?.takeIf { cual in instalados(act) }?.let { i.setPackage(it) }
        return try { act.startActivity(i); null } catch (e: ActivityNotFoundException) {
            "No se pudo abrir ${nombre(cual)} (¿está instalado?)"
        }
    }
}

/** Ajustes propios de esta app (preferencias "mensajes_ajustes"; los de avisos están en el núcleo). */
object AjustesApp {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("mensajes_ajustes", Context.MODE_PRIVATE)
    fun whatsapp(ctx: Context): String = prefs(ctx).getString("whatsapp", WhatsApp.COMUN) ?: WhatsApp.COMUN
    fun guardarWhatsapp(ctx: Context, codigo: String) = prefs(ctx).edit().putString("whatsapp", codigo).apply()

    /** "Abrir WhatsApp automáticamente" a la hora del envío: encendida por defecto. */
    fun autoAbrir(ctx: Context): Boolean = prefs(ctx).getBoolean("auto_abrir", true)
    fun guardarAutoAbrir(ctx: Context, valor: Boolean) = prefs(ctx).edit().putBoolean("auto_abrir", valor).apply()

    /** Lo que vale para este envío (su elección, o la de Ajustes). */
    fun autoAbrir(ctx: Context, e: Envio): Boolean = e.autoAbrir ?: autoAbrir(ctx)

    /** Texto de la opción para un envío: "Según Ajustes (encendida)", "Sí" o "No". */
    fun textoAutoAbrir(ctx: Context, valor: Boolean?): String = when (valor) {
        null -> "Según Ajustes (" + (if (autoAbrir(ctx)) "encendida" else "apagada") + ")"
        true -> "Sí, en este envío"
        false -> "No, en este envío"
    }
}

/** Cierra un envío (igual que "Hecho"): cancela alarmas y posposición, quita la notificación y sale del núcleo. */
fun cerrarEnvio(ctx: Context, id: Int, motivo: String) {
    Programador.quitar(ctx, id)
    Envios.obtener(ctx, id)?.let { Envios.guardar(ctx, it.copy(activo = false, cerrado = true, pospuestoHasta = 0)) }
    Registro.anotar(ctx, "#$id envío cerrado ($motivo)")
}

/**
 * Envío que se repite: separa la vez actual (la de [Envio.momento]) para enviarla, como los "cumplidos"
 * de Recordatorios. Se crea un envío aparte, vencido y sin repetición, con todos los contactos sin enviar
 * ([Envio.origen] = el original), y el original sigue programado desde la próxima vez.
 * Si era la última vez (límite de veces o sin próxima fecha), el propio original queda vencido.
 * Deja las alarmas del original al día. Devuelve el envío de esa vez (el nuevo o el original).
 */
fun separarVez(ctx: Context, e: Envio): Envio {
    val ahora = System.currentTimeMillis()
    val veces = e.ocurrencias + 1
    val siguiente = if (e.regla.limite in 1..veces) null
                    else Recurrencia.siguiente(e.regla, e.momento, maxOf(ahora, e.momento))
    val sinEnviar = e.destinos.map { it.sinEnviar() }
    if (siguiente == null) {
        val ultima = e.copy(activo = false, ocurrencias = veces, destinos = sinEnviar)
        Envios.guardar(ctx, ultima)
        Programador.cancelar(ctx, e.id, incluirPospuesto = false)
        Registro.anotar(ctx, "#${e.id} última vez (${Tiempo.completo(e.momento)}): ya no se repite")
        return ultima
    }
    val vez = e.copy(id = Envios.nuevoId(ctx), activo = false, cerrado = false, pospuestoHasta = 0,
        destinos = sinEnviar, regla = Regla.NINGUNA, ocurrencias = 0, origen = e.id)
    Envios.guardar(ctx, vez)
    val sigue = e.copy(momento = siguiente, ocurrencias = veces)
    Envios.guardar(ctx, sigue)
    Registro.anotar(ctx, "#${e.id} vez del ${Tiempo.completo(e.momento)} separada como #${vez.id}")
    Programador.programar(ctx, ManejadorMensajes.comoAviso(ctx, sigue))
    return vez
}

/**
 * Pantalla de envío: recorre los contactos de un envío y abre WhatsApp con el texto de cada uno.
 * Al volver de WhatsApp pregunta si se envió y lo marca. Con todos enviados, el envío se cierra solo.
 * Se abre desde "Enviar" en la notificación, en la pantalla de alarma o en la tarjeta de la lista.
 * Con la apertura automática ([EXTRA_AUTO], la abre el núcleo a la hora del envío) abre WhatsApp sola con el
 * primer pendiente y, cada vez que se confirma "Sí, enviado", sigue con el próximo.
 */
class EnviarActivity : Activity() {
    companion object {
        const val EXTRA_AUTO = "auto"
        /** Al abrirse, manda los SMS pendientes (pidiendo el permiso si falta). */
        const val EXTRA_SMS = "sms"
        private const val PEDIDO_SMS = 5
    }

    private var pedirSms = false    // falta mandar los SMS pedidos al abrir la pantalla ([EXTRA_SMS])
    private var smsLuegoDePermiso: Int? = null   // qué mandar al conceder el permiso: -1 = todos, o un contacto
    private val manejador = Handler(Looper.getMainLooper())
    private var refrescos = 0       // vueltas de refresco mientras Android confirma los SMS
    private var botonSms: Button? = null

    private var envioId = -1
    private var auto = false        // falta abrir WhatsApp solo con el primer pendiente (apertura automática)
    private var cadena = false      // apertura automática en curso: tras "Sí, enviado" sigue con el próximo
    private var esperando = -1      // contacto al que se le abrió WhatsApp (falta confirmar si se envió)
    private var terminado = false   // se envió a todos en esta pantalla
    private var preguntando = false // el diálogo "¿Se envió?" está abierto

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        setContentView(R.layout.activity_enviar)
        envioId = intent.getIntExtra(Programador.EXTRA_ID, -1)
        esperando = estado?.getInt("esperando", -1) ?: -1
        terminado = estado?.getBoolean("terminado", false) ?: false
        auto = estado?.getBoolean("auto", false) ?: intent.getBooleanExtra(EXTRA_AUTO, false)
        cadena = estado?.getBoolean("cadena", false) ?: auto
        pedirSms = estado == null && intent.getBooleanExtra(EXTRA_SMS, false)
        if (estado == null && auto) Registro.anotar(this, "#$envioId apertura automática: se abrió la pantalla de envío")
        // Si la alarma está sonando, se calla (la notificación queda en la bandeja).
        if (Notificador.sonando(this, envioId)) Notificador.silenciar(this, envioId)
        findViewById<Button>(R.id.enCerrar).setOnClickListener { cerrar() }
    }

    override fun onNewIntent(nuevo: Intent) {
        super.onNewIntent(nuevo)
        setIntent(nuevo)
        val id = nuevo.getIntExtra(Programador.EXTRA_ID, -1)
        if (id != envioId) { envioId = id; esperando = -1; terminado = false; cadena = false }
        if (nuevo.getBooleanExtra(EXTRA_SMS, false)) pedirSms = true
        if (nuevo.getBooleanExtra(EXTRA_AUTO, false) && esperando == -1 && !preguntando) {
            auto = true
            cadena = true
            Registro.anotar(this, "#$envioId apertura automática: se abrió la pantalla de envío")
        }
        if (Notificador.sonando(this, envioId)) Notificador.silenciar(this, envioId)
    }

    override fun onSaveInstanceState(salida: Bundle) {
        super.onSaveInstanceState(salida)
        salida.putInt("esperando", esperando)
        salida.putBoolean("terminado", terminado)
        salida.putBoolean("auto", auto)
        salida.putBoolean("cadena", cadena)
    }

    override fun onResume() {
        super.onResume()
        val e = Envios.obtener(this, envioId)
        if (e == null || (e.cerrado && !terminado)) {
            avisar(if (e == null) "El envío ya no existe" else "Este envío ya está cerrado")
            finish()
            return
        }
        mostrar(e)
        if (pedirSms) { pedirSms = false; enviarSms(-1) }
        if (esperando != -1 && !preguntando) confirmar(esperando)
        else if (auto && !preguntando) {
            auto = false
            e.pendientes.firstOrNull()?.let { enviarA(it.contactoId) } ?: run { cadena = false }
        }
    }

    private fun nombre(contactoId: Int) = Contactos.obtener(this, contactoId)?.etiqueta ?: "(contacto borrado)"

    private fun mostrar(e: Envio) {
        val dp = resources.displayMetrics.density
        findViewById<TextView>(R.id.enTitulo).text = Envios.titulo(this, e)
        findViewById<TextView>(R.id.enDetalle).text = when {
            e.cerrado -> "Enviado a todos · envío cerrado"
            e.pospuestoHasta > 0 -> "Pospuesto hasta el ${Tiempo.completo(e.pospuestoHasta)}"
            e.activo -> "Programado para el ${Tiempo.completo(e.momento)}. Enviar ahora no cancela la " +
                "programación: si queda alguien sin enviar, el aviso suena igual a esa hora."
            else -> "Venció el ${Tiempo.completo(e.momento)}"
        }
        findViewById<TextView>(R.id.enProgreso).text = Envios.progreso(e).ifBlank { "Enviado a 0 de ${e.destinos.size}" }

        val lista = findViewById<LinearLayout>(R.id.enLista)
        lista.removeAllViews()
        e.destinos.forEach { d -> lista.addView(fila(e, d), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = (8 * dp).toInt() }) }

        val siguiente = e.pendientes.firstOrNull()
        findViewById<Button>(R.id.enSiguiente).apply {
            visibility = if (siguiente == null) View.GONE else View.VISIBLE
            if (siguiente != null) {
                val n = e.destinos.indexOf(siguiente) + 1
                text = "Enviar a ${nombre(siguiente.contactoId)} ($n de ${e.destinos.size})"
                setOnClickListener { enviarA(siguiente.contactoId) }
            }
        }
        mostrarBotonSms(e)
        findViewById<Button>(R.id.enCerrar).text = if (e.cerrado) "Listo" else "Cerrar envío (Hecho)"
        if (WhatsApp.instalado(this) == null && !e.cerrado) {
            findViewById<TextView>(R.id.enAyuda).text = "No se encontró WhatsApp en el celular: se intentará abrir por el navegador."
        }
        // Con los dos WhatsApp instalados: con cuál se abre este envío (se cambia acá, solo para este envío).
        findViewById<TextView>(R.id.enWhatsapp).apply {
            visibility = if (WhatsApp.ambos(this@EnviarActivity) && !e.cerrado) View.VISIBLE else View.GONE
            val actual = if (e.whatsapp.isBlank()) "${WhatsApp.nombre(AjustesApp.whatsapp(this@EnviarActivity))} (según Ajustes)"
                         else WhatsApp.nombre(e.whatsapp)
            text = "Se abre con: $actual · tocá para cambiarlo en este envío"
            setOnClickListener { elegirWhatsappDelEnvio() }
        }
    }

    /** "Enviar SMS (N)": manda todos los SMS pendientes del envío (va debajo de "Enviar a…"). */
    private fun mostrarBotonSms(e: Envio) {
        val siguiente = findViewById<Button>(R.id.enSiguiente)
        val b = botonSms ?: Button(this, null, 0, RN.style.Boton).also { nuevo ->
            botonSms = nuevo
            val padre = siguiente.parent as LinearLayout
            padre.addView(nuevo, padre.indexOfChild(siguiente) + 1, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (8 * resources.displayMetrics.density).toInt() })
            nuevo.setOnClickListener { enviarSms(-1) }
        }
        val n = if (e.cerrado) 0 else e.pendientesSms.size
        b.visibility = if (n == 0) View.GONE else View.VISIBLE
        b.text = if (n > 1) "Enviar SMS ($n contactos)" else "Enviar SMS"
    }

    /** Manda los SMS pendientes ([contactoId] = -1) o el de un contacto; pide el permiso si falta. */
    private fun enviarSms(contactoId: Int) {
        if (!Sms.permitido(this)) {
            smsLuegoDePermiso = contactoId
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), PEDIDO_SMS)
            return
        }
        if (Notificador.sonando(this, envioId)) Notificador.silenciar(this, envioId)
        avisar(if (contactoId == -1) SmsApp.enviarTodos(this, envioId) else SmsApp.enviarUno(this, envioId, contactoId))
        Notificador.actualizar(this, envioId)
        // Android confirma cada SMS al rato: se vuelve a dibujar unas veces para mostrar las marcas.
        refrescos = 10
        manejador.removeCallbacksAndMessages(null)
        manejador.postDelayed(::refrescar, 1500)
    }

    private fun refrescar() {
        if (isFinishing) return
        Envios.obtener(this, envioId)?.let { if (it.cerrado) terminado = true; mostrar(it) }
        if (--refrescos > 0) manejador.postDelayed(::refrescar, 1500)
    }

    override fun onDestroy() {
        manejador.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(pedido: Int, permisos: Array<out String>, resultados: IntArray) {
        super.onRequestPermissionsResult(pedido, permisos, resultados)
        if (pedido != PEDIDO_SMS) return
        val cual = smsLuegoDePermiso ?: return
        smsLuegoDePermiso = null
        if (Sms.permitido(this)) enviarSms(cual)
        else AlertDialog.Builder(this, temaDialogo())
            .setTitle("Falta el permiso de SMS")
            .setMessage("Sin el permiso de SMS no se pueden mandar. Si Android no deja darlo (la app se instaló a mano): " +
                "Ajustes del celular > Apps > Programador de Mensajes > ⋮ > «Permitir ajustes restringidos», " +
                "y después volvé a tocar «Enviar SMS».")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun elegirWhatsappDelEnvio() {
        val codigos = listOf("", WhatsApp.COMUN, WhatsApp.BUSINESS, WhatsApp.PREGUNTAR)
        val textos = codigos.map {
            if (it.isBlank()) "Según Ajustes (${WhatsApp.nombre(AjustesApp.whatsapp(this))})" else WhatsApp.nombre(it)
        }.toTypedArray()
        val e = Envios.obtener(this, envioId) ?: return
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Abrir este envío con")
            .setSingleChoiceItems(textos, codigos.indexOf(e.whatsapp).coerceAtLeast(0)) { d, i ->
                Envios.obtener(this, envioId)?.let { Envios.guardar(this, it.copy(whatsapp = codigos[i])) }
                d.dismiss()
                Envios.obtener(this, envioId)?.let { mostrar(it) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Fila de un contacto: toque = enviarle a él; mantener presionado = marcar/desmarcar como enviado. */
    private fun fila(e: Envio, d: Destino): View {
        val dp = resources.displayMetrics.density
        val c = Contactos.obtener(this, d.contactoId)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(RN.drawable.campo)
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            if (d.listo) alpha = 0.65f
            addView(TextView(this@EnviarActivity).apply {
                text = (if (d.listo) "✓ " else "○ ") + (c?.etiqueta ?: "(contacto borrado)")
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(getColor(if (d.listo) RN.color.acento else RN.color.texto))
            })
            addView(TextView(this@EnviarActivity).apply {
                val estados = mutableListOf<String>()
                if (d.canal.whatsapp) estados += if (d.enviado) "WhatsApp enviado" else "WhatsApp pendiente"
                if (d.canal.sms) estados += if (d.smsEnviado) "SMS enviado" else "SMS pendiente"
                text = (c?.numero ?: "") + " · " + estados.joinToString(" · ")
                textSize = 14f
                setTextColor(getColor(RN.color.texto2))
            })
            if (e.modo == ModoTexto.POR_CONTACTO || Variables.usa(e.texto)) addView(TextView(this@EnviarActivity).apply {
                text = Variables.aplicar(e.textoPara(d), c)
                textSize = 14f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(getColor(RN.color.texto3))
                setPadding(0, (4 * dp).toInt(), 0, 0)
            })
            setOnClickListener {
                if (!d.canal.whatsapp) {
                    // Solo SMS: mandarlo (o volver a mandarlo) a este contacto.
                    AlertDialog.Builder(this@EnviarActivity, temaDialogo())
                        .setTitle(if (d.smsEnviado) "SMS ya enviado" else "Enviar SMS")
                        .setMessage((if (d.smsEnviado) "¿Mandarle el SMS otra vez a " else "¿Mandarle el SMS a ") +
                            "${nombre(d.contactoId)}? (puede tener costo según tu plan)")
                        .setPositiveButton("Enviar SMS") { _, _ -> enviarSms(d.contactoId) }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
                else if (!d.enviado) enviarA(d.contactoId)
                else AlertDialog.Builder(this@EnviarActivity, temaDialogo())
                    .setTitle("Ya enviado")
                    .setMessage("¿Enviarle otra vez a ${nombre(d.contactoId)}?")
                    .setPositiveButton("Enviar") { _, _ -> enviarA(d.contactoId) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            setOnLongClickListener {
                // Marca a mano: WhatsApp (o el SMS, si va solo por SMS).
                if (d.canal.whatsapp) Envios.marcarEnviado(this@EnviarActivity, envioId, d.contactoId, !d.enviado)?.let { revisar(it) }
                else Envios.marcarSms(this@EnviarActivity, envioId, d.contactoId, !d.smsEnviado)?.let { revisar(it) }
                true
            }
        }
    }

    private fun enviarA(contactoId: Int, elegido: String? = null) {
        val e = Envios.obtener(this, envioId) ?: return
        val d = e.destinos.firstOrNull { it.contactoId == contactoId } ?: return
        val c = Contactos.obtener(this, contactoId) ?: run { avisar("Ese contacto ya no existe"); return }
        val cual = elegido ?: WhatsApp.cual(this, e) ?: run {
            // "Preguntar cada vez": se elige para este contacto.
            val opciones = listOf(WhatsApp.COMUN, WhatsApp.BUSINESS)
            AlertDialog.Builder(this, temaDialogo())
                .setTitle("Enviar a ${c.etiqueta} con")
                .setItems(opciones.map { WhatsApp.nombre(it) }.toTypedArray()) { _, i -> enviarA(contactoId, opciones[i]) }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        val error = WhatsApp.abrir(this, c.numeroWhatsapp(), Variables.aplicar(e.textoPara(d), c), cual)
        if (error != null) { avisar(error); return }
        esperando = contactoId
        Registro.anotar(this, "#$envioId ${WhatsApp.nombre(cual)} abierto para «${c.etiqueta}»")
    }

    /** Al volver de WhatsApp: ¿se envió? */
    private fun confirmar(contactoId: Int) {
        var respondido = false
        preguntando = true
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("¿Se envió?")
            .setMessage("¿Enviaste el mensaje a ${nombre(contactoId)}?")
            .setPositiveButton("Sí, enviado") { _, _ ->
                respondido = true
                esperando = -1
                Registro.anotar(this, "#$envioId enviado a «${nombre(contactoId)}»")
                Envios.marcarEnviado(this, envioId, contactoId)?.let { revisar(it) }
                // Apertura automática: sigue con el próximo pendiente.
                if (cadena) Envios.obtener(this, envioId)?.takeIf { !it.cerrado }?.pendientes?.firstOrNull()
                    ?.let { enviarA(it.contactoId) } ?: run { cadena = false }
            }
            .setNegativeButton("No") { _, _ -> respondido = true; esperando = -1; cadena = false }
            .setOnDismissListener { preguntando = false; if (!respondido) { esperando = -1; cadena = false } }
            .show()
    }

    /** Vuelve a dibujar y, si ya se envió a todos, cierra el envío; si no, actualiza el progreso de la notificación. */
    private fun revisar(e: Envio) {
        if (e.faltan.isEmpty() && !e.cerrado) {
            cerrarEnvio(this, envioId, "enviado a todos")
            terminado = true
            avisar("Enviado a todos. Envío cerrado.")
        } else if (!e.cerrado) {
            Notificador.actualizar(this, envioId)
        }
        Envios.obtener(this, envioId)?.let { mostrar(it) }
    }

    private fun cerrar() {
        val e = Envios.obtener(this, envioId)
        if (e == null || e.cerrado) { finish(); return }
        val faltan = e.faltan.size
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Cerrar envío")
            .setMessage((if (faltan == 0) "¿Cerrar el envío?"
                        else "Quedan $faltan sin enviar. ¿Cerrar el envío igual? (es lo mismo que «Hecho»)") +
                        if (e.activo || e.pospuestoHasta > 0) " También se cancela la programación." else "")
            .setPositiveButton("Cerrar envío") { _, _ ->
                cerrarEnvio(this, envioId, if (faltan == 0) "Hecho" else "Hecho, quedaron $faltan sin enviar")
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
