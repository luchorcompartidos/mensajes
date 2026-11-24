# DEFINICIONES — Programador de Mensajes

> Definiciones de funciones grandes antes de programarlas. Las decisiones resumidas están en
> `DESARROLLO.md` (sección 4); acá queda el detalle para implementar.
> Estado: **confirmadas** (24-11-2026).

---

## 0. Apertura automática de WhatsApp (etapa 0.12 → Release Patch v0.0.12; núcleo 0.7.0) — **implementada**

### 0.1 Qué hace
A la hora del envío, si el celular está **desbloqueado**, se abre WhatsApp solo con el chat y el texto ya escritos (falta solo tocar "Enviar"). Con varios contactos, un chat por vez; al volver, "¿Se envió?" y sigue el próximo (como hoy).

### 0.2 Opción
- Ajustes: **"Abrir WhatsApp automáticamente" — ENCENDIDA por defecto**, se puede apagar. También cambiable en cada envío.
- Celular bloqueado, sin permiso u opción apagada → como hoy (alarma / notificación).
- La notificación sale **siempre** igual, como respaldo (si Android bloquea la apertura, la app no se entera).

### 0.3 Permiso "Mostrar sobre otras apps" (`SYSTEM_ALERT_WINDOW`)
- Se declara en el manifiesto (núcleo). **No se puede otorgar al instalar**: es acceso especial que da el usuario en Ajustes del sistema.
- La app: detecta si falta (`Settings.canDrawOverlays`); como la opción viene encendida, **avisa con un cartel en la pantalla principal** y lleva directo a la pantalla del permiso (`ACTION_MANAGE_OVERLAY_PERMISSION` con el paquete); vuelve a revisarlo al regresar. En Ajustes: "✓ concedido / ✗ falta [Conceder]". Si Android lo bloquea por app instalada a mano: *Info de la app > ⋮ > Permitir ajustes restringidos*.

### 0.4 Técnica
- Núcleo genérico: `Permisos` (consultar/pedir overlay) + al disparar el aviso pregunta a la app por `Manejador` qué abrir; si hay permiso, opción activa y no está bloqueado (`KeyguardManager`), lo abre.
- Apertura desde el receiver con `startActivity` (excepción por permiso overlay); en `PendingIntent`, opt-in `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` (al enviar y al crear: target 35).

### 0.5 Validación
- Verificar el permiso en el APK compilado.
- Botón **"Probar apertura automática"** (envío de prueba en 1 minuto) y Registro de cada intento: abierto / no abierto y motivo.
- Casos en el celular de prueba (**Motorola Moto g84 5G, Android 15**): desbloqueado en inicio; usando otra app; bloqueado; sin permiso; opción apagada; varios contactos; WhatsApp Business.

---

## A. Importar / exportar con Google Sheets (CSV)

### A.1 Fuente: solo archivo CSV (por privacidad)
- En Sheets: *Archivo > Descargar > CSV*. En la app: "Importar…" → selector de archivos de Android.
- **No** se usa link publicado (los números quedarían públicos). Sin internet, sin login. Sirve también con Excel.
- Futuro posible (no ahora): Apps Script para sincronizar. Descartado: API de Google con login.

### A.2 Contactos (etapa 0.13 → Release Patch v0.0.13) — pantalla Contactos, menú "Importar…" / "Exportar…" — **implementada**
| Columna | Obligatoria | Uso |
|---|---|---|
| `numero` | sí | Se normaliza como hoy (`numeroWhatsapp`, 549…). |
| `nombre` | no | Si falta, se muestra el número. |
| `lista` | no | Una o varias separadas por `,`; se crean las que no existan y se agrega el contacto. |
| `default` | no | "sí" = grupo Default. |
| `modo` | no | Se suma en la etapa SMS: WhatsApp / SMS / Ambos. |
- **Duplicados** (mismo número normalizado): se **actualiza el nombre** sin preguntar. Solo cambia la lista de contactos **de la app** (no la agenda de Android ni WhatsApp). Efecto: los envíos no enviados muestran y usan en `{nombre}` el nombre nuevo (los envíos guardan el contacto, no el nombre).
- **Exportar contactos a CSV**: mismas columnas; el archivo se comparte o se guarda con el selector de Android (respaldo o para armar la planilla).
- Implementado (`Csv.kt`): el número se guarda normalizado (solo dígitos, 549…); `default` solo **marca** ("sí", "x", "1", TRUE de casilla de Sheets), no desmarca; nombre vacío no borra el nombre existente; encabezados alternativos `telefono`/`celular`/`whatsapp` y `listas`. Número repetido en el archivo = error de esa fila; número en formato científico (`5,49E+12`, típico de Excel) = error con ayuda; 10 dígitos o menos = aviso "¿sin código de país?" (se importa igual). Archivo UTF-8 o Windows-1252 (Excel).
- Exportar: se guarda con el selector de Android (ahí aparece también Google Drive); UTF-8 con BOM, separador `,`, `default` = sí/no. No hay botón "Compartir" aparte (necesitaría un proveedor de archivos; a sumar si hace falta).

### A.3 Envíos (etapa 0.14 → Release Patch v0.0.14) — pantalla principal, botón "Importar" — **implementada**
| titulo | fecha | hora | numero | texto | alarma | repeticion | veces |
|---|---|---|---|---|---|---|---|
| Recordatorio turno | 02/12/2026 | 18:30 | 3514445566 | Hola {nombre}, te recuerdo el turno | sí | semanal | 4 |
- Una fila por destinatario. Filas con mismo `titulo` + `fecha` + `hora` = un envío. Textos iguales → "mismo texto"; distintos → "un texto por contacto".
- `numero` o `nombre` de un contacto existente (si hay dos con ese nombre: error, poner el número); los números nuevos se crean como contactos con el `nombre` de la fila. Los contactos que ya existen **conservan su nombre**.
- Variables iguales que en la app. Obligatorias: `fecha`, `hora`, `texto` y `numero` o `nombre`; `titulo` opcional.
- **Opcionales** (decisión 24-11): `alarma` sí/no (vacía = la de Ajustes); `repeticion` diaria / semanal / mensual / anual / no (semanal, mensual y anual toman el día de la `fecha`); `veces` = terminar después de X veces (solo si se repite). Se toman de la primera fila del envío que las tenga (aviso si otra fila dice otra cosa).
- **Ya existe** (mismo título, fecha y hora, sin cerrar): se **actualiza** (decisión 24-11): se suman los destinatarios nuevos y los pendientes toman el texto del archivo; los ya enviados no cambian. `alarma`/`repeticion` solo se cambian si vienen en el archivo y el envío sigue programado. Nunca se borra nada.
- Errores: fecha/hora inválidas, sin texto, número inválido o en formato científico, contacto inexistente, destinatario repetido en el mismo envío, fecha ya pasada (sin repetición), repetición o veces no reconocidas. Con repetición y fecha pasada: empieza en la próxima vez (aviso).
- Formatos aceptados: fecha `dd/mm/aaaa` (también `d/m/aa`, `-` o `.`, y `aaaa-mm-dd`); hora `hh:mm` (también `hh:mm:ss`, `18.30` o `6:30 p. m.`).
- Implementado en `CsvEnvios.kt` (usa `Csv` y el diálogo común `dialogoImportacion` de `Csv.kt`).

### A.4 Reglas del lector
- Primera fila = encabezados, sin importar mayúsculas, tildes ni orden; columnas desconocidas se ignoran.
- Separador `,` o `;` detectado solo; comillas con comas y saltos de línea respetadas.
- Fecha `dd/mm/aaaa`, hora `hh:mm` (24 h).
- **Vista previa antes de importar**: "N nuevos · M ya existen (se actualiza el nombre) · K con error" + lista de errores por fila → Importar / Cancelar.
- Lo importado se **copia** (no queda enlazado). Reimportar suma y actualiza, **nunca borra**.
- Futuro: columnas extra como variables por contacto (`{turno}`, `{monto}`).

---

## B. SMS (etapa 0.15) — **parte A implementada** (v0.0.15, núcleo 0.8.0); falta la parte B (v0.0.16)
Parte A: B.1, B.2 "Preguntar antes" con marca automática, B.3 en el editor (contador + detalle), B.4 permiso y datos, columna `modo` del CSV de contactos.
Parte B: B.2 "Automático" y "Reintentar" / "Abrir app de SMS"; B.3 control "se dividiría en N partes" al enviar y al guardar (automático), "Convertir caracteres", Ajustes (interruptor general + SMS de prueba).

### B.1 Canal: por contacto, como Recordatorios
- Cada contacto tiene modo: **WhatsApp / SMS / Ambos** (los existentes quedan en WhatsApp).
- **En cada envío**, cada destinatario arranca con el modo de su contacto y se puede cambiar **solo para ese envío** (solo WhatsApp / solo SMS / ambos) o quitarlo. No cambia el contacto ni otros envíos. (Recordatorios no tiene este ajuste por destinatario; alinear si se migra.)

### B.2 Modo de envío del SMS
- Por defecto **"Preguntar antes"**: notificación/alarma con botón "Enviar SMS"; al tocarlo se mandan todos los SMS del envío.
- Opcional **"Automático"**: al llegar la hora se mandan solos, sin alarma; notificación "SMS enviado a N de M".
- Android confirma si cada SMS salió → la app **marca enviado sola**. Si falla: aviso con "Reintentar" y "Abrir app de SMS".
- Variables, plantillas, listas, Default, repetición, posponer y "Enviar ahora": igual que WhatsApp.

### B.3 Largo y costo: igual que Recordatorios
Se llevan al núcleo (`Sms.kt`) las funciones de Recordatorios (`Envios.medir`, `descripcionTamanio`, `convertirGsm`, `caracteresUnicode`, `convertible`, `paraSms`, `controlarTamanioSms`, `editarSms`, `enviarSmsPrueba`), con el mismo comportamiento:
- Cálculo oficial de Android (`SmsMessage.calculateLength`).
- **En el editor**: línea corta siempre visible debajo del texto ("142 caracteres · 1 SMS"); al tocarla, o si se divide, el detalle de Recordatorios: "2 SMS · 95 caracteres · codificación Unicode (70 por SMS; 67 si se divide)" + "Fuerzan Unicode: á ó".
- **"Convertir caracteres"** (solo los que fuerzan Unicode: tildes, comillas, guiones, "…", "→"…), visible solo si se divide y la conversión cambia algo; se aplica a ese envío.
- Si se divide: "El SMS se dividiría en N partes" + destinatarios y SMS por cada uno → **Enviar igual / Editar SMS / No enviar SMS / Convertir caracteres**. Editar SMS: contador en vivo y "✓ entra en 1 SMS", solo para ese envío.
- **Modo automático**: ese control se hace **al guardar el envío** (no hay nadie para responder a la hora del envío).
- Ajustes: interruptor general de SMS y "Enviar SMS de prueba" (con confirmación de envío real y posible costo).

### B.4 Datos técnicos
- `SEND_SMS` se pide la primera vez que se usa SMS. Si Android lo bloquea (app instalada a mano): *Info de la app > Permitir ajustes restringidos*.
- `SmsManager.sendMultipartTextMessage` con `PendingIntent` de resultado (Recordatorios hoy no usa resultado: en Mensajes sí, para la marca automática).
- Datos: `Contacto` suma `modo`; `Destino` suma `canal` del envío; `Envio` suma `smsAuto`. Datos viejos = WhatsApp.
- Primera versión con la SIM predeterminada; elegir SIM, después.
- A probar: envío automático con pantalla apagada y ahorro de batería.

---

## C. Orden de etapas
1. Probar la v0.0.11 (pendiente).
2. **0.12** — Apertura automática de WhatsApp (sección 0) → Patch v0.0.12.
3. **0.13** — Importar y exportar contactos (CSV) → Patch v0.0.13 (**hecha**).
4. **0.14** — Importar envíos (CSV) (**hecha**).
5. **0.15** — SMS (núcleo `Sms.kt`, modo por contacto y por envío, largo y costo): parte A **hecha** (v0.0.15); parte B → v0.0.16.
6. Después: variables desde la planilla, elegir SIM, Apps Script.
- Migración de Recordatorios al núcleo: **por ahora no**.
