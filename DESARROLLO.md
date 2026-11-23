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
| URL publicada | — (APK en Releases) |
| Tipo | Android APK (Kotlin nativo, compilado en GitHub Actions) |
| Estado | Idea |
| Última versión (Release) | — (primera prevista: **v0.0.1**) |
| Se conecta con | WhatsApp (por intent, sin API) · a futuro Google Sheets |

## 2. Etapa actual
**Etapa 0.2 — Esqueleto que compila** (a la espera de definir el enfoque del núcleo compartido, ver Pendientes)
- Objetivo de la etapa: app vacía que compila en Actions y publica la Release v0.0.1 con el APK.
- Hecho hasta ahora: definiciones de nombre, posponer, modo de texto y versión inicial.
- En curso: —

## 3. Próximos pasos
1. **Etapa 0.2 — Esqueleto que compila:** copiar de `recordatorios` la base Gradle (`settings/build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `proguard-rules.pro`) y `.github/workflows/compilar.yml`; namespace/applicationId propio; pantalla vacía; Release **v0.0.1** con APK (nombre con versión + copia de nombre fijo para el link de Mi Panel).
2. **Etapa 0.3 — Núcleo de alarmas:** `Programador`, `Notificador`, `AlarmaReceiver`, `AccionReceiver`, `ArranqueReceiver` y `Posposicion` (de `Avisos.kt` / `Modelo.kt` de Recordatorios), en forma genérica según el enfoque de núcleo que se elija.
3. **Etapa 0.4 — ABM de mensajes:** crear / editar / borrar un envío (texto, uno o más contactos, fecha/hora, modo de texto); lista ordenada por próxima fecha.
4. **Etapa 0.5 — Envío:** notificación al vencer → abre WhatsApp con el texto precargado por contacto (base: `EnviarWhatsappActivity` y `Contactos`/`Agenda` de `Contactos.kt`), marcando enviados uno por uno.

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

## 5. Pendientes / dudas abiertas
- [ ] Ícono de la app.
- [ ] Paquete (applicationId): propuesto `com.mismensajes.app` (distinto de `com.misrecordatorios.app`, así se pueden instalar las dos). No se puede cambiar después sin reinstalar y perder los datos.
- [ ] Núcleo compartido con Recordatorios: copiar y evolucionar por separado, o módulo común en un repo aparte (git submodule) usado por las dos apps.
- [ ] Link al APK desde Mi Panel: propuesto subir, además del APK con versión, una copia de nombre fijo → `https://github.com/luchorcompartidos/mensajes/releases/latest/download/programador-mensajes.apk`.
- [ ] ¿Repetición en v1? ¿Variables tipo `{nombre}` en el texto?
- [ ] Futuro: SMS automático (`SEND_SMS`, base en `SuspensionSms` / `enviarSmsPrueba`), plantillas, listas, importar desde Google Sheets.

## 6. Datos técnicos clave
- Archivos principales: (aún no hay código). Base a copiar: `recordatorios/app/src/main/java/com/misrecordatorios/app/` → `Avisos.kt`, `Modelo.kt`, `Contactos.kt`, `Version.kt`.
- Requisitos heredados: minSdk 34 (Android 14), compileSdk/targetSdk 35; permisos `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`, `READ_CONTACTS` (y `SEND_SMS` solo cuando llegue el SMS automático).
- Configuración / servicios: ninguno por ahora.
- Cómo se publica: push a `main` → GitHub Actions (`compilar.yml`) compila el APK y lo adjunta a la Release.
- Releases: según `ESTANDAR-VERSIONES.md` del repo home (`[major]` / `[minor]` / `[patch]` / `[skip release]` en el commit; cambios que no tocan la app van sin release). Como Recordatorios: solo Changelog y aviso con link al instalador.

## 7. Historial (resumido)
- 2026-11-23: Repo creado y DESARROLLO.md inicial (el primer push falló por falta de acceso; se rehízo y subió en la sesión siguiente, con las definiciones de la etapa 0.2). Sin release.

---
*Al cerrar cada sesión: actualizar secciones 1, 2, 3 y 7, y subir el archivo al repo.*
