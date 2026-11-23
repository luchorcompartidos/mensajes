package com.mismensajes.app

import android.app.Application
import android.content.Context
import com.mis.nucleo.Aviso
import com.mis.nucleo.Manejador
import com.mis.nucleo.Notificador
import com.mis.nucleo.Nucleo
import org.json.JSONObject

/** Se crea antes que cualquier pantalla o receiver: deja configurado el núcleo. */
class AppMensajes : Application() {
    override fun onCreate() {
        super.onCreate()
        Nucleo.configurar(ManejadorMensajes)
        Notificador.crearCanales(this)
    }
}

/**
 * Lo que la app le resuelve al núcleo. Etapa 0.3: un único aviso de prueba (para probar alarmas,
 * notificaciones y posponer en el celular). En la etapa 0.4 pasa a leer los envíos programados.
 */
object ManejadorMensajes : Manejador {
    override fun avisos(ctx: Context): List<Aviso> = listOfNotNull(Prueba.obtener(ctx))

    override fun aviso(ctx: Context, id: Int): Aviso? = Prueba.obtener(ctx)?.takeIf { it.id == id }

    override fun alVencer(ctx: Context, a: Aviso, pospuesto: Boolean, perdido: Boolean, avisar: Boolean) {
        if (avisar) Notificador.mostrar(ctx, a, comoAlarma = a.alarma && !perdido, pospuesta = pospuesto)
        if (!pospuesto) Prueba.guardar(ctx, a.copy(activo = false))   // no se repite: queda vencido
    }

    override fun guardarPospuesto(ctx: Context, id: Int, hasta: Long) {
        aviso(ctx, id)?.let { Prueba.guardar(ctx, it.copy(pospuestoHasta = hasta)) }
    }

    override fun alHecho(ctx: Context, id: Int) {
        // "Hecho" sin posposición pendiente: la prueba terminó.
        aviso(ctx, id)?.takeIf { !it.activo && it.pospuestoHasta == 0L }?.let { Prueba.guardar(ctx, null) }
    }

    override val pantallaPrincipal = MainActivity::class.java
    override val nombreCanal = "Mensajes programados"
}

/** Aviso de prueba guardado en el celular (sobrevive a reinicios). */
object Prueba {
    const val ID = 1
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("prueba", Context.MODE_PRIVATE)

    fun obtener(ctx: Context): Aviso? {
        val o = prefs(ctx).getString("aviso", null)?.let { JSONObject(it) } ?: return null
        return Aviso(
            id = o.getInt("id"), titulo = o.optString("titulo"), texto = o.optString("texto"),
            momento = o.getLong("momento"), activo = o.optBoolean("activo", true),
            alarma = o.optBoolean("alarma", false), pospuestoHasta = o.optLong("posp", 0)
        )
    }

    fun guardar(ctx: Context, a: Aviso?) {
        val e = prefs(ctx).edit()
        if (a == null) e.remove("aviso")
        else e.putString("aviso", JSONObject()
            .put("id", a.id).put("titulo", a.titulo).put("texto", a.texto).put("momento", a.momento)
            .put("activo", a.activo).put("alarma", a.alarma).put("posp", a.pospuestoHasta).toString())
        e.commit()
    }
}
