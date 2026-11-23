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
| Última versión (Release) | **v0.0.1** (23-11-2026) |
| Se conecta con | WhatsApp (por intent, sin API) · a futuro Google Sheets |

## 2. Etapa actual
**Etapa 0.3 — Núcleo de alarmas** (próxima a empezar; la 0.2 quedó cerrada con la v0.0.1)
- Etapa 0.2 (cerrada): app vacía que compila en Actions y publica la Release v0.0.1 con el APK.
- Hecho hasta ahora: módulos `:app` (`com.mismensajes.app`, pantalla inicial con versión de app y de núcleo) y `:nucleo` (`com.mis.nucleo`, con el tema/paleta "Agenda" de Recordatorios); `compilar.yml` adaptado (primer patch = v0.0.1, dos copias del APK); ícono provisorio (globo de mensaje con reloj, fondo verde).
- Compilación OK y Release **v0.0.1** publicada, firmada con la clave fija (mismos Secrets que Recordatorios); el link fijo redirige al APK de la última Release.
- En curso: —

## 3. Próximos pasos
1. **Etapa 0.3 — Núcleo de alarmas (en `:nucleo`):** `Programador`, `Notificador`, `AlarmaReceiver`, `AccionReceiver`, `ArranqueReceiver` y `Posposicion` (de `Avisos.kt` / `Modelo.kt` de Recordatorios), genéricos (sin saber si es recordatorio o mensaje).
2. **Etapa 0.4 — ABM de mensajes:** crear / editar / borrar un envío (texto, uno o más contactos, fecha/hora, modo de texto); lista ordenada por próxima fecha.
3. **Etapa 0.5 — Envío:** notificación al vencer → abre WhatsApp con el texto precargado por contacto (base: `EnviarWhatsappActivity` y `Contactos`/`Agenda` de `Contactos.kt`, que pasan al núcleo), marcando enviados uno por uno.
4. **Etapa posterior — Núcleo a su repo:** mover `nucleo/` a `luchorcompartidos/nucleo-android` (submódulo, `submodules: true` en Actions) y, en etapa aparte, migrar Recordatorios a ese núcleo.

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
- 2026-11-23 Link de Mi Panel: cada Release sube además una copia de nombre fijo → `https://github.com/luchorcompartidos/mensajes/releases/latest/download/programador-mensajes.apk`.

## 5. Pendientes / dudas abiertas
- [ ] Ícono definitivo (hoy uno provisorio).
- [ ] Cargar el link fijo en la planilla de Mi Panel.
- [ ] ¿Repetición en v1? ¿Variables tipo `{nombre}` en el texto?
- [ ] Futuro: SMS automático (`SEND_SMS`, base en `SuspensionSms` / `enviarSmsPrueba`), plantillas, listas, importar desde Google Sheets.

## 6. Datos técnicos clave
- Archivos principales: `app/src/main/java/com/mismensajes/app/MainActivity.kt`; `nucleo/src/main/java/com/mis/nucleo/Nucleo.kt` (+ `nucleo/src/main/res`: tema, paleta, botones); `.github/workflows/compilar.yml`. Base a traer: `recordatorios/app/src/main/java/com/misrecordatorios/app/` → `Avisos.kt`, `Modelo.kt`, `Contactos.kt`, `Version.kt`.
- Requisitos heredados: minSdk 34 (Android 14), compileSdk/targetSdk 35; permisos `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`, `READ_CONTACTS` (y `SEND_SMS` solo cuando llegue el SMS automático).
- Configuración / servicios: Secrets `KEYSTORE_B64` y `KEYSTORE_PASS` cargados (los mismos de Recordatorios; alias `recordatorios`, o el Secret `KEYSTORE_ALIAS` si se cambia la clave). Respaldo: `secretos-github.txt` del chat "Recordatorios PWA vs app nativa" (14-11-2026).
- Cómo se publica: push a `main` → GitHub Actions (`compilar.yml`) compila el APK y lo adjunta a la Release (`programador-mensajes-X.Y.Z.apk` + `programador-mensajes.apk`).
- Núcleo: versión propia (`Nucleo.VERSION`, hoy 0.1.0), independiente de la de cada app; `useAndroidX=false` (sin librerías externas).
- Releases: según `ESTANDAR-VERSIONES.md` del repo home (`[major]` / `[minor]` / `[patch]` / `[skip release]` en el commit; cambios que no tocan la app van sin release). Como Recordatorios: solo Changelog y aviso con link al instalador.

## 7. Historial (resumido)
- 2026-11-23: Repo creado y DESARROLLO.md inicial (el primer push falló por falta de acceso; se rehízo y subió en la sesión siguiente, con las definiciones de la etapa 0.2). Sin release.
- 2026-11-23: Etapa 0.2: esqueleto `:app` + `:nucleo` y workflow → Release **v0.0.1** (patch).

---
*Al cerrar cada sesión: actualizar secciones 1, 2, 3 y 7, y subir el archivo al repo.*
