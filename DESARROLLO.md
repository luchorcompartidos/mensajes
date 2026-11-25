# DESARROLLO — Programador de Mensajes

> Archivo para retomar el trabajo en un chat nuevo.
> Uso: *"Seguimos con Mensajes, etapa [X.Y]. Leé `DESARROLLO.md` del repo luchorcompartidos/mensajes."*
> Mantenerlo corto (idealmente menos de 1 página). Lo viejo va resumido en "Historial".

---

## 1. Ficha rápida
| Campo | Valor |
|---|---|
| App | Programador de Mensajes |
| Repo | luchorcompartidos/mensajes |
| URL publicada | APK: https://github.com/luchorcompartidos/mensajes/releases/latest/download/programador-mensajes.apk |
| Tipo | Android APK (Kotlin nativo, compilado en GitHub Actions) |
| Estado | En desarrollo |
| Última versión (Release) | **v0.0.17** (24-11-2026) |
| Se conecta con | WhatsApp (por intent, sin API) · SMS (SIM predeterminada) · núcleo `luchorcompartidos/nucleo-android` (submódulo `nucleo/`) · a futuro Google Sheets |

## 2. Etapa actual
**Etapa 0.16 — Variables desde la planilla: cerrada** → Release **v0.0.17** (patch). Núcleo **0.9.0** (`Sms.enCamino`, `olvidar` y `enviarPrueba` pasaron de `SmsApp` al núcleo; DESARROLLO.md del núcleo al día). Detalle en `DEFINICIONES.md` A.5.
- **Columnas extra de los CSV = variables propias** ("Monto a pagar" → `{monto_a_pagar}`): las del CSV de contactos quedan en el contacto (cualquier envío); las del CSV de envíos, en ese destinatario de ese envío, y pisan a las del contacto. Celda vacía no borra. Exportar contactos suma esas columnas.
- **Sin valor:** se envía vacío, con aviso en la vista previa de la importación, en la edición del envío y en la pantalla de envío.
- **Ficha del contacto:** "Datos para variables" (editar y "Agregar dato…"). "Insertar variable" lista también las propias; la vista previa de plantillas las muestra como «monto».
- **A probar en el celular:** v0.0.17 (importar contactos con columnas extra y reimportar con celdas vacías; exportar; importar envíos con `{turno}` que pise al del contacto; variable sin valor —avisos y texto enviado—; ficha del contacto con "Agregar dato…"; Insertar variable; SMS con variables y control de tamaño; SMS de prueba y "SMS en camino", que ahora usan el núcleo) + lo pendiente de la v0.0.5 a v0.0.16.
- En curso: —. Tag **v0.9.0** del núcleo creado por el usuario.

## 3. Próximos pasos
1. **Probar la v0.0.17** (variables) y la v0.0.16 (SMS), con lo de la v0.0.5 a v0.0.15, y corregir lo que aparezca.
2. Siguiente funcionalidad a definir (`DEFINICIONES.md` C.7: elegir SIM, Apps Script; o exportar envíos a CSV).
3. Migrar Recordatorios al núcleo: por ahora no (decisión 24-11). A definir: grupo Default como lista.

## 4. Decisiones tomadas
- 2026-11-23 App separada de Recordatorios (no pantalla nueva dentro de ella): escalabilidad y funciones futuras propias de mensajería.
- 2026-11-23 Kotlin nativo compilado en GitHub Actions, igual que Recordatorios, sin Android Studio.
- 2026-11-23 Sin permisos de accesibilidad: alarma exacta → notificación → abre WhatsApp con el texto precargado; el usuario toca "Enviar".
- 2026-11-23 Se toma de `luchorcompartidos/recordatorios` el núcleo de alarmas, notificaciones y posponer.
- 2026-11-23 Dejar lugar a futuro para: SMS automático, plantillas, listas de contactos e importar desde Google Sheets.
- 2026-11-23 Nombre visible: **Programador de Mensajes**.
- 2026-11-23 Numeración: la primera versión de la app es **v0.0.1** (SemVer de tres números).
- 2026-11-23 Posponer: inicialmente las mismas opciones que Recordatorios.
- 2026-11-23 Texto: opción seleccionable **en cada envío**: "mismo texto para todos" o "un texto por contacto".
- 2026-11-23 Paquete (applicationId): **`com.mismensajes.app`**.
- 2026-11-23 Núcleo compartido con Recordatorios, **opción 2 por etapas**: primero módulo `:nucleo` dentro de este repo; cuando funcione, repo propio `nucleo-android` como submódulo de las dos apps; Recordatorios se migra al final, en etapa aparte (no se toca hasta que el núcleo esté probado).
- 2026-11-23 Núcleo genérico: no conoce recordatorios ni mensajes; la app responde por `Manejador`. Conserva ids de canales, claves de Ajustes ("ajustes") y Registro ("registro") de Recordatorios para que su migración no pierda configuración.
- 2026-11-23 Contactos como Recordatorios: no se lee ni se lista la agenda; el número se escribe a mano y "Validar en WA" busca solo ese número (nombre + si tiene WhatsApp). Lista propia reutilizable en cualquier envío.
- 2026-11-23 Pantallas "Posponer…" y alarma a pantalla completa: se sumaron en la 0.4 (en `:app`, `Pantallas.kt`).
- 2026-11-23 Los ids de envíos nunca se reutilizan (contador "ultimo_id"), para no cruzar alarmas o notificaciones viejas.
- 2026-11-23 Envío: la app no sabe si WhatsApp envió el mensaje; al volver se pregunta "¿Se envió?" y recién ahí se marca. Con todos enviados, el envío se cierra solo (igual que "Hecho").
- 2026-11-23 La apertura de WhatsApp se escribió en esta app (`WhatsApp.abrir`, link `api.whatsapp.com/send`) sin copiar `EnviarWhatsappActivity` de Recordatorios (en esa sesión no se pudo abrir ese repo); alinear al pasar el núcleo a su repo.
- 2026-11-23 WhatsApp común/Business: se elige en Ajustes (común, Business o preguntar cada vez) y se puede cambiar en cada envío.
- 2026-11-23 Grupo Default como Recordatorios (clave "def"): preselecciona contactos en envíos nuevos.
- 2026-11-23 "Enviar ahora" no cancela la programación; solo "Hecho"/cerrar envío la cancela.
- 2026-11-23 Parte A / Parte B de la 0.6 en Releases separadas, para no encarecer la sesión.
- 2026-11-23 Variables: `{nombre}`, `{primer_nombre}`, `{saludo}`, `{dia}`, `{fecha}`, `{hora}`; fecha/hora/día/saludo son los del momento en que se abre WhatsApp (la vista previa usa la hora programada).
- 2026-11-23 Posponer rápido y alarma por defecto se guardan en el núcleo (claves de Recordatorios), para compartirlos al migrar.
- 2026-11-23 `Version.kt` de Recordatorios va al núcleo (`VersionGitHub`, repo configurable por app), no a `:app`: así Recordatorios lo reutiliza al migrar. Sin Changelog interno: se usa el de las Releases.
- 2026-11-23 Link de Mi Panel: cada Release sube además una copia de nombre fijo → `https://github.com/luchorcompartidos/mensajes/releases/latest/download/programador-mensajes.apk`.
- 2026-11-23 Plantillas: el texto se **copia** al envío (no queda enlazado); editar o borrar una plantilla no cambia envíos ya creados. Parte A (ABM) y parte B (usar en envíos) en Releases separadas.
- 2026-11-23 Núcleo en su repo `nucleo-android`, como submódulo `nucleo/`. Cambios del núcleo se hacen en ese repo y acá se actualiza el submódulo (ver README del núcleo). Mover el núcleo no genera Release (el APK no cambia).
- 2026-11-24 Listas de contactos: se gestionan en la pantalla de Contactos (no pantalla aparte); guardan ids de contactos (prefs "listas"); al usarlas, los contactos se **copian** al envío (cambiar la lista no cambia envíos ya creados). El grupo Default sigue aparte, como en Recordatorios. Nombre obligatorio y sin repetir.
- 2026-11-24 Repetición: la regla y el cálculo van al núcleo (`Regla`/`Recurrencia`, mismos códigos y claves que Recordatorios). Cada vez se **separa** en un envío aparte (`origen` = id del que se repite), como los "cumplidos" de Recordatorios: así cada vez tiene sus propias marcas de enviado, su notificación y su posposición. Si el celular estuvo apagado y se perdieron varias veces, se separa una sola y se sigue desde la próxima futura.
- 2026-11-24 Google Sheets: **solo archivo CSV** (sin link publicado, por privacidad). Orden: contactos (importar + exportar) → envíos → SMS. Duplicados por número: se actualiza el nombre solo en la app (no agenda ni WhatsApp). Detalle en `DEFINICIONES.md`.
- 2026-11-24 SMS: modo **por contacto** (WhatsApp/SMS/Ambos, como Recordatorios), ajustable por destinatario **solo para cada envío**; por defecto **"Preguntar antes"** (automático opcional, con control de tamaño al guardar); largo y costo **igual que Recordatorios** (funciones llevadas al núcleo `Sms.kt`) + contador corto en el editor.
- 2026-11-24 Migración de Recordatorios al núcleo: por ahora no.
- 2026-11-24 CSV de contactos en `:app` (no en el núcleo): Recordatorios no lo tiene. Importar = selector de Android (`ACTION_OPEN_DOCUMENT`, sin permisos); exportar = `ACTION_CREATE_DOCUMENT`. `default` solo marca; nombre vacío no pisa el existente.
- 2026-11-24 Apertura automática de WhatsApp (permiso "Mostrar sobre otras apps", que da el usuario: no se puede otorgar al instalar): **encendida por defecto**, se apaga en Ajustes o por envío; mecanismo en el núcleo, qué abrir en la app; notificación siempre como respaldo. Va **antes de Sheets**. Celular de prueba: Moto g84 5G, Android 15.
- 2026-11-24 Apertura automática implementada: la pantalla de envío es la que se abre (no WhatsApp directo), así sigue el "¿Se envió?"; en modo automático encadena los contactos tras "Sí, enviado". También al sonar una posposición. La prueba sigue la opción de Ajustes (para probar también "apagada").
- 2026-11-24 Importar envíos (CSV): columnas opcionales `alarma` (sí/no), `repeticion` simple (diaria/semanal/mensual/anual, con el día de la fecha) y `veces`; si el envío ya existe (mismo título, fecha y hora, sin cerrar) se **actualiza** (suma destinatarios, actualiza textos pendientes). Botón "Importar" en la principal; código en `CsvEnvios.kt`.
- 2026-11-24 SMS parte A: modo del contacto `ModoEnvio` (códigos W/S/A de Recordatorios, sin "Desactivado"); el destino guarda `canal` y `smsEnviado` aparte de `enviado` (WhatsApp); "pendientes" = WhatsApp, `pendientesSms`, y el envío se cierra cuando a nadie le falta nada (`faltan`). El SMS usa el mismo texto que WhatsApp (variables reemplazadas, "→" como "->"). Botón "Enviar SMS" de la notificación: con permiso manda directo (receiver); sin permiso abre la pantalla, que lo pide. Con WhatsApp y SMS pendientes a la vez, la notificación tiene Enviar / Enviar SMS / Posponer rápido ("Posponer…" queda en la alarma y al tocar la notificación). El contador del editor se muestra solo si algún destinatario va por SMS.
- 2026-11-24 SMS parte B: el modo (Preguntar antes / Automático) es **por envío** (`Envio.smsAuto`, por defecto Preguntar antes). La decisión del control de tamaño queda en el destino (`smsOk` = enviar igual, `smsTexto` = editado/convertido, con las variables ya reemplazadas; se borra al editar el envío y se conserva en cada vez de un envío que se repite). Automático solo a la hora o en una posposición, no con lo perdido con el celular apagado (ahí avisa con "Enviar SMS"). "En camino" = el estado que guarda el núcleo (`sms_envios`), para no mandar dos veces. Interruptor general en `:app` (`AjustesApp`); el SMS de prueba y "en camino" pasaron al núcleo en la 0.9.0 (`Sms.enviarPrueba`, `Sms.enCamino`); el aviso del resultado de la prueba sigue en la app.
- 2026-11-24 Variables desde la planilla: de **los dos CSV** (contacto → `Contacto.datos`; envío → `Destino.datos`, que pisa al del contacto); celda vacía no borra; **sin valor = vacío con aviso**; editables en la ficha del contacto (no en el envío); nombres reservados (`Variables.RESERVADAS` y variables de la app) no se usan. Va en Release **Patch** (v0.0.17).
- 2026-11-24 `Sms.enCamino`, `olvidar` y `enviarPrueba` pasan al núcleo (0.9.0). El tag del núcleo lo crea el usuario (el push de tags desde la sesión de Claude se corta); v0.9.0 → `8c36887`, creado.
- 2026-11-23 "Usar plantilla" / "Guardar como plantilla" trabajan sobre el último campo de texto tocado (igual que "Insertar variable"); "Agregar" va al final, en renglón nuevo; no se guardan dos plantillas con el mismo texto.

## 5. Pendientes / dudas abiertas
- [ ] Probar en el celular de la v0.0.5 a la v0.0.17 (ver sección 2).
- [ ] ¿Exportar envíos a CSV (respaldo / para editar en la planilla)? No pedido todavía.
- [ ] ¿Sumar "Compartir" al exportar (necesita un proveedor de archivos propio, sin AndroidX)? Hoy se guarda con el selector (incluye Drive).
- [ ] Futuro: elegir SIM para los SMS (hoy, la predeterminada).
- [ ] ¿Editar en la app los valores propios de un envío (hoy solo reimportando)?
- [ ] ¿Pasar `Contactos`/`Agenda` y las pantallas al núcleo cuando se migre Recordatorios?
- [ ] ¿Pasar el grupo Default a ser una lista más?

## 6. Datos técnicos clave
- Definiciones de funciones grandes: `DEFINICIONES.md`. Archivos principales (`app/src/main/java/com/mismensajes/app/`): `AppMensajes.kt` (Application + `ManejadorMensajes`), `Datos.kt` (`Envio` —con `regla`, `ocurrencias`, `origen`, `autoAbrir`—, `Destino` —con `datos`, JSON "dv"—, `Envios`, `Contacto` —con `datos`, JSON "datos"—, `Contactos`, `Agenda`), `MainActivity.kt` (lista), `EditarActivity.kt` (alta/edición/borrado), `ContactosActivity.kt` (contactos + diálogos), `Pantallas.kt` (`PosponerActivity`, `AlarmaActivity`, `elegirHora`), `Enviar.kt` (`WhatsApp`, `AjustesApp` —prefs "mensajes_ajustes", incluye "auto_abrir", "sms_activo", "sms_prueba"—, `cerrarEnvio`, `separarVez`, `EnviarActivity`), `AjustesActivity.kt` (WhatsApp + apertura automática + SMS —interruptor y prueba— + posponer rápido + alarma por defecto), `Variables.kt` (variables del texto: de la app + propias; `datos`, `propias`, `faltantes`, `RESERVADAS`), `Plantillas.kt` (`Plantilla`, `Plantillas`, `PlantillasActivity`, `editarPlantilla`, `elegirPlantilla`, `insertarVariable`), `Listas.kt` (`Lista`, `Listas` —prefs "listas"—, `editarLista`, `usarLista`, `guardarComoLista`), `Csv.kt` (`Csv`: leer/escribir CSV, `columnasVariables`/`valoresVariables`; `CsvContactos`: analizar/importar/exportar; `leerCsv`, `dialogoImportacion`, `vistaPreviaImportacion`), `CsvEnvios.kt` (`CsvEnvios`: analizar/importar envíos; `vistaPreviaEnvios`), `EnviarSms.kt` (`SmsApp`: enviarTodos/enviarUno/automatico, resultado, avisos de falla —Reintentar / Abrir app de SMS—, "SMS enviado a N de M" y aviso de la prueba, `enCamino` (usa el núcleo), `porMandar`, `faltan`; `Activity.controlarSms`; `SmsAccionReceiver`: Enviar SMS y Reintentar); núcleo (submódulo `nucleo/`, repo `nucleo-android`) en `nucleo/src/main/java/com/mis/nucleo/`: `Nucleo.kt` (configurar, `Aviso`, `Manejador`), `Programador.kt` (alarmas + `AlarmaReceiver`/`AccionReceiver`/`ArranqueReceiver`), `Notificador.kt`, `Posposicion.kt` (+ `Ajustes`, `Tiempo`, `Registro`, `Permisos`), `Version.kt` (`VersionGitHub`: Releases, Changelog, aviso de versión nueva), `Repeticion.kt` (`Repeticion`, `Regla`, `Recurrencia`), `Apertura.kt` (`Apertura`: permiso de superposición, bloqueo, apertura al vencer), `Sms.kt` (`Sms`: medir, descripción, convertir, enviar con resultado, `enCamino`, `olvidar`, `enviarPrueba` + `SmsResultadoReceiver`; `controlarTamanioSms`, `editarSms`); `nucleo/src/main/res` (tema, paleta, botones, `ic_notif`); `.github/workflows/compilar.yml`.
- Requisitos heredados: minSdk 34 (Android 14), compileSdk/targetSdk 35. Permisos de alarmas (`POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`, `SYSTEM_ALERT_WINDOW`) y receivers declarados en el manifiesto de `:nucleo`; `INTERNET` (Releases de GitHub) también en `:nucleo`; `READ_CONTACTS` (solo para validar) y `SEND_SMS` en el manifiesto de `:app`.
- Configuración / servicios: Secrets `KEYSTORE_B64` y `KEYSTORE_PASS` cargados (los mismos de Recordatorios; alias `recordatorios`, o el Secret `KEYSTORE_ALIAS` si se cambia la clave). Respaldo: `secretos-github.txt` del chat "Recordatorios PWA vs app nativa" (14-11-2026).
- Cómo se publica: push a `main` → GitHub Actions (`compilar.yml`, checkout con submódulos) compila el APK y lo adjunta a la Release (`programador-mensajes-X.Y.Z.apk` + `programador-mensajes.apk`).
- Núcleo: versión propia (`Nucleo.VERSION`, hoy 0.9.0), independiente de la de cada app; `useAndroidX=false` (sin librerías externas). Para tocarlo: cambiar en `nucleo-android`, luego `cd nucleo && git pull origin main`, y commitear el submódulo acá.
- Releases: según `ESTANDAR-VERSIONES.md` del repo home (`[major]` / `[minor]` / `[patch]` / `[skip release]` en el commit; cambios que no tocan la app van sin release). Como Recordatorios: solo Changelog y aviso con link al instalador.

## 7. Historial (resumido)
- 2026-11-23: Repo creado y DESARROLLO.md inicial (el primer push falló por falta de acceso; se rehízo y subió en la sesión siguiente, con las definiciones de la etapa 0.2). Sin release.
- 2026-11-23: Etapa 0.2: esqueleto `:app` + `:nucleo` y workflow → Release **v0.0.1** (patch).
- 2026-11-23: Etapa 0.3: núcleo de alarmas genérico (Programador, Notificador, receivers, Posposicion, Ajustes) + pantalla de prueba → Release **v0.0.2** (patch).
- 2026-11-23: Etapa 0.4: ABM de envíos, contactos propios con validación en WA y pantallas Posponer/alarma (se descarta el aviso de prueba) → Release **v0.0.3** (patch).
- 2026-11-23: Etapa 0.5: envío por WhatsApp contacto por contacto (botón Enviar en notificación, alarma y lista; marca de enviado y cierre automático); núcleo 0.3.0 → Release **v0.0.4** (patch). A probar: botón Enviar en notificación común y en alarma (que calle el sonido), desbloqueo desde la alarma, "¿Se envió?", cierre automático, "un texto por contacto", números 549….
- 2026-11-23: Etapa 0.6 parte A: Enviar ahora, grupo Default, Ajustes con WhatsApp común/Business (+ cambio por envío), progreso en la notificación, ícono definitivo, revisión de código; núcleo 0.4.0 → Release **v0.0.5** (patch).
- 2026-11-23: Etapa 0.6 parte B: Ajustes con posponer rápido y alarma por defecto; variables en el texto con botón "Insertar variable" y vista previa → Release **v0.0.6** (patch). Etapa 0.6 cerrada.
- 2026-11-23: Etapa 0.7: `VersionGitHub` en el núcleo (0.5.0), aviso de versión nueva en la pantalla principal, versión tocable y "Acerca de" en Ajustes con Changelog e instalador → Release **v0.0.7** (patch).
- 2026-11-23: Etapa 0.8 parte A: plantillas de texto reutilizables (lista + alta/edición/borrado, con variables y vista previa) y botón "Plantillas" en la principal → Release **v0.0.8** (patch).
- 2026-11-23: Etapa 0.8 parte B: "Usar plantilla" (reemplazar, agregar o en todos los contactos) y "Guardar como plantilla" en la edición del envío → Release **v0.0.9** (patch). Etapa 0.8 cerrada.
- 2026-11-23: Etapa 0.9: núcleo movido a `luchorcompartidos/nucleo-android` (submódulo `nucleo/`, `submodules: true` en Actions) → sin Release (compilación de prueba, OK).
- 2026-11-24: Etapa 0.10: listas de contactos (ABM en Contactos, "Usar lista" / "Guardar como lista" en el envío) → Release **v0.0.10** (patch).
- 2026-11-24: Etapa 0.11: repetición de envíos (regla en el núcleo 0.6.0, cada vez separada en un envío aparte, "Enviar ahora" adelanta la vez) → Release **v0.0.11** (patch).
- 2026-11-24: Definiciones de Google Sheets (CSV) y SMS en `DEFINICIONES.md`, propuestas y confirmadas; definida la apertura automática de WhatsApp (etapa 0.12) → sin Release (solo documentación).
- 2026-11-24: Etapa 0.12: apertura automática de WhatsApp (núcleo 0.7.0 `Apertura`, opción en Ajustes y por envío, cartel de permiso, prueba en 1 minuto, Registro) → Release **v0.0.12** (patch).
- 2026-11-24: Etapa 0.13: importar contactos desde CSV con vista previa y exportar a CSV (`Csv.kt`, menú ⋮ de Contactos) → Release **v0.0.13** (patch).
- 2026-11-24: Etapa 0.14: importar envíos desde CSV (botón "Importar" en la principal, `CsvEnvios.kt`, alarma/repetición/veces, actualiza los existentes) → Release **v0.0.14** (patch).
- 2026-11-24: Etapa 0.15 parte A: SMS "Preguntar antes" (modo por contacto y canal por envío, columna `modo` del CSV, contador en el editor, "Enviar SMS" con marca automática); núcleo 0.8.0 `Sms.kt` → Release **v0.0.15** (patch).
- 2026-11-24: Etapa 0.15 parte B: SMS automático por envío (control al guardar), control "se dividiría en N partes" al enviar, Reintentar / Abrir app de SMS, Ajustes con interruptor general y SMS de prueba → Release **v0.0.16** (patch). Etapa 0.15 cerrada. Tag v0.8.0 del núcleo creado por el usuario.
- 2026-11-24: Núcleo 0.9.0 (`Sms.enCamino`, `olvidar`, `enviarPrueba`) y su DESARROLLO.md al día. Etapa 0.16: variables desde la planilla (columnas extra de los CSV, ficha del contacto, avisos sin valor) → Release **v0.0.17** (patch). Tag v0.9.0 del núcleo creado por el usuario.

---
*Al cerrar cada sesión: actualizar secciones 1, 2, 3 y 7, y subir el archivo al repo.*
