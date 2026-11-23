package com.mismensajes.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.mis.nucleo.Nucleo

/** Etapa 0.2: pantalla inicial vacía que prueba la compilación y el uso del núcleo. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.version).text =
            "v${Nucleo.versionApp(this)} · núcleo ${Nucleo.VERSION}"
    }
}
