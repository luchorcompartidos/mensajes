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
| Última versión (Release) | **v0.0.2** (23-11-2026) |
| Se conecta con | WhatsApp (por intent, sin API) · a futuro Google Sheets |

## 2. Etapa actual
**Etapa 0.4 — ABM de mensajes** (próxima a empezar; la 0.3 quedó cerrada con la v0.0.2)
- Etapa 0.3 (cerrada): núcleo de alarmas genérico en `:nucleo` (núcleo 0.2.0) + pantalla de prueba → Release **v0.0.2**.
- Cómo funciona el núcleo: trabaja con `Aviso` (id, título, texto, momento, activo, alarma, pospuestoHasta, previo) y le pregunta todo a la app por la interfaz `Manejador` (`avisos`, `aviso`, `alVencer`, `guardarPospuesto`, `alHecho`, pantallas, acciones extra). La app lo configura en `AppMensajes.onCreate()` con `Nucleo.configurar(...)`.
- Hoy `ManejadorMensajes` maneja un único aviso de prueba (`Prueba`): botones "Probar aviso / alarma en 1 minuto", "Cancelar prueba" y registro de eventos en pantalla.
- **A probar en el celular:** aviso común, alarma, "Posponer 10 minutos", "Callar", "Hecho" y que sobreviva a un reinicio.
- En curso: —

## 3. Próximos pasos
1. **Etapa 0.4 — ABM de mensajes:** crear / editar / borrar un envío (texto, uno o más contactos, fecha/hora, modo de texto); lista ordenada por próxima fecha. `ManejadorMensajes` pasa a leer los envíos guardados (reemplaza a `Prueba`).
2. **Etapa 0.5 — Envío:** notificación al vencer → abre WhatsApp con el texto precargado por contacto (base: `EnviarWhatsappActivity` y `Contactos`/`Agenda` de `Contactos.kt`, que pasan al núcleo), marcando enviados uno por uno. El botón "Enviar" entra por `Manejador.accionesExtra`.
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
- 2026-11-23 Link de Mi Panel: cada Release sube además una copia de nombre fijo → `https://github.com/luchorcompartidos/mensajes/releases/latest/download/programador-mensajes.apk`.

## 5. Pendientes / dudas abiertas
- [ ] Ícono definitivo (hoy uno provisorio).
- [ ] Probar en el celular la v0.0.2 (ver sección 2).
- [ ] Pantallas "Posponer…" y alarma a pantalla completa: el núcleo ya las admite (`pantallaPosponer` / `pantallaAlarma`), falta traerlas de `Pantallas.kt` de Recordatorios (¿en la 0.4 o 0.5?).
- [ ] Cargar el link fijo en la planilla de Mi Panel.
- [ ] ¿Repetición en v1? ¿Variables tipo `{nombre}` en el texto?
- [ ] Futuro: SMS automático (`SEND_SMS`, base en `SuspensionSms` / `enviarSmsPrueba`), plantillas, listas, importar desde Google Sheets.

## 6. Datos técnicos clave
- Archivos principales: `app/.../MainActivity.kt`, `app/.../AppMensajes.kt` (Application + `ManejadorMensajes` + `Prueba`); núcleo en `nucleo/src/main/java/com/mis/nucleo/`: `Nucleo.kt` (configurar, `Aviso`, `Manejador`), `Programador.kt` (alarmas + `AlarmaReceiver`/`AccionReceiver`/`ArranqueReceiver`), `Notificador.kt`, `Posposicion.kt` (+ `Ajustes`, `Tiempo`, `Registro`, `Permisos`); `nucleo/src/main/res` (tema, paleta, botones, `ic_notif`); `.github/workflows/compilar.yml`. Base pendiente de traer de Recordatorios: `Contactos.kt`, `Version.kt`, `PosponerActivity`/`AlarmaActivity` (`Pantallas.kt`).
- Requisitos heredados: minSdk 34 (Android 14), compileSdk/targetSdk 35. Permisos de alarmas (`POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`) y receivers declarados en el manifiesto de `:nucleo`; faltan `READ_CONTACTS` (0.5) y `SEND_SMS` (solo con SMS automático).
- Configuración / servicios: Secrets `KEYSTORE_B64` y `KEYSTORE_PASS` cargados (los mismos de Recordatorios; alias `recordatorios`, o el Secret `KEYSTORE_ALIAS` si se cambia la clave). Respaldo: `secretos-github.txt` del chat "Recordatorios PWA vs app nativa" (14-11-2026).
- Cómo se publica: push a `main` → GitHub Actions (`compilar.yml`) compila el APK y lo adjunta a la Release (`programador-mensajes-X.Y.Z.apk` + `programador-mensajes.apk`).
- Núcleo: versión propia (`Nucleo.VERSION`, hoy 0.2.0), independiente de la de cada app; `useAndroidX=false` (sin librerías externas).
- Releases: según `ESTANDAR-VERSIONES.md` del repo home (`[major]` / `[minor]` / `[patch]` / `[skip release]` en el commit; cambios que no tocan la app van sin release). Como Recordatorios: solo Changelog y aviso con link al instalador.

## 7. Historial (resumido)
- 2026-11-23: Repo creado y DESARROLLO.md inicial (el primer push falló por falta de acceso; se rehízo y subió en la sesión siguiente, con las definiciones de la etapa 0.2). Sin release.
- 2026-11-23: Etapa 0.2: esqueleto `:app` + `:nucleo` y workflow → Release **v0.0.1** (patch).
- 2026-11-23: Etapa 0.3: núcleo de alarmas genérico (Programador, Notificador, receivers, Posposicion, Ajustes) + pantalla de prueba → Release **v0.0.2** (patch).

---
*Al cerrar cada sesión: actualizar secciones 1, 2, 3 y 7, y subir el archivo al repo.*
