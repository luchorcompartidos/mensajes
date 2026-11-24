package com.mismensajes.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mis.nucleo.Apertura
import com.mis.nucleo.Nucleo
import com.mis.nucleo.Permisos
import com.mis.nucleo.Programador
import com.mis.nucleo.Recurrencia
import com.mis.nucleo.Registro
import com.mis.nucleo.Tiempo
import com.mis.nucleo.VersionGitHub
import com.mis.nucleo.R as RN

/** Pantalla principal: envíos programados (por próxima fecha) y ya vencidos. */
class MainActivity : Activity() {
    private var verRegistro = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Tocar la versión: fecha de la Release, aviso de versión nueva y Changelog.
        findViewById<TextView>(R.id.version).setOnClickListener {
            VersionGitHub.consultar(this, forzar = true) { if (!isFinishing) { mostrarVersion(); VersionGitHub.dialogo(this, temaDialogo()) } }
        }
        findViewById<Button>(R.id.btnNuevo).setOnClickListener {
            startActivity(Intent(this, EditarActivity::class.java))
        }
        findViewById<Button>(R.id.btnContactos).setOnClickListener {
            startActivity(Intent(this, ContactosActivity::class.java))
        }
        findViewById<Button>(R.id.btnPlantillas).setOnClickListener {
            startActivity(Intent(this, PlantillasActivity::class.java))
        }
        findViewById<Button>(R.id.btnImportar).setOnClickListener { ayudaImportar() }
        findViewById<TextView>(R.id.btnAjustes).setOnClickListener {
            startActivity(Intent(this, AjustesActivity::class.java))
        }
        findViewById<TextView>(R.id.tituloRegistro).setOnClickListener {
            verRegistro = !verRegistro
            actualizar()
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        // Si la app se detuvo a la fuerza, Android borra sus alarmas: al abrirla se vuelven a programar.
        Programador.reprogramarTodos(this, margenMs = 2 * 60_000L, registrar = false)
        actualizar()
        mostrarVersion()
        VersionGitHub.consultar(this) { if (!isFinishing) mostrarVersion() }
    }

    /** Encabezado con la versión y franja "Hay una versión nueva" (con la última consulta guardada). */
    private fun mostrarVersion() {
        val n = VersionGitHub.nueva(this)
        findViewById<TextView>(R.id.version).text =
            "v${Nucleo.versionApp(this)} · núcleo ${Nucleo.VERSION}" + if (n != null) " · nueva: ${n.tag}" else " ⓘ"
        findViewById<TextView>(R.id.avisoVersion).apply {
            if (n == null) { visibility = View.GONE; return }
            text = "Hay una versión nueva (${n.tag}). Tocá acá para descargar el instalador."
            visibility = View.VISIBLE
            setOnClickListener { VersionGitHub.dialogo(this@MainActivity, temaDialogo()) }
        }
    }

    /** Formato del archivo y, después, el selector de archivos de Android (sin permisos). */
    private fun ayudaImportar() {
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Importar envíos (CSV)")
            .setMessage("Elegí un archivo CSV (Google Sheets: Archivo > Descargar > CSV; también sirve Excel o Drive).\n\n" +
                "Una fila por destinatario. Las filas con el mismo título, fecha y hora forman un envío.\n\n" +
                "Columnas: titulo, fecha (dd/mm/aaaa), hora (hh:mm), numero (o nombre de un contacto), texto.\n" +
                "Opcionales: alarma (sí/no), repeticion (diaria, semanal, mensual, anual) y veces (terminar después de X veces).\n\n" +
                "Antes de importar se muestra una vista previa. Se suma y se actualiza; no se borra nada.")
            .setPositiveButton("Elegir archivo") { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values",
                        "text/plain", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                }, PEDIDO_IMPORTAR)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onActivityResult(pedido: Int, resultado: Int, datos: Intent?) {
        super.onActivityResult(pedido, resultado, datos)
        val uri = datos?.data
        if (pedido == PEDIDO_IMPORTAR && resultado == RESULT_OK && uri != null) vistaPreviaEnvios(uri) { actualizar() }
    }

    private companion object {
        const val PEDIDO_IMPORTAR = 21
    }

    override fun onRequestPermissionsResult(codigo: Int, permisos: Array<out String>, resultados: IntArray) {
        super.onRequestPermissionsResult(codigo, permisos, resultados)
        actualizar()
    }

    private fun actualizar() {
        val faltante = Permisos.faltante(this, usaAlarmas = true)
        findViewById<TextView>(R.id.avisoPermiso).apply {
            visibility = if (faltante == null) View.GONE else View.VISIBLE
            text = faltante?.first
            setOnClickListener { faltante?.second?.let { startActivity(it) } }
        }
        // Apertura automática encendida pero sin el permiso: cartel que lleva directo a darlo.
        findViewById<TextView>(R.id.avisoApertura).apply {
            val falta = AjustesApp.autoAbrir(this@MainActivity) && !Apertura.permitida(this@MainActivity)
            visibility = if (falta) View.VISIBLE else View.GONE
            text = "Para que WhatsApp se abra solo a la hora del envío, falta el permiso «Mostrar sobre otras apps». " +
                "Tocá acá para darlo (o apagá la opción en Ajustes)."
            setOnClickListener {
                try { startActivity(Apertura.intentPermiso(this@MainActivity)) }
                catch (e: Exception) { startActivity(Intent(this@MainActivity, AjustesActivity::class.java)) }
            }
        }
        val lista = findViewById<LinearLayout>(R.id.lista)
        lista.removeAllViews()
        val (programados, vencidos) = Envios.ordenados(this)
        lista.addView(subtitulo("Programados (${programados.size})"))
        if (programados.isEmpty()) lista.addView(vacio("No hay envíos programados. Tocá «Nuevo envío»."))
        programados.forEach { lista.addView(tarjeta(it), margenTarjeta()) }
        if (vencidos.isNotEmpty()) {
            lista.addView(subtitulo("Ya vencidos (${vencidos.size})"))
            vencidos.forEach { lista.addView(tarjeta(it), margenTarjeta()) }
        }
        findViewById<TextView>(R.id.tituloRegistro).text =
            if (verRegistro) "Registro de eventos ▾" else "Registro de eventos ▸"
        findViewById<TextView>(R.id.registro).apply {
            visibility = if (verRegistro) View.VISIBLE else View.GONE
            if (verRegistro) text = Registro.texto(this@MainActivity)
        }
    }

    private fun vacio(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setTextColor(getColor(RN.color.texto3))
        setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun margenTarjeta() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * resources.displayMetrics.density).toInt() }

    private fun estado(e: Envio): String = when {
        e.pospuestoHasta > 0 -> "Pospuesto hasta el ${Tiempo.completo(e.pospuestoHasta)}"
        e.activo -> (if (e.alarma) "⏰ " else "") + Tiempo.completo(e.momento) +
            if (e.regla.repite) "\n🔁 " + Recurrencia.texto(e.regla, e.momento, e.ocurrencias) else ""
        e.cerrado -> "Cerrado · era para el ${Tiempo.completo(e.momento)}"
        else -> "Vencido el ${Tiempo.completo(e.momento)}"
    }

    private fun abrirEnvio(id: Int) =
        startActivity(Intent(this, EnviarActivity::class.java).putExtra(Programador.EXTRA_ID, id))

    /** "Enviar ahora" en un envío que se repite: separa la próxima vez (queda en vencidos) y la abre para enviar. */
    private fun adelantarVez(e: Envio) {
        val sig = if (e.regla.limite in 1..e.ocurrencias + 1) null
                  else Recurrencia.siguiente(e.regla, e.momento, maxOf(System.currentTimeMillis(), e.momento))
        AlertDialog.Builder(this, temaDialogo())
            .setTitle("Enviar ahora")
            .setMessage("Este envío se repite. Se adelanta la vez del ${Tiempo.completo(e.momento)}: queda aparte, " +
                "en «Ya vencidos», para enviarla ahora, y " +
                (if (sig == null) "como era la última vez, el envío ya no se repite."
                 else "el envío sigue programado para el ${Tiempo.completo(sig)}."))
            .setPositiveButton("Enviar ahora") { _, _ ->
                val actual = Envios.obtener(this, e.id)?.takeIf { it.repite } ?: return@setPositiveButton
                abrirEnvio(separarVez(this, actual).id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Tarjeta de un envío: toque = editar; mantener presionado = borrar. */
    private fun tarjeta(e: Envio): View {
        val dp = resources.displayMetrics.density
        val nombres = Envios.nombres(this, e)
        val cabeza = e.titulo.ifBlank { nombres }
        val progreso = Envios.progreso(e)
        val linea2 = (if (e.titulo.isBlank()) "" else "Para: $nombres\n") + estado(e) +
            if (progreso.isBlank()) "" else " · $progreso"
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(RN.drawable.campo)
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            if (e.proxima == 0L && !e.paraEnviar) alpha = 0.65f
            addView(TextView(this@MainActivity).apply {
                text = cabeza
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(getColor(RN.color.texto))
            })
            addView(TextView(this@MainActivity).apply {
                text = linea2
                textSize = 14f
                setTextColor(getColor(RN.color.texto2))
            })
            addView(TextView(this@MainActivity).apply {
                text = Envios.resumenTexto(e)
                textSize = 14f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(getColor(RN.color.texto3))
                setPadding(0, (4 * dp).toInt(), 0, 0)
            })
            // Vencido con pendientes: "Enviar" (principal). Todavía programado: "Enviar ahora" (secundario).
            if (e.puedeEnviar) addView(Button(this@MainActivity, null, 0,
                if (e.paraEnviar) RN.style.Boton_Principal else RN.style.Boton).apply {
                val n = if (e.faltan.size > 1) " (${e.faltan.size} pendientes)" else ""
                text = (if (e.paraEnviar) "Enviar" else "Enviar ahora") + n
                setOnClickListener { if (e.repite) adelantarVez(e) else abrirEnvio(e.id) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * dp).toInt() })
            setOnClickListener {
                startActivity(Intent(this@MainActivity, EditarActivity::class.java).putExtra(Programador.EXTRA_ID, e.id))
            }
            setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity, temaDialogo())
                    .setTitle("Borrar envío")
                    .setMessage("¿Borrar «${Envios.titulo(this@MainActivity, e)}»?")
                    .setPositiveButton("Borrar") { _, _ -> borrarEnvio(e.id); actualizar() }
                    .setNegativeButton("Cancelar", null)
                    .show()
                true
            }
        }
    }
}
