package com.mismensajes.app

import android.app.Application
import android.content.Context
import com.mis.nucleo.Aviso
import com.mis.nucleo.Manejador
import com.mis.nucleo.Notificador
import com.mis.nucleo.Nucleo
import com.mis.nucleo.Programador

/** Se crea antes que cualquier pantalla o receiver: deja configurado el núcleo. */
class AppMensajes : Application() {
    override fun onCreate() {
        super.onCreate()
        Nucleo.configurar(ManejadorMensajes)
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
        id = e.id, titulo = Envios.titulo(ctx, e), texto = Envios.resumenTexto(e), momento = e.momento,
        activo = e.activo, alarma = e.alarma, pospuestoHasta = e.pospuestoHasta
    )

    override fun avisos(ctx: Context): List<Aviso> =
        Envios.todos(ctx).filter { !it.cerrado }.map { comoAviso(ctx, it) }

    override fun aviso(ctx: Context, id: Int): Aviso? =
        Envios.obtener(ctx, id)?.takeIf { !it.cerrado }?.let { comoAviso(ctx, it) }

    override fun alVencer(ctx: Context, a: Aviso, pospuesto: Boolean, perdido: Boolean, avisar: Boolean) {
        if (avisar) Notificador.mostrar(ctx, a, comoAlarma = a.alarma && !perdido, pospuesta = pospuesto)
        // Un envío no se repite: al vencer su fecha queda vencido (la posposición sigue aparte).
        if (!pospuesto) Envios.obtener(ctx, a.id)?.let { Envios.guardar(ctx, it.copy(activo = false)) }
    }

    override fun guardarPospuesto(ctx: Context, id: Int, hasta: Long) {
        Envios.obtener(ctx, id)?.let { Envios.guardar(ctx, it.copy(pospuestoHasta = hasta)) }
    }

    override fun alHecho(ctx: Context, id: Int) {
        // "Hecho" después de vencer y sin posposición pendiente: el envío se cierra.
        Envios.obtener(ctx, id)?.takeIf { !it.activo && it.pospuestoHasta == 0L }
            ?.let { Envios.guardar(ctx, it.copy(cerrado = true)) }
    }

    override val pantallaPrincipal = MainActivity::class.java
    override val pantallaPosponer = PosponerActivity::class.java
    override val pantallaAlarma = AlarmaActivity::class.java
    override val nombreCanal = "Mensajes programados"
}
