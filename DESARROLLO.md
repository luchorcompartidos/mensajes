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
| Última versión (Release) | **v0.0.3** (23-11-2026) |
| Se conecta con | WhatsApp (por intent, sin API) · a futuro Google Sheets |

## 2. Etapa actual
**Etapa 0.5 — Envío por WhatsApp** (próxima a empezar; la 0.4 quedó cerrada con la v0.0.3)
- Etapa 0.4 (cerrada): ABM de envíos + contactos propios + pantallas "Posponer…" y alarma → Release **v0.0.3**.
- Cómo quedó: `Envio` (título opcional, modo de texto, texto común, `Destino`s con texto propio y marca `enviado`, momento, alarma, activo, cerrado, pospuestoHasta) guardado en preferencias "envios". `ManejadorMensajes` convierte cada envío en `Aviso` (mismo id). Al vencer queda "vencido"; con "Hecho" pasa a "cerrado" (sale del núcleo). Editar cancela la posposición y reprograma.
- Contactos: lista propia (preferencias "contactos", mismas claves que Recordatorios), carga manual del número + "Validar en WA y recuperar" (busca solo ese número en la agenda; `READ_CONTACTS`). Borrar un contacto lo quita de los envíos.
- **A probar en el celular:** crear / editar / borrar (también manteniendo presionada la tarjeta), modo "un texto por contacto", validar número, aviso común y alarma a pantalla completa (Callar, Hecho, Posponer rápido, Posponer…, Editar), que sobreviva a un reinicio.
- En curso: —

## 3. Próximos pasos
1. **Etapa 0.5 — Envío:** al vencer, botón "Enviar" (por `Manejador.accionesExtra` y en la pantalla de alarma) → abre WhatsApp con el texto precargado contacto por contacto (base: `EnviarWhatsappActivity` y `Envios.abrirWhatsapp` de Recordatorios), marcando `Destino.enviado` uno por uno y mostrando el avance en la lista.
2. **Etapa posterior — Núcleo a su repo:** mover `nucleo/` a `luchorcompartidos/nucleo-android` (submódulo, `submodules: true` en Actions) y, en etapa aparte, migrar Recordatorios a ese núcleo.

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
- 2026-11-23 Link de Mi Panel: cada Release sube además una copia de nombre fijo → `https://github.com/luchorcompartidos/mensajes/releases/latest/download/programador-mensajes.apk`.

## 5. Pendientes / dudas abiertas
- [ ] Ícono definitivo (hoy uno provisorio).
- [ ] Probar en el celular la v0.0.3 (ver sección 2).
- [ ] ¿Grupo "Default" de contactos como en Recordatorios? ¿Pantalla de Ajustes (posponer rápido, alarma por defecto)?
- [ ] ¿Pasar `Contactos`/`Agenda` y las pantallas al núcleo cuando se migre Recordatorios?
- [ ] Cargar el link fijo en la planilla de Mi Panel.
- [ ] ¿Repetición en v1? ¿Variables tipo `{nombre}` en el texto?
- [ ] Futuro: SMS automático (`SEND_SMS`, base en `SuspensionSms` / `enviarSmsPrueba`), plantillas, listas, importar desde Google Sheets.

## 6. Datos técnicos clave
- Archivos principales (`app/src/main/java/com/mismensajes/app/`): `AppMensajes.kt` (Application + `ManejadorMensajes`), `Datos.kt` (`Envio`, `Envios`, `Contacto`, `Contactos`, `Agenda`), `MainActivity.kt` (lista), `EditarActivity.kt` (alta/edición/borrado), `ContactosActivity.kt` (contactos + diálogos), `Pantallas.kt` (`PosponerActivity`, `AlarmaActivity`, `elegirHora`); núcleo en `nucleo/src/main/java/com/mis/nucleo/`: `Nucleo.kt` (configurar, `Aviso`, `Manejador`), `Programador.kt` (alarmas + `AlarmaReceiver`/`AccionReceiver`/`ArranqueReceiver`), `Notificador.kt`, `Posposicion.kt` (+ `Ajustes`, `Tiempo`, `Registro`, `Permisos`); `nucleo/src/main/res` (tema, paleta, botones, `ic_notif`); `.github/workflows/compilar.yml`. Base pendiente de traer de Recordatorios: `EnviarWhatsappActivity`/`abrirWhatsapp` (`Contactos.kt`), `Version.kt`.
- Requisitos heredados: minSdk 34 (Android 14), compileSdk/targetSdk 35. Permisos de alarmas (`POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`) y receivers declarados en el manifiesto de `:nucleo`; `READ_CONTACTS` en el manifiesto de `:app` (solo para validar); falta `SEND_SMS` (solo con SMS automático).
- Configuración / servicios: Secrets `KEYSTORE_B64` y `KEYSTORE_PASS` cargados (los mismos de Recordatorios; alias `recordatorios`, o el Secret `KEYSTORE_ALIAS` si se cambia la clave). Respaldo: `secretos-github.txt` del chat "Recordatorios PWA vs app nativa" (14-11-2026).
- Cómo se publica: push a `main` → GitHub Actions (`compilar.yml`) compila el APK y lo adjunta a la Release (`programador-mensajes-X.Y.Z.apk` + `programador-mensajes.apk`).
- Núcleo: versión propia (`Nucleo.VERSION`, hoy 0.2.0), independiente de la de cada app; `useAndroidX=false` (sin librerías externas).
- Releases: según `ESTANDAR-VERSIONES.md` del repo home (`[major]` / `[minor]` / `[patch]` / `[skip release]` en el commit; cambios que no tocan la app van sin release). Como Recordatorios: solo Changelog y aviso con link al instalador.

## 7. Historial (resumido)
- 2026-11-23: Repo creado y DESARROLLO.md inicial (el primer push falló por falta de acceso; se rehízo y subió en la sesión siguiente, con las definiciones de la etapa 0.2). Sin release.
- 2026-11-23: Etapa 0.2: esqueleto `:app` + `:nucleo` y workflow → Release **v0.0.1** (patch).
- 2026-11-23: Etapa 0.3: núcleo de alarmas genérico (Programador, Notificador, receivers, Posposicion, Ajustes) + pantalla de prueba → Release **v0.0.2** (patch).
- 2026-11-23: Etapa 0.4: ABM de envíos, contactos propios con validación en WA y pantallas Posponer/alarma (se descarta el aviso de prueba) → Release **v0.0.3** (patch).

---
*Al cerrar cada sesión: actualizar secciones 1, 2, 3 y 7, y subir el archivo al repo.*
