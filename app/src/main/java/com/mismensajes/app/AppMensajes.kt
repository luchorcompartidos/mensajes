package com.mismensajes.app

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mis.nucleo.Aviso
import com.mis.nucleo.Manejador
import com.mis.nucleo.Notificador
import com.mis.nucleo.Nucleo
import com.mis.nucleo.Programador
import com.mis.nucleo.Tiempo
import com.mis.nucleo.VersionGitHub

/** Se crea antes que cualquier pantalla o receiver: deja configurado el núcleo. */
class AppMensajes : Application() {
    override fun onCreate() {
        super.onCreate()
        Nucleo.configurar(ManejadorMensajes)
        VersionGitHub.repo = "luchorcompartidos/mensajes"
        VersionGitHub.apkFijo = "programador-mensajes.apk"
        Notificador.crearCanales(this)
        quitarPruebaVieja()
    }

    /** La v0.0.2 tenía un aviso de prueba (#1): se descarta para que no se cruce con los envíos. */
    private fun quitarPruebaVieja() {
        val p = getSharedPreferences("prueba", MODE_PRIVATE)
        if (!p.contains("aviso")) return
        Programador.cancelar(this, 1)
        Notificador.quitar(this, 1)
        p.edit().clear().commit()
    }
}

/** Lo que la app le resuelve al núcleo: cada envío programado es un aviso (mismo id). */
object ManejadorMensajes : Manejador {
    fun comoAviso(ctx: Context, e: Envio) = Aviso(
        id = e.id, titulo = Envios.titulo(ctx, e),
        texto = listOf(resumen(ctx, e), Envios.progreso(e)).filter { it.isNotBlank() }.joinToString("\n"),
        momento = e.momento,
        activo = e.activo, alarma = e.alarma, pospuestoHasta = e.pospuestoHasta
    )

    /** Con un solo contacto y mismo texto, la notificación muestra el mensaje con las variables ya reemplazadas. */
    private fun resumen(ctx: Context, e: Envio): String {
        val t = Envios.resumenTexto(e)
        if (e.modo != ModoTexto.MISMO || e.destinos.size != 1 || !Variables.usa(t)) return t
        return Variables.aplicar(t, Contactos.obtener(ctx, e.destinos[0].contactoId), Tiempo.zdt(e.momento))
    }

    override fun avisos(ctx: Context): List<Aviso> =
        Envios.todos(ctx).filter { !it.cerrado }.map { comoAviso(ctx, it) }

    override fun aviso(ctx: Context, id: Int): Aviso? =
        Envios.obtener(ctx, id)?.takeIf { !it.cerrado }?.let { comoAviso(ctx, it) }

    override fun alVencer(ctx: Context, a: Aviso, pospuesto: Boolean, perdido: Boolean, avisar: Boolean) {
        val e = Envios.obtener(ctx, a.id)
        // Apertura automática de WhatsApp y SMS automático solo a la hora (no con lo perdido mientras el celular estaba apagado).
        if (pospuesto || e == null) {
            val smsSolos = e != null && !perdido && SmsApp.automatico(ctx, e)
            if (avisar && !(smsSolos && e!!.pendientes.isEmpty()))
                Notificador.mostrar(ctx, a, comoAlarma = a.alarma && !perdido, pospuesta = pospuesto, abrir = !perdido)
            return
        }
        // Sin repetición: al vencer su fecha queda vencido (la posposición sigue aparte).
        // Con repetición: esa vez se separa en un envío aparte (es el que avisa) y el original sigue desde la próxima.
        val vez = if (e.regla.repite) separarVez(ctx, e) else e.copy(activo = false).also { Envios.guardar(ctx, it) }
        // SMS automático: se mandan solos y, si no queda nadie por WhatsApp, no hay alarma ni notificación de aviso
        // (solo "SMS enviado a N de M").
        val smsSolos = !perdido && SmsApp.automatico(ctx, vez)
        if (avisar && !(smsSolos && vez.pendientes.isEmpty()))
            Notificador.mostrar(ctx, comoAviso(ctx, vez), comoAlarma = vez.alarma && !perdido, pospuesta = false,
                abrir = !perdido)
    }

    override fun guardarPospuesto(ctx: Context, id: Int, hasta: Long) {
        Envios.obtener(ctx, id)?.let { Envios.guardar(ctx, it.copy(pospuestoHasta = hasta)) }
    }

    override fun alHecho(ctx: Context, id: Int) {
        // "Hecho" después de vencer y sin posposición pendiente: el envío se cierra.
        Envios.obtener(ctx, id)?.takeIf { !it.activo && it.pospuestoHasta == 0L }
            ?.let { Envios.guardar(ctx, it.copy(cerrado = true)) }
    }

    /**
     * Botón "Enviar" (mientras quede alguien sin enviar por WhatsApp): abre la pantalla de envío.
     * Botón "Enviar SMS" (mientras quede algún SMS por mandar): manda todos los SMS del envío
     * (o abre la pantalla de envío, si falta el permiso o hay que hacer el control de tamaño).
     * Con los dos, Android muestra 3 botones: "Posponer…" queda en la pantalla de alarma y al tocar la notificación.
     */
    override fun accionesExtra(ctx: Context, a: Aviso): List<Notification.Action> {
        val e = Envios.obtener(ctx, a.id) ?: return emptyList()
        if (e.cerrado) return emptyList()
        val botones = mutableListOf<Notification.Action>()
        if (e.pendientes.isNotEmpty()) {
            val pi = PendingIntent.getActivity(ctx, a.id + 2_000_000,
                Intent(ctx, EnviarActivity::class.java).putExtra(Programador.EXTRA_ID, a.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            botones += Notificador.boton(ctx, "Enviar", pi)
        }
        val sms = SmsApp.porMandar(ctx, e)   // sin los que ya están en camino (ni con los SMS apagados)
        if (sms.isNotEmpty()) botones += SmsApp.boton(ctx, e, sms.size)
        return botones
    }

    override fun alResultadoSms(ctx: Context, ref: String, ok: Boolean, motivo: String) =
        SmsApp.alResultado(ctx, ref, ok, motivo)

    /**
     * Apertura automática (el núcleo ya revisa permiso y bloqueo): abre la pantalla de envío en modo
     * automático, que abre WhatsApp con el primer contacto pendiente.
     */
    override fun aperturaAutomatica(ctx: Context, a: Aviso): Intent? {
        val e = Envios.obtener(ctx, a.id) ?: return null
        if (e.cerrado || e.pendientes.isEmpty() || !AjustesApp.autoAbrir(ctx, e)) return null
        return Intent(ctx, EnviarActivity::class.java).putExtra(Programador.EXTRA_ID, a.id)
            .putExtra(EnviarActivity.EXTRA_AUTO, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    override fun motivoSinApertura(ctx: Context, a: Aviso): String {
        val e = Envios.obtener(ctx, a.id) ?: return "el envío ya no existe"
        return when {
            e.cerrado || e.pendientes.isEmpty() -> "no queda nadie sin enviar por WhatsApp"
            e.autoAbrir == false -> "apagada en este envío"
            !AjustesApp.autoAbrir(ctx, e) -> "apagada en Ajustes"
            else -> "no corresponde"
        }
    }

    /** Con "Enviar" no entra "Hecho" (Android muestra 3 botones): queda en la pantalla de envío y en la alarma. */
    override fun mostrarHecho(ctx: Context, a: Aviso) = accionesExtra(ctx, a).isEmpty()

    override val pantallaPrincipal = MainActivity::class.java
    override val pantallaPosponer = PosponerActivity::class.java
    override val pantallaAlarma = AlarmaActivity::class.java
    override val nombreCanal = "Mensajes programados"
    override val iconoNotificacion = R.drawable.ic_notif_msg
}
