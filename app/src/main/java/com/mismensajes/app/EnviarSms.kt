package com.mismensajes.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mis.nucleo.Notificador
import com.mis.nucleo.Programador
import com.mis.nucleo.Registro
import com.mis.nucleo.Sms

/**
 * SMS de los envíos (modo "Preguntar antes"): al tocar "Enviar SMS" se mandan todos los SMS pendientes
 * del envío. Android confirma cada uno y recién ahí se marca como enviado (núcleo: [Sms.enviar] →
 * [ManejadorMensajes.alResultadoSms]). Con todo enviado (WhatsApp y SMS), el envío se cierra solo.
 */
object SmsApp {
    private const val CANAL = "sms_resultados"
    const val ACCION_ENVIAR = "com.mismensajes.app.ENVIAR_SMS"

    /** Texto del SMS para ese destinatario: variables reemplazadas y "→" como "->" (igual que Recordatorios). */
    fun texto(ctx: Context, e: Envio, d: Destino): String =
        Sms.paraSms(Variables.aplicar(e.textoPara(d), Contactos.obtener(ctx, d.contactoId)))

    /**
     * Manda los SMS pendientes del envío. Devuelve un resumen para mostrar
     * ("SMS en camino a 2 contactos", o el motivo si no se pudo).
     */
    fun enviarTodos(ctx: Context, id: Int): String {
        val e = Envios.obtener(ctx, id) ?: return "El envío ya no existe"
        if (e.cerrado) return "El envío ya está cerrado"
        val pendientes = e.pendientesSms
        if (pendientes.isEmpty()) return "No queda ningún SMS por enviar"
        if (!Sms.permitido(ctx)) return "Falta el permiso de SMS"
        var ok = 0
        val errores = mutableListOf<String>()
        pendientes.forEach { d ->
            val c = Contactos.obtener(ctx, d.contactoId)
            if (c == null) { errores += "(contacto borrado)"; return@forEach }
            val error = Sms.enviar(ctx, c.numero, texto(ctx, e, d), ref = "$id:${d.contactoId}", etiqueta = c.etiqueta)
            if (error == null) ok++ else errores += "${c.etiqueta}: $error"
        }
        Registro.anotar(ctx, "#$id Enviar SMS: $ok pasado(s) a Android" +
            if (errores.isEmpty()) "" else ", ${errores.size} con error")
        return (if (ok > 0) "SMS en camino a $ok contacto(s)" else "No se mandó ningún SMS") +
            if (errores.isEmpty()) "" else ". No se pudo: " + errores.joinToString("; ")
    }

    /** Manda (o vuelve a mandar) el SMS de un solo destinatario. Devuelve el resumen para mostrar. */
    fun enviarUno(ctx: Context, id: Int, contactoId: Int): String {
        val e = Envios.obtener(ctx, id) ?: return "El envío ya no existe"
        val d = e.destinos.firstOrNull { it.contactoId == contactoId } ?: return "Ese contacto ya no está en el envío"
        val c = Contactos.obtener(ctx, contactoId) ?: return "Ese contacto ya no existe"
        val error = Sms.enviar(ctx, c.numero, texto(ctx, e, d), ref = "$id:$contactoId", etiqueta = c.etiqueta)
        return if (error == null) "SMS en camino a ${c.etiqueta}" else "No se pudo mandar el SMS a ${c.etiqueta}: $error"
    }

    /** Resultado que confirma Android (ref = "idEnvío:idContacto"). */
    fun alResultado(ctx: Context, ref: String, ok: Boolean, motivo: String) {
        val partes = ref.split(":")
        val id = partes.getOrNull(0)?.toIntOrNull() ?: return
        val contactoId = partes.getOrNull(1)?.toIntOrNull() ?: return
        val nombre = Contactos.obtener(ctx, contactoId)?.etiqueta ?: "(contacto borrado)"
        if (!ok) {
            Registro.anotar(ctx, "#$id SMS a «$nombre» falló: $motivo")
            avisarFalla(ctx, id, nombre, motivo)
            Notificador.actualizar(ctx, id)
            return
        }
        val e = Envios.marcarSms(ctx, id, contactoId) ?: return
        Registro.anotar(ctx, "#$id SMS a «$nombre» marcado como enviado")
        if (e.faltan.isEmpty() && !e.cerrado) {
            cerrarEnvio(ctx, id, "enviado a todos")
            Notificador.quitar(ctx, id)
        } else if (!e.cerrado) Notificador.actualizar(ctx, id)
    }

    /** Aviso aparte si un SMS no salió (abre la pantalla de envío para reintentar). */
    private fun avisarFalla(ctx: Context, id: Int, nombre: String, motivo: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Resultado de SMS", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(ctx, id + 3_000_000,
            Intent(ctx, EnviarActivity::class.java).putExtra(Programador.EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val t = "No salió el SMS a $nombre ($motivo). Tocá para abrir el envío y volver a intentar."
        nm.notify("sms_$id", id, Notification.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notif_msg)
            .setContentTitle("SMS no enviado")
            .setContentText(t)
            .setStyle(Notification.BigTextStyle().bigText(t))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build())
    }

    /**
     * Botón "Enviar SMS" de la notificación. Con el permiso ya dado, manda directo (sin abrir nada);
     * si falta, abre la pantalla de envío, que lo pide y manda.
     */
    fun boton(ctx: Context, id: Int, cantidad: Int): Notification.Action {
        val pi = if (Sms.permitido(ctx)) PendingIntent.getBroadcast(ctx, id + 4_000_000,
                Intent(ctx, SmsAccionReceiver::class.java).setAction(ACCION_ENVIAR).putExtra(Programador.EXTRA_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            else PendingIntent.getActivity(ctx, id + 4_000_000, intentPantalla(ctx, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notificador.boton(ctx, if (cantidad > 1) "Enviar SMS ($cantidad)" else "Enviar SMS", pi)
    }

    /** Pantalla de envío que manda los SMS al abrirse (pidiendo el permiso si falta). */
    fun intentPantalla(ctx: Context, id: Int): Intent =
        Intent(ctx, EnviarActivity::class.java).putExtra(Programador.EXTRA_ID, id)
            .putExtra(EnviarActivity.EXTRA_SMS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}

/** "Enviar SMS" desde la notificación (con el permiso ya dado): calla la alarma y manda los SMS. */
class SmsAccionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != SmsApp.ACCION_ENVIAR) return
        val id = i.getIntExtra(Programador.EXTRA_ID, -1)
        if (Notificador.sonando(ctx, id)) Notificador.silenciar(ctx, id)
        Registro.anotar(ctx, "#$id «Enviar SMS» tocado en la notificación")
        SmsApp.enviarTodos(ctx, id)
        Notificador.actualizar(ctx, id)
    }
}
