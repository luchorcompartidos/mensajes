package com.mismensajes.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mis.nucleo.Notificador
import com.mis.nucleo.Programador
import com.mis.nucleo.Registro
import com.mis.nucleo.Sms
import com.mis.nucleo.Tiempo
import com.mis.nucleo.controlarTamanioSms
import java.time.ZonedDateTime

/**
 * SMS de los envíos. Dos modos (por envío, [Envio.smsAuto]):
 * - "Preguntar antes" (por defecto): al tocar "Enviar SMS" se mandan todos los SMS pendientes del envío.
 * - "Automático": a la hora del envío se mandan solos, sin alarma; aviso "SMS enviado a N de M".
 * Android confirma cada uno y recién ahí se marca como enviado (núcleo: [Sms.enviar] →
 * [ManejadorMensajes.alResultadoSms]). Con todo enviado (WhatsApp y SMS), el envío se cierra solo.
 * Si uno falla: aviso con "Reintentar" y "Abrir app de SMS".
 */
object SmsApp {
    private const val CANAL = "sms_resultados"
    const val ACCION_ENVIAR = "com.mismensajes.app.ENVIAR_SMS"
    const val ACCION_REINTENTAR = "com.mismensajes.app.REINTENTAR_SMS"
    const val EXTRA_CONTACTO = "contacto"
    private const val PRUEBA = "prueba"

    /**
     * Texto del SMS para ese destinatario: el editado/convertido en el control de tamaño, o el de WhatsApp
     * con las variables reemplazadas y "→" como "->" (igual que Recordatorios). [ahora]: para las variables de fecha/hora.
     */
    fun texto(ctx: Context, e: Envio, d: Destino, ahora: ZonedDateTime = ZonedDateTime.now()): String =
        d.smsTexto.ifBlank { Sms.paraSms(Variables.aplicar(e.textoPara(d), Contactos.obtener(ctx, d.contactoId), ahora)) }

    private fun ref(id: Int, contactoId: Int) = "$id:$contactoId"

    /**
     * El SMS de ese contacto ya se le pasó a Android y falta la confirmación (para no mandarlo dos veces).
     * Lee el estado que guarda el núcleo (`Sms.PREFS`); alinear con una función del núcleo cuando se pueda tocar.
     */
    fun enCamino(ctx: Context, id: Int, contactoId: Int) =
        ctx.getSharedPreferences("sms_envios", Context.MODE_PRIVATE).contains(ref(id, contactoId))

    private fun olvidarEnCamino(ctx: Context, ref: String) =
        ctx.getSharedPreferences("sms_envios", Context.MODE_PRIVATE).edit().remove(ref).commit()

    /** SMS que faltan mandar (sin los que están en camino). Con los SMS apagados en Ajustes, ninguno. */
    fun porMandar(ctx: Context, e: Envio): List<Destino> =
        if (!AjustesApp.smsActivo(ctx) || e.cerrado) emptyList()
        else e.pendientesSms.filter { !enCamino(ctx, e.id, it.contactoId) }

    /** A quiénes les falta algo; con los SMS apagados en Ajustes solo cuenta WhatsApp. */
    fun faltan(ctx: Context, e: Envio): List<Destino> = if (AjustesApp.smsActivo(ctx)) e.faltan else e.pendientes

    /** Destinatarios cuyo SMS se dividiría y todavía no se decidió qué hacer (control "se dividiría en N partes"). */
    fun sinControlar(ctx: Context, e: Envio, destinos: List<Destino>, ahora: ZonedDateTime = ZonedDateTime.now()) =
        destinos.filter { it.canal.sms && !it.smsDecidido && Sms.partes(texto(ctx, e, it, ahora)) > 1 }

    /**
     * Manda los SMS pendientes del envío. Devuelve un resumen para mostrar
     * ("SMS en camino a 2 contactos", o el motivo si no se pudo).
     */
    fun enviarTodos(ctx: Context, id: Int): String {
        val e = Envios.obtener(ctx, id) ?: return "El envío ya no existe"
        if (e.cerrado) return "El envío ya está cerrado"
        if (!AjustesApp.smsActivo(ctx)) return "Los SMS están apagados en Ajustes"
        if (e.pendientesSms.isEmpty()) return "No queda ningún SMS por enviar"
        val pendientes = porMandar(ctx, e)
        if (pendientes.isEmpty()) return "Los SMS ya están en camino: falta la confirmación de Android"
        if (!Sms.permitido(ctx)) return "Falta el permiso de SMS"
        var ok = 0
        val errores = mutableListOf<String>()
        pendientes.forEach { d ->
            val c = Contactos.obtener(ctx, d.contactoId)
            if (c == null) { errores += "(contacto borrado)"; return@forEach }
            val error = mandar(ctx, e, d, c)
            if (error == null) ok++ else errores += "${c.etiqueta}: $error"
        }
        Registro.anotar(ctx, "#$id Enviar SMS: $ok pasado(s) a Android" +
            if (errores.isEmpty()) "" else ", ${errores.size} con error")
        return (if (ok > 0) "SMS en camino a $ok contacto(s)" else "No se mandó ningún SMS") +
            if (errores.isEmpty()) "" else ". No se pudo: " + errores.joinToString("; ")
    }

    /** Manda (o vuelve a mandar) el SMS de un solo destinatario. Devuelve el resumen para mostrar. */
    fun enviarUno(ctx: Context, id: Int, contactoId: Int): String {
        if (!AjustesApp.smsActivo(ctx)) return "Los SMS están apagados en Ajustes"
        val e = Envios.obtener(ctx, id) ?: return "El envío ya no existe"
        val d = e.destinos.firstOrNull { it.contactoId == contactoId } ?: return "Ese contacto ya no está en el envío"
        val c = Contactos.obtener(ctx, contactoId) ?: return "Ese contacto ya no existe"
        val error = mandar(ctx, e, d, c)
        return if (error == null) "SMS en camino a ${c.etiqueta}" else "No se pudo mandar el SMS a ${c.etiqueta}: $error"
    }

    private fun mandar(ctx: Context, e: Envio, d: Destino, c: Contacto): String? {
        val r = ref(e.id, d.contactoId)
        if (d.smsFalla.isNotEmpty()) Envios.fallaSms(ctx, e.id, d.contactoId, "")
        quitarAvisoFalla(ctx, e.id, d.contactoId)
        val error = Sms.enviar(ctx, c.numero, texto(ctx, e, d), ref = r, etiqueta = c.etiqueta)
        if (error != null) olvidarEnCamino(ctx, r)   // no salió: que no quede "en camino"
        return error
    }

    /**
     * Modo automático, a la hora del envío (o de la posposición): manda los SMS pendientes sin preguntar.
     * El control de tamaño ya se hizo al guardar. Devuelve true si los mandó (entonces no hace falta alarma
     * si no queda WhatsApp). Sin permiso o con los SMS apagados, avisa como siempre (botón "Enviar SMS").
     */
    fun automatico(ctx: Context, e: Envio): Boolean {
        if (!e.smsAuto || e.cerrado || e.pendientesSms.isEmpty()) return false
        if (!AjustesApp.smsActivo(ctx)) { Registro.anotar(ctx, "#${e.id} SMS automático: no (apagados en Ajustes)"); return false }
        if (!Sms.permitido(ctx)) { Registro.anotar(ctx, "#${e.id} SMS automático: no (falta el permiso de SMS)"); return false }
        Registro.anotar(ctx, "#${e.id} SMS automático: " + enviarTodos(ctx, e.id))
        Envios.obtener(ctx, e.id)?.let { avisarAutomatico(ctx, it) }
        return true
    }

    /** Resultado que confirma Android (ref = "idEnvío:idContacto", o "prueba:…" para el SMS de prueba). */
    fun alResultado(ctx: Context, ref: String, ok: Boolean, motivo: String) {
        val partes = ref.split(":")
        if (partes.getOrNull(0) == PRUEBA) { avisarPrueba(ctx, ok, motivo); return }
        val id = partes.getOrNull(0)?.toIntOrNull() ?: return
        val contactoId = partes.getOrNull(1)?.toIntOrNull() ?: return
        val nombre = Contactos.obtener(ctx, contactoId)?.etiqueta ?: "(contacto borrado)"
        if (!ok) {
            Registro.anotar(ctx, "#$id SMS a «$nombre» falló: $motivo")
            val e = Envios.fallaSms(ctx, id, contactoId, motivo)
            avisarFalla(ctx, id, contactoId, nombre, motivo)
            if (e != null && e.smsAuto) avisarAutomatico(ctx, e)
            Notificador.actualizar(ctx, id)
            return
        }
        val e = Envios.marcarSms(ctx, id, contactoId) ?: return
        Registro.anotar(ctx, "#$id SMS a «$nombre» marcado como enviado")
        quitarAvisoFalla(ctx, id, contactoId)
        if (e.smsAuto) avisarAutomatico(ctx, e)
        if (faltan(ctx, e).isEmpty() && !e.cerrado) {
            cerrarEnvio(ctx, id, "enviado a todos")
            Notificador.quitar(ctx, id)
        } else if (!e.cerrado) Notificador.actualizar(ctx, id)
    }

    private fun canal(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Resultado de SMS", NotificationManager.IMPORTANCE_DEFAULT))
        return nm
    }

    private fun pantalla(ctx: Context, id: Int, codigo: Int, extra: Intent.() -> Unit = {}) =
        PendingIntent.getActivity(ctx, codigo,
            Intent(ctx, EnviarActivity::class.java).putExtra(Programador.EXTRA_ID, id).apply(extra)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun tagFalla(id: Int) = "sms_$id"

    fun quitarAvisoFalla(ctx: Context, id: Int, contactoId: Int) =
        ctx.getSystemService(NotificationManager::class.java).cancel(tagFalla(id), contactoId)

    /** Aviso aparte si un SMS no salió: "Reintentar" (manda de nuevo) y "Abrir app de SMS" (con el texto escrito). */
    private fun avisarFalla(ctx: Context, id: Int, contactoId: Int, nombre: String, motivo: String) {
        val nm = canal(ctx)
        val codigo = (id * 1000 + contactoId % 1000) % 1_000_000
        val reintentar = PendingIntent.getBroadcast(ctx, 6_000_000 + codigo,
            Intent(ctx, SmsAccionReceiver::class.java).setAction(ACCION_REINTENTAR)
                .putExtra(Programador.EXTRA_ID, id).putExtra(EXTRA_CONTACTO, contactoId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val abrirApp = pantalla(ctx, id, 7_000_000 + codigo) { putExtra(EnviarActivity.EXTRA_APP_SMS, contactoId) }
        val t = "No salió el SMS a $nombre ($motivo). Tocá para abrir el envío."
        nm.notify(tagFalla(id), contactoId, Notification.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notif_msg)
            .setContentTitle("SMS no enviado")
            .setContentText(t)
            .setStyle(Notification.BigTextStyle().bigText(t))
            .setContentIntent(pantalla(ctx, id, 3_000_000 + codigo))
            .addAction(Notificador.boton(ctx, "Reintentar", reintentar))
            .addAction(Notificador.boton(ctx, "Abrir app de SMS", abrirApp))
            .setAutoCancel(true)
            .build())
    }

    /** Modo automático: "SMS enviado a N de M" (se actualiza con cada confirmación de Android). */
    private fun avisarAutomatico(ctx: Context, e: Envio) {
        val conSms = e.destinos.filter { it.canal.sms }
        val ok = conSms.count { it.smsEnviado }
        val fallas = conSms.count { !it.smsEnviado && it.smsFalla.isNotEmpty() }
        val t = "SMS enviado a $ok de ${conSms.size}" + if (fallas > 0) " · $fallas no salieron" else ""
        canal(ctx).notify("smsauto", e.id, Notification.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notif_msg)
            .setContentTitle(Envios.titulo(ctx, e))
            .setContentText(t)
            .setContentIntent(pantalla(ctx, e.id, 8_000_000 + e.id % 1_000_000))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build())
    }

    /** "Enviar SMS de prueba" de Ajustes: un SMS real al número indicado; el resultado llega como aviso. */
    fun enviarPrueba(ctx: Context, numero: String): String? =
        Sms.enviar(ctx, numero, "Prueba de Programador de Mensajes (${Tiempo.completo(System.currentTimeMillis())})",
            ref = "$PRUEBA:${System.currentTimeMillis()}", etiqueta = "prueba ($numero)")

    private fun avisarPrueba(ctx: Context, ok: Boolean, motivo: String) {
        Registro.anotar(ctx, "SMS de prueba: " + if (ok) "enviado" else "no salió ($motivo)")
        canal(ctx).notify("smsprueba", 1, Notification.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notif_msg)
            .setContentTitle(if (ok) "SMS de prueba enviado" else "SMS de prueba no enviado")
            .setContentText(if (ok) "Android confirmó que salió." else "No salió: $motivo")
            .setAutoCancel(true)
            .build())
    }

    /**
     * Botón "Enviar SMS" de la notificación. Con el permiso ya dado y sin SMS que se dividan sin decidir,
     * manda directo (sin abrir nada); si no, abre la pantalla de envío, que pide el permiso o hace el control.
     */
    fun boton(ctx: Context, e: Envio, cantidad: Int): Notification.Action {
        val directo = Sms.permitido(ctx) && sinControlar(ctx, e, porMandar(ctx, e)).isEmpty()
        val pi = if (directo) PendingIntent.getBroadcast(ctx, e.id + 4_000_000,
                Intent(ctx, SmsAccionReceiver::class.java).setAction(ACCION_ENVIAR).putExtra(Programador.EXTRA_ID, e.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            else PendingIntent.getActivity(ctx, e.id + 4_000_000, intentPantalla(ctx, e.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notificador.boton(ctx, if (cantidad > 1) "Enviar SMS ($cantidad)" else "Enviar SMS", pi)
    }

    /** Pantalla de envío que manda los SMS al abrirse (pidiendo el permiso o haciendo el control si hace falta). */
    fun intentPantalla(ctx: Context, id: Int): Intent =
        Intent(ctx, EnviarActivity::class.java).putExtra(Programador.EXTRA_ID, id)
            .putExtra(EnviarActivity.EXTRA_SMS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Abre la app de SMS del celular con el número y el texto escritos. Devuelve null si se abrió, o el motivo. */
    fun abrirAppSms(act: Activity, numero: String, texto: String): String? {
        val num = numero.trim()
        if (num.count { it.isDigit() } < 6) return "El contacto no tiene un número válido"
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num))).putExtra("sms_body", texto)
        return try { act.startActivity(i); null } catch (e: ActivityNotFoundException) { "No se encontró una app de SMS" }
    }
}

/**
 * Control "El SMS se dividiría en N partes" (núcleo: [controlarTamanioSms], igual que Recordatorios) para esos
 * destinatarios del envío: agrupa los que tienen el mismo texto y pregunta una vez por grupo.
 * La decisión se guarda en cada destino, solo para este envío: "Enviar igual" → [Destino.smsOk];
 * editado o convertido → [Destino.smsTexto]. [listo] recibe el envío actualizado (sin guardar) y los
 * contactos que quedaron en "No enviar SMS". [ahora]: momento para las variables (al guardar, la hora programada).
 */
fun Activity.controlarSms(e: Envio, destinos: List<Destino>, ahora: ZonedDateTime = ZonedDateTime.now(),
                          listo: (Envio, List<Int>) -> Unit) {
    val grupos = SmsApp.sinControlar(this, e, destinos, ahora)
        .groupBy { SmsApp.texto(this, e, it, ahora) }.entries.toList()
    var actual = e
    val noEnviar = mutableListOf<Int>()
    fun siguiente(i: Int) {
        if (i >= grupos.size) { listo(actual, noEnviar); return }
        val (original, ds) = grupos[i]
        val ids = ds.map { it.contactoId }.toSet()
        controlarTamanioSms(original, ds.size) { decidido ->
            if (decidido == null) noEnviar += ids
            else actual = actual.copy(destinos = actual.destinos.map {
                if (it.contactoId !in ids) it
                else if (decidido == original) it.copy(smsOk = true) else it.copy(smsTexto = decidido)
            })
            Registro.anotar(this, "#${e.id} control de tamaño del SMS (${ds.size} contacto/s): " + when (decidido) {
                null -> "no enviar SMS"; original -> "enviar igual"; else -> "texto cambiado (${Sms.partes(decidido.orEmpty())} SMS)"
            })
            siguiente(i + 1)
        }
    }
    siguiente(0)
}

/** Botones "Enviar SMS" (con el permiso ya dado) y "Reintentar" de las notificaciones. */
class SmsAccionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val id = i.getIntExtra(Programador.EXTRA_ID, -1)
        when (i.action) {
            SmsApp.ACCION_ENVIAR -> {
                if (Notificador.sonando(ctx, id)) Notificador.silenciar(ctx, id)
                Registro.anotar(ctx, "#$id «Enviar SMS» tocado en la notificación")
                SmsApp.enviarTodos(ctx, id)
            }
            SmsApp.ACCION_REINTENTAR -> {
                val contactoId = i.getIntExtra(SmsApp.EXTRA_CONTACTO, -1)
                Registro.anotar(ctx, "#$id «Reintentar» SMS: " + SmsApp.enviarUno(ctx, id, contactoId))
            }
            else -> return
        }
        Notificador.actualizar(ctx, id)
    }
}
