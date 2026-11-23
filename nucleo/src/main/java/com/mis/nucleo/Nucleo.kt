package com.mis.nucleo

import android.content.Context

/**
 * Punto de entrada del núcleo compartido entre apps (hoy: Programador de Mensajes;
 * más adelante también Recordatorios). En la etapa 0.3 se suman acá las alarmas,
 * notificaciones, posponer y la reprogramación al reiniciar el teléfono.
 */
object Nucleo {
    /** Versión del núcleo (independiente de la versión de cada app). */
    const val VERSION = "0.1.0"

    /** Versión de la app que usa el núcleo (versionName del APK). */
    fun versionApp(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
}
