package com.mismensajes.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.text.InputType
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.mis.nucleo.Ajustes
import com.mis.nucleo.Apertura
import com.mis.nucleo.Programador
import com.mis.nucleo.Registro
import com.mis.nucleo.Tiempo
import com.mis.nucleo.Notificador
import com.mis.nucleo.Nucleo
import com.mis.nucleo.VersionGitHub
import com.mis.nucleo.Posposicion
import com.mis.nucleo.Sms
import com.mis.nucleo.R as RN

/**
 * Ajustes de la app: con qué WhatsApp se abren los envíos (común, Business o preguntar cada vez; cada envío
 * puede cambiarlo desde su pantalla de envío), el botón de posponer rápido, si los envíos nuevos suenan como alarma y "Acerca de" (versión y Changelog).
 * Estos dos últimos se guardan en el núcleo (preferencias "ajustes", mismas claves que Recordatorios).
 * También la apertura automática de WhatsApp (encendida por defecto) con el estado del permiso
 * "Mostrar sobre otras apps" y un envío de prueba. SMS: interruptor general y "Enviar SMS de prueba".
 */
private const val PEDIDO_SMS = 7

class AjustesActivity : Activity() {
    private var refrescarPermiso: () -> Unit = {}

    override fun onResume() {
        super.onResume()
        refrescarPermiso()   // al volver de la pantalla del permiso
    }

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        title = "Ajustes"
        actionBar?.setDisplayHomeAsUpEnabled(true)
        val dp = resources.displayMetrics.density
        val m = (24 * dp).toInt()
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(m, m / 2, m, m)
        }
        caja.addView(subtitulo("WhatsApp"))
        caja.addView(nota("Con qué WhatsApp se abren los envíos cuando están instalados los dos. " +
            "En cada envío se puede cambiar desde su pantalla de envío."))

        val codigos = listOf(WhatsApp.COMUN, WhatsApp.BUSINESS, WhatsApp.PREGUNTAR)
        val grupo = RadioGroup(this)
        codigos.forEachIndexed { i, codigo ->
            grupo.addView(RadioButton(this).apply {
                id = 100 + i
                text = WhatsApp.nombre(codigo)
                textSize = 16f
                setTextColor(getColor(RN.color.texto))
                minHeight = (44 * dp).toInt()
            })
        }
        grupo.check(100 + codigos.indexOf(AjustesApp.whatsapp(this)).coerceAtLeast(0))
        grupo.setOnCheckedChangeListener { _, id -> AjustesApp.guardarWhatsapp(this, codigos[id - 100]) }
        caja.addView(grupo)

        val instalados = WhatsApp.instalados(this)
        caja.addView(nota(when (instalados.size) {
            2 -> "Instalados en este celular: WhatsApp y WhatsApp Business."
            1 -> "En este celular solo está ${WhatsApp.nombre(instalados[0])}: se usa ese, sin importar este ajuste."
            else -> "No se encontró WhatsApp en este celular: los envíos se intentan abrir por el navegador."
        }))

        caja.addView(subtitulo("Apertura automática"))
        caja.addView(Switch(this).apply {
            text = "Abrir WhatsApp automáticamente a la hora del envío"
            textSize = 15f
            setTextColor(getColor(RN.color.texto))
            minHeight = (48 * dp).toInt()
            isChecked = AjustesApp.autoAbrir(this@AjustesActivity)
            setOnCheckedChangeListener { _, valor -> AjustesApp.guardarAutoAbrir(this@AjustesActivity, valor); refrescarPermiso() }
        })
        caja.addView(nota("Con el celular desbloqueado, a la hora del envío se abre WhatsApp con el chat y el texto ya " +
            "escritos: solo falta tocar «Enviar». Con varios contactos, al confirmar «Sí, enviado» sigue con el próximo. " +
            "Con el celular bloqueado, sin el permiso o con la opción apagada, avisa como siempre (alarma o notificación). " +
            "La notificación sale siempre, de respaldo. En cada envío se puede cambiar."))
        val estadoPermiso = nota("")
        val btnPermiso = Button(this).apply {
            isAllCaps = false
            text = "Conceder «Mostrar sobre otras apps»"
            setOnClickListener { pedirPermisoApertura() }
        }
        val ayudaPermiso = nota("Si Android no deja activarlo (app instalada a mano): Ajustes del sistema > Apps > " +
            "Programador de Mensajes > ⋮ (arriba a la derecha) > «Permitir ajustes restringidos», y volvé a intentar.")
        refrescarPermiso = {
            val ok = Apertura.permitida(this)
            estadoPermiso.text = "Permiso «Mostrar sobre otras apps»: " + if (ok) "✓ concedido" else "✗ falta"
            btnPermiso.visibility = if (ok) android.view.View.GONE else android.view.View.VISIBLE
            ayudaPermiso.visibility = btnPermiso.visibility
        }
        refrescarPermiso()
        caja.addView(estadoPermiso)
        caja.addView(btnPermiso)
        caja.addView(ayudaPermiso)
        caja.addView(Button(this).apply {
            isAllCaps = false
            text = "Probar apertura automática (envío de prueba en 1 minuto)"
            setOnClickListener { probarApertura() }
        })

        caja.addView(subtitulo("SMS"))
        caja.addView(Switch(this).apply {
            text = "Mandar SMS"
            textSize = 15f
            setTextColor(getColor(RN.color.texto))
            minHeight = (48 * dp).toInt()
            isChecked = AjustesApp.smsActivo(this@AjustesActivity)
            setOnCheckedChangeListener { _, valor ->
                AjustesApp.guardarSmsActivo(this@AjustesActivity, valor)
                Registro.anotar(this@AjustesActivity, "SMS " + if (valor) "encendidos en Ajustes" else "apagados en Ajustes")
                // Las notificaciones en la bandeja se redibujan (con o sin «Enviar SMS»).
                Envios.todos(this@AjustesActivity).filter { !it.cerrado && it.usaSms && !Notificador.sonando(this@AjustesActivity, it.id) }
                    .forEach { Notificador.actualizar(this@AjustesActivity, it.id) }
            }
        })
        caja.addView(nota("Apagado: no se manda ningún SMS (ni con «Enviar SMS» ni automático) y los envíos se cierran " +
            "solos cuando se envió todo por WhatsApp. Los contactos y envíos conservan su modo. " +
            "Se usa la SIM predeterminada; cada SMS puede tener costo según tu plan."))
        caja.addView(Button(this).apply {
            isAllCaps = false
            text = "Enviar SMS de prueba"
            setOnClickListener { smsPrueba() }
        })

        caja.addView(subtitulo("Avisos"))
        caja.addView(nota("El botón de posponer rápido es el que aparece en la notificación y en la pantalla de alarma. " +
            "Con «Personalizado…» se abre la pantalla para elegir el plazo."))
        val btnRapido = Button(this).apply { isAllCaps = false }
        fun mostrarRapido() { btnRapido.text = Posposicion.textoBoton(Ajustes.posponerRapido(this)) }
        mostrarRapido()
        btnRapido.setOnClickListener { elegirPosponerRapido { mostrarRapido() } }
        caja.addView(btnRapido)

        caja.addView(Switch(this).apply {
            text = "Envíos nuevos: sonar como alarma (pantalla completa)"
            textSize = 15f
            setTextColor(getColor(RN.color.texto))
            minHeight = (48 * dp).toInt()
            isChecked = Ajustes.alarmaPorDefecto(this@AjustesActivity)
            setOnCheckedChangeListener { _, valor -> Ajustes.guardarAlarmaPorDefecto(this@AjustesActivity, valor) }
        })
        caja.addView(nota("Es lo que viene marcado al crear un envío; en cada envío se puede cambiar."))

        caja.addView(subtitulo("Acerca de"))
        val estadoVersion = nota("")
        fun refrescarVersion() {
            val n = VersionGitHub.nueva(this)
            val ult = VersionGitHub.guardadas(this).firstOrNull()
            estadoVersion.text = "Versión instalada: ${VersionGitHub.instalada(this)} · núcleo ${Nucleo.VERSION}" + when {
                n != null -> "\nHay una versión nueva: ${n.tag} (${VersionGitHub.fecha(n.fecha)})"
                ult != null -> "\nÚltima publicada: ${ult.tag} (${VersionGitHub.fecha(ult.fecha)})"
                else -> ""
            }
        }
        refrescarVersion()
        VersionGitHub.consultar(this) { if (!isFinishing) refrescarVersion() }
        caja.addView(estadoVersion)
        caja.addView(Button(this).apply {
            isAllCaps = false
            text = "Versión y actualizaciones"
            setOnClickListener {
                VersionGitHub.consultar(this@AjustesActivity, forzar = true) {
                    if (!isFinishing) { refrescarVersion(); VersionGitHub.dialogo(this@AjustesActivity, temaDialogo()) }
                }
            }
        })
        caja.addView(Button(this).apply {
            isAllCaps = false
            text = "Ver cambios (Changelog)"
            setOnClickListener {
                VersionGitHub.consultar(this@AjustesActivity, forzar = true) {
                    if (!isFinishing) { refrescarVersion(); VersionGitHub.changelog(this@AjustesActivity, temaDialogo()) }
                }
            }
        })
        caja.addView(Button(this).apply {
            isAllCaps = false
            text = "Descargar instalador de la última versión"
            setOnClickListener { VersionGitHub.descargar(this@AjustesActivity, VersionGitHub.guardadas(this@AjustesActivity).firstOrNull()) }
        })

        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(caja) })
    }

    private fun pedirPermisoApertura() {
        try { startActivity(Apertura.intentPermiso(this)) }
        catch (e: Exception) { avisar("No se pudo abrir la pantalla del permiso: buscalo en Ajustes del sistema > Apps") }
    }

    /**
     * Envío de prueba para dentro de 1 minuto, a un contacto elegido (sigue la opción de Ajustes, para poder
     * probar también con la opción apagada). El resultado de cada intento queda en el Registro de eventos.
     */
    private fun probarApertura() {
        val contactos = Contactos.todos(this)
        if (contactos.isEmpty()) { avisar("Primero cargá un contacto en «Contactos»"); return }
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Probar con el contacto")
            .setItems(contactos.map { it.etiqueta }.toTypedArray()) { _, i ->
                val momento = System.currentTimeMillis() + 60_000L
                val e = Envio(id = Envios.nuevoId(this), titulo = "Prueba de apertura automática",
                    texto = "Prueba de apertura automática (no hace falta enviarlo)",
                    destinos = listOf(Destino(contactos[i].id)), momento = momento)
                Envios.guardar(this, e)
                Registro.anotar(this, "#${e.id} envío de prueba de apertura automática creado")
                Programador.programar(this, ManejadorMensajes.comoAviso(this, e))
                AlertDialog.Builder(this, temaDialogo())
                    .setTitle("Envío de prueba programado")
                    .setMessage("Suena a las ${Tiempo.hora(momento)}:${"%02d".format(Tiempo.zdt(momento).second)}. " +
                        "Dejá el celular como en el caso a probar (en el inicio, usando otra app, bloqueado…). " +
                        "WhatsApp no envía nada solo: no hace falta tocar «Enviar». " +
                        "El resultado queda en el «Registro de eventos» de la pantalla principal. " +
                        "Después podés borrar el envío de prueba (mantener presionado en la lista).")
                    .setPositiveButton("Entendido", null)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** "Enviar SMS de prueba": pide el número, confirma que es un SMS real (con posible costo) y lo manda. */
    private fun smsPrueba() {
        if (!Sms.permitido(this)) { requestPermissions(arrayOf(Manifest.permission.SEND_SMS), PEDIDO_SMS); return }
        val m = (20 * resources.displayMetrics.density).toInt()
        val campo = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "Número (ej. 1123456789)"
            setText(AjustesApp.numeroPrueba(this@AjustesActivity))
        }
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Enviar SMS de prueba")
            .setMessage("Se manda un SMS real (con la SIM predeterminada); puede tener costo según tu plan. " +
                "El resultado llega como aviso y queda en el Registro de eventos.")
            .setView(LinearLayout(this).apply { setPadding(m, 0, m, 0); addView(campo) })
            .setPositiveButton("Enviar") { _, _ ->
                val numero = campo.text.toString().trim()
                AjustesApp.guardarNumeroPrueba(this, numero)
                val error = SmsApp.enviarPrueba(this, numero)
                avisar(if (error == null) "SMS de prueba en camino a $numero" else "No se pudo mandar: $error")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onRequestPermissionsResult(pedido: Int, permisos: Array<out String>, resultados: IntArray) {
        super.onRequestPermissionsResult(pedido, permisos, resultados)
        if (pedido != PEDIDO_SMS) return
        if (Sms.permitido(this)) smsPrueba()
        else AlertDialog.Builder(this, temaDialogo())
            .setTitle("Falta el permiso de SMS")
            .setMessage("Sin el permiso de SMS no se pueden mandar. Si Android no deja darlo (la app se instaló a mano): " +
                "Ajustes del celular > Apps > Programador de Mensajes > ⋮ > «Permitir ajustes restringidos».")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun elegirPosponerRapido(listo: () -> Unit) {
        val opciones = Posposicion.OPCIONES_RAPIDO
        val actual = opciones.indexOfFirst { it.codigo == Ajustes.posponerRapido(this) }
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Posponer rápido")
            .setSingleChoiceItems(opciones.map { it.texto }.toTypedArray(), actual) { d, i ->
                Ajustes.guardarPosponerRapido(this, opciones[i].codigo)
                // Las notificaciones que ya están en la bandeja se redibujan con el botón nuevo (sin sonar; una alarma sonando no se toca).
                Envios.todos(this).filter { !it.cerrado && !Notificador.sonando(this, it.id) }.forEach { Notificador.actualizar(this, it.id) }
                d.dismiss()
                listo()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun nota(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(getColor(RN.color.texto2))
        setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
