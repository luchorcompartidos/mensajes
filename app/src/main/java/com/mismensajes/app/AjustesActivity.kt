package com.mismensajes.app

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.mis.nucleo.R as RN

/**
 * Ajustes de la app. Por ahora: con qué WhatsApp se abren los envíos (común, Business o preguntar cada vez).
 * Cada envío puede cambiarlo desde su pantalla de envío.
 */
class AjustesActivity : Activity() {
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

        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(caja) })
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
