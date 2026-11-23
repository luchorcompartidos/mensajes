package com.mismensajes.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.mis.nucleo.Aviso
import com.mis.nucleo.Notificador
import com.mis.nucleo.Nucleo
import com.mis.nucleo.Permisos
import com.mis.nucleo.Posposicion
import com.mis.nucleo.Ajustes
import com.mis.nucleo.Programador
import com.mis.nucleo.Registro
import com.mis.nucleo.Tiempo

/** Etapa 0.3: pantalla inicial con pruebas del núcleo de alarmas (aviso común y alarma en 1 minuto). */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.version).text =
            "v${Nucleo.versionApp(this)} · núcleo ${Nucleo.VERSION}"
        findViewById<Button>(R.id.btnAviso).setOnClickListener { probar(alarma = false) }
        findViewById<Button>(R.id.btnAlarma).setOnClickListener { probar(alarma = true) }
        findViewById<Button>(R.id.btnCancelar).setOnClickListener {
            Programador.quitar(this, Prueba.ID)
            Prueba.guardar(this, null)
            Registro.anotar(this, "#${Prueba.ID} prueba cancelada")
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
    }

    override fun onRequestPermissionsResult(codigo: Int, permisos: Array<out String>, resultados: IntArray) {
        super.onRequestPermissionsResult(codigo, permisos, resultados)
        actualizar()
    }

    private fun probar(alarma: Boolean) {
        Programador.quitar(this, Prueba.ID)
        val a = Aviso(
            id = Prueba.ID,
            titulo = if (alarma) "Prueba de alarma" else "Prueba de aviso",
            texto = "Prueba del núcleo de alarmas (etapa 0.3)",
            momento = System.currentTimeMillis() + 60_000,
            alarma = alarma
        )
        Prueba.guardar(this, a)
        Programador.programar(this, a)
        actualizar()
    }

    private fun actualizar() {
        val faltante = Permisos.faltante(this, usaAlarmas = true)
        findViewById<TextView>(R.id.avisoPermiso).apply {
            visibility = if (faltante == null) View.GONE else View.VISIBLE
            text = faltante?.first
            setOnClickListener { faltante?.second?.let { startActivity(it) } }
        }
        val a = Prueba.obtener(this)
        findViewById<TextView>(R.id.estado).text = when {
            a == null -> "Sin prueba programada. Botón rápido de la notificación: " +
                Posposicion.textoBoton(Ajustes.posponerRapido(this)) + "."
            a.pospuestoHasta > 0 -> "«${a.titulo}» pospuesto hasta ${Tiempo.completo(a.pospuestoHasta)}."
            a.activo -> "«${a.titulo}» programado para ${Tiempo.completo(a.momento)}."
            else -> "«${a.titulo}» ya sonó (${Tiempo.completo(a.momento)}). Tocá «Hecho» en la notificación para cerrarlo."
        }
        findViewById<TextView>(R.id.registro).text = Registro.texto(this)
    }
}
