package com.mismensajes.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mis.nucleo.Notificador
import com.mis.nucleo.Programador
import com.mis.nucleo.Registro
import com.mis.nucleo.Tiempo
import com.mis.nucleo.R as RN

/**
 * Abre WhatsApp con el número y el texto precargados (sin API ni accesibilidad: el usuario toca "Enviar"
 * en WhatsApp). Usa WhatsApp común y, si no está, WhatsApp Business.
 */
object WhatsApp {
    private val PAQUETES = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun instalado(ctx: Context): String? = PAQUETES.firstOrNull {
        runCatching { ctx.packageManager.getPackageInfo(it, 0) }.isSuccess
    }

    /** Devuelve null si se abrió, o el motivo por el que no se pudo. */
    fun abrir(act: Activity, numero: String, texto: String): String? {
        if (numero.length < 6) return "El contacto no tiene un número válido"
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$numero&text=" + Uri.encode(texto))
        val i = Intent(Intent.ACTION_VIEW, uri)
        instalado(act)?.let { i.setPackage(it) }
        return try { act.startActivity(i); null } catch (e: ActivityNotFoundException) {
            "No se pudo abrir WhatsApp (¿está instalado?)"
        }
    }
}

/** Cierra un envío (igual que "Hecho"): cancela alarmas y posposición, quita la notificación y sale del núcleo. */
fun cerrarEnvio(ctx: Context, id: Int, motivo: String) {
    Programador.quitar(ctx, id)
    Envios.obtener(ctx, id)?.let { Envios.guardar(ctx, it.copy(activo = false, cerrado = true, pospuestoHasta = 0)) }
    Registro.anotar(ctx, "#$id envío cerrado ($motivo)")
}

/**
 * Pantalla de envío: recorre los contactos de un envío y abre WhatsApp con el texto de cada uno.
 * Al volver de WhatsApp pregunta si se envió y lo marca. Con todos enviados, el envío se cierra solo.
 * Se abre desde "Enviar" en la notificación, en la pantalla de alarma o en la tarjeta de la lista.
 */
class EnviarActivity : Activity() {
    private var envioId = -1
    private var esperando = -1      // contacto al que se le abrió WhatsApp (falta confirmar si se envió)
    private var terminado = false   // se envió a todos en esta pantalla

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        setContentView(R.layout.activity_enviar)
        envioId = intent.getIntExtra(Programador.EXTRA_ID, -1)
        esperando = estado?.getInt("esperando", -1) ?: -1
        terminado = estado?.getBoolean("terminado", false) ?: false
        // Si la alarma está sonando, se calla (la notificación queda en la bandeja).
        if (Notificador.sonando(this, envioId)) Notificador.silenciar(this, envioId)
        findViewById<Button>(R.id.enCerrar).setOnClickListener { cerrar() }
    }

    override fun onNewIntent(nuevo: Intent) {
        super.onNewIntent(nuevo)
        setIntent(nuevo)
        val id = nuevo.getIntExtra(Programador.EXTRA_ID, -1)
        if (id != envioId) { envioId = id; esperando = -1; terminado = false }
        if (Notificador.sonando(this, envioId)) Notificador.silenciar(this, envioId)
    }

    override fun onSaveInstanceState(salida: Bundle) {
        super.onSaveInstanceState(salida)
        salida.putInt("esperando", esperando)
        salida.putBoolean("terminado", terminado)
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
        if (esperando != -1) confirmar(esperando)
    }

    private fun nombre(contactoId: Int) = Contactos.obtener(this, contactoId)?.etiqueta ?: "(contacto borrado)"

    private fun mostrar(e: Envio) {
        val dp = resources.displayMetrics.density
        findViewById<TextView>(R.id.enTitulo).text = Envios.titulo(this, e)
        findViewById<TextView>(R.id.enDetalle).text = when {
            e.cerrado -> "Enviado a todos · envío cerrado"
            e.pospuestoHasta > 0 -> "Pospuesto hasta el ${Tiempo.completo(e.pospuestoHasta)}"
            e.activo -> "Programado para el ${Tiempo.completo(e.momento)}"
            else -> "Venció el ${Tiempo.completo(e.momento)}"
        }
        val enviados = e.destinos.count { it.enviado }
        findViewById<TextView>(R.id.enProgreso).text = "Enviado a $enviados de ${e.destinos.size}"

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
        findViewById<Button>(R.id.enCerrar).text = if (e.cerrado) "Listo" else "Cerrar envío (Hecho)"
        if (WhatsApp.instalado(this) == null && !e.cerrado) {
            findViewById<TextView>(R.id.enAyuda).text = "No se encontró WhatsApp en el celular: se intentará abrir por el navegador."
        }
    }

    /** Fila de un contacto: toque = enviarle a él; mantener presionado = marcar/desmarcar como enviado. */
    private fun fila(e: Envio, d: Destino): View {
        val dp = resources.displayMetrics.density
        val c = Contactos.obtener(this, d.contactoId)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(RN.drawable.campo)
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            if (d.enviado) alpha = 0.65f
            addView(TextView(this@EnviarActivity).apply {
                text = (if (d.enviado) "✓ " else "○ ") + (c?.etiqueta ?: "(contacto borrado)")
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(getColor(if (d.enviado) RN.color.acento else RN.color.texto))
            })
            addView(TextView(this@EnviarActivity).apply {
                text = (c?.numero ?: "") + if (d.enviado) " · enviado" else " · pendiente"
                textSize = 14f
                setTextColor(getColor(RN.color.texto2))
            })
            if (e.modo == ModoTexto.POR_CONTACTO) addView(TextView(this@EnviarActivity).apply {
                text = d.texto
                textSize = 14f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(getColor(RN.color.texto3))
                setPadding(0, (4 * dp).toInt(), 0, 0)
            })
            setOnClickListener {
                if (!d.enviado) enviarA(d.contactoId)
                else AlertDialog.Builder(this@EnviarActivity, temaDialogo())
                    .setTitle("Ya enviado")
                    .setMessage("¿Enviarle otra vez a ${nombre(d.contactoId)}?")
                    .setPositiveButton("Enviar") { _, _ -> enviarA(d.contactoId) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            setOnLongClickListener {
                Envios.marcarEnviado(this@EnviarActivity, envioId, d.contactoId, !d.enviado)?.let { revisar(it) }
                true
            }
        }
    }

    private fun enviarA(contactoId: Int) {
        val e = Envios.obtener(this, envioId) ?: return
        val d = e.destinos.firstOrNull { it.contactoId == contactoId } ?: return
        val c = Contactos.obtener(this, contactoId) ?: run { avisar("Ese contacto ya no existe"); return }
        val error = WhatsApp.abrir(this, c.numeroWhatsapp(), e.textoPara(d))
        if (error != null) { avisar(error); return }
        esperando = contactoId
        Registro.anotar(this, "#$envioId WhatsApp abierto para «${c.etiqueta}»")
    }

    /** Al volver de WhatsApp: ¿se envió? */
    private fun confirmar(contactoId: Int) {
        var respondido = false
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("¿Se envió?")
            .setMessage("¿Enviaste el mensaje a ${nombre(contactoId)}?")
            .setPositiveButton("Sí, enviado") { _, _ ->
                respondido = true
                esperando = -1
                Registro.anotar(this, "#$envioId enviado a «${nombre(contactoId)}»")
                Envios.marcarEnviado(this, envioId, contactoId)?.let { revisar(it) }
            }
            .setNegativeButton("No") { _, _ -> respondido = true; esperando = -1 }
            .setOnDismissListener { if (!respondido) esperando = -1 }
            .show()
    }

    /** Vuelve a dibujar y, si ya se envió a todos, cierra el envío. */
    private fun revisar(e: Envio) {
        if (e.pendientes.isEmpty() && !e.cerrado) {
            cerrarEnvio(this, envioId, "enviado a todos")
            terminado = true
            avisar("Enviado a todos. Envío cerrado.")
        }
        Envios.obtener(this, envioId)?.let { mostrar(it) }
    }

    private fun cerrar() {
        val e = Envios.obtener(this, envioId)
        if (e == null || e.cerrado) { finish(); return }
        val faltan = e.pendientes.size
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Cerrar envío")
            .setMessage(if (faltan == 0) "¿Cerrar el envío?"
                        else "Quedan $faltan sin enviar. ¿Cerrar el envío igual? (es lo mismo que «Hecho»)")
            .setPositiveButton("Cerrar envío") { _, _ ->
                cerrarEnvio(this, envioId, if (faltan == 0) "Hecho" else "Hecho, quedaron $faltan sin enviar")
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
