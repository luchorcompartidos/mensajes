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
| Última versión (Release) | **v0.0.6** (23-11-2026) |
| Se conecta con | WhatsApp (por intent, sin API) · a futuro Google Sheets |

## 2. Etapa actual
**Etapa 0.6 — cerrada** (parte A → v0.0.5, parte B → **v0.0.6**). Próxima etapa: a definir (ver sección 3).
- Parte B (cerrada) → Release **v0.0.6**:
  - **Ajustes → Avisos:** botón de **posponer rápido** (lista completa + "Personalizado…"; las notificaciones en la bandeja se redibujan con el botón nuevo, salvo una alarma sonando) y casilla **"Envíos nuevos: sonar como alarma"** (lo que viene marcado al crear un envío). Se guardan en el núcleo (`Ajustes`, prefs "ajustes", mismas claves que Recordatorios): el núcleo no cambió (sigue 0.4.0).
  - **Variables en el texto** (`Variables.kt`): `{nombre}`, `{primer_nombre}`, `{saludo}` (Buen día / Buenas tardes / Buenas noches), `{dia}`, `{fecha}` (dd/mm/aaaa), `{hora}`. Valen en "mismo texto" y en "un texto por contacto"; no distinguen mayúsculas ni tildes (`{Día}` = `{dia}`). Se reemplazan al abrir WhatsApp, con la fecha/hora de ese momento; las llaves desconocidas quedan tal cual; si `{nombre}` queda vacío se acomodan los espacios.
  - **Pantalla de edición:** botón "+ Insertar variable" (inserta en el cursor del último campo de texto tocado) y **vista previa** para el primer contacto, a la hora programada, con aviso de variables no reconocidas.
  - **Pantalla de envío y notificación:** cada contacto muestra su texto ya reemplazado; con un solo contacto y mismo texto, la notificación también.
- **A probar en el celular:** lo pendiente de la 0.4 y la 0.5 (ver Historial) + parte A (v0.0.5) + parte B (v0.0.6).
- En curso: —

## 3. Próximos pasos
1. **Probar la v0.0.6** (incluye lo de la v0.0.5) en el celular y corregir lo que aparezca.
2. **Elegir la próxima etapa** (propuestas): traer `Version.kt` de Recordatorios (Changelog + aviso de versión nueva) · plantillas de texto reutilizables (ya con variables).
3. **Etapa posterior — Núcleo a su repo:** mover `nucleo/` a `luchorcompartidos/nucleo-android` (submódulo, `submodules: true` en Actions) y, en etapa aparte, migrar Recordatorios a ese núcleo.

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
- 2026-11-23 Link de Mi Panel: cada Release sube además una copia de nombre fijo → `https://github.com/luchorcompartidos/mensajes/releases/latest/download/programador-mensajes.apk`.

## 5. Pendientes / dudas abiertas
- [ ] Probar en el celular la v0.0.6 (incluye lo de la v0.0.5; ver sección 2).
- [ ] ¿Más variables a futuro (ej. de una planilla de Google Sheets)?
- [ ] ¿Pasar `Contactos`/`Agenda` y las pantallas al núcleo cuando se migre Recordatorios?
- [ ] Cargar el link fijo en la planilla de Mi Panel.
- [ ] ¿Repetición en v1?
- [ ] Futuro: SMS automático (`SEND_SMS`, base en `SuspensionSms` / `enviarSmsPrueba`), plantillas, listas, importar desde Google Sheets.

## 6. Datos técnicos clave
- Archivos principales (`app/src/main/java/com/mismensajes/app/`): `AppMensajes.kt` (Application + `ManejadorMensajes`), `Datos.kt` (`Envio`, `Envios`, `Contacto`, `Contactos`, `Agenda`), `MainActivity.kt` (lista), `EditarActivity.kt` (alta/edición/borrado), `ContactosActivity.kt` (contactos + diálogos), `Pantallas.kt` (`PosponerActivity`, `AlarmaActivity`, `elegirHora`), `Enviar.kt` (`WhatsApp`, `AjustesApp` —prefs "mensajes_ajustes"—, `cerrarEnvio`, `EnviarActivity`), `AjustesActivity.kt` (WhatsApp + posponer rápido + alarma por defecto), `Variables.kt` (variables del texto); núcleo en `nucleo/src/main/java/com/mis/nucleo/`: `Nucleo.kt` (configurar, `Aviso`, `Manejador`), `Programador.kt` (alarmas + `AlarmaReceiver`/`AccionReceiver`/`ArranqueReceiver`), `Notificador.kt`, `Posposicion.kt` (+ `Ajustes`, `Tiempo`, `Registro`, `Permisos`); `nucleo/src/main/res` (tema, paleta, botones, `ic_notif`); `.github/workflows/compilar.yml`. Pendiente de traer de Recordatorios: `Version.kt` (Changelog / aviso de versión nueva).
- Requisitos heredados: minSdk 34 (Android 14), compileSdk/targetSdk 35. Permisos de alarmas (`POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`) y receivers declarados en el manifiesto de `:nucleo`; `READ_CONTACTS` en el manifiesto de `:app` (solo para validar); falta `SEND_SMS` (solo con SMS automático).
- Configuración / servicios: Secrets `KEYSTORE_B64` y `KEYSTORE_PASS` cargados (los mismos de Recordatorios; alias `recordatorios`, o el Secret `KEYSTORE_ALIAS` si se cambia la clave). Respaldo: `secretos-github.txt` del chat "Recordatorios PWA vs app nativa" (14-11-2026).
- Cómo se publica: push a `main` → GitHub Actions (`compilar.yml`) compila el APK y lo adjunta a la Release (`programador-mensajes-X.Y.Z.apk` + `programador-mensajes.apk`).
- Núcleo: versión propia (`Nucleo.VERSION`, hoy 0.4.0: suma `Notificador.actualizar`, redibujar sin sonar), independiente de la de cada app; `useAndroidX=false` (sin librerías externas).
- Releases: según `ESTANDAR-VERSIONES.md` del repo home (`[major]` / `[minor]` / `[patch]` / `[skip release]` en el commit; cambios que no tocan la app van sin release). Como Recordatorios: solo Changelog y aviso con link al instalador.

## 7. Historial (resumido)
- 2026-11-23: Repo creado y DESARROLLO.md inicial (el primer push falló por falta de acceso; se rehízo y subió en la sesión siguiente, con las definiciones de la etapa 0.2). Sin release.
- 2026-11-23: Etapa 0.2: esqueleto `:app` + `:nucleo` y workflow → Release **v0.0.1** (patch).
- 2026-11-23: Etapa 0.3: núcleo de alarmas genérico (Programador, Notificador, receivers, Posposicion, Ajustes) + pantalla de prueba → Release **v0.0.2** (patch).
- 2026-11-23: Etapa 0.4: ABM de envíos, contactos propios con validación en WA y pantallas Posponer/alarma (se descarta el aviso de prueba) → Release **v0.0.3** (patch).
- 2026-11-23: Etapa 0.5: envío por WhatsApp contacto por contacto (botón Enviar en notificación, alarma y lista; marca de enviado y cierre automático); núcleo 0.3.0 → Release **v0.0.4** (patch). A probar: botón Enviar en notificación común y en alarma (que calle el sonido), desbloqueo desde la alarma, "¿Se envió?", cierre automático, "un texto por contacto", números 549….
- 2026-11-23: Etapa 0.6 parte A: Enviar ahora, grupo Default, Ajustes con WhatsApp común/Business (+ cambio por envío), progreso en la notificación, ícono definitivo, revisión de código; núcleo 0.4.0 → Release **v0.0.5** (patch).
- 2026-11-23: Etapa 0.6 parte B: Ajustes con posponer rápido y alarma por defecto; variables en el texto con botón "Insertar variable" y vista previa → Release **v0.0.6** (patch). Etapa 0.6 cerrada.

---
*Al cerrar cada sesión: actualizar secciones 1, 2, 3 y 7, y subir el archivo al repo.*
