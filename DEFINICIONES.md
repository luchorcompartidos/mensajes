# DEFINICIONES — Programador de Mensajes

> Propuestas de funciones grandes antes de programarlas. Cuando una se confirma, la decisión pasa a
> `DESARROLLO.md` (sección 4) y acá queda solo como referencia.
> Estado: **propuesta, a confirmar** (24-11-2026).

---

## A. Importar desde Google Sheets

### A.1 Objetivo
Cargar de una vez muchos contactos (y más adelante envíos) desde una planilla, en lugar de escribirlos de a uno.

### A.2 Cómo se lee la planilla (opciones)
| Opción | Cómo funciona | A favor | En contra |
|---|---|---|---|
| **1. Archivo CSV** | En Sheets: *Archivo > Descargar > CSV*; en la app: "Importar…" y se elige el archivo (selector de Android). | Privado, sin internet, sin configurar nada. Sirve también con Excel. | Hay que descargar el archivo cada vez. |
| **2. Link CSV publicado** | En Sheets: *Archivo > Compartir > Publicar en la web > CSV* (como Mi Panel); el link se pega una vez en Ajustes y la app lo lee con "Importar desde la planilla". | Cómodo: se edita la planilla y se vuelve a importar. Usa `INTERNET`, que ya está. | **Los números quedan públicos** para cualquiera que tenga el link. |
| 3. Apps Script | Un script en la planilla devuelve los datos (con una clave en el link) y podría recibir marcas de "enviado". | Privado y permite escribir de vuelta en la planilla. | Hay que instalar el script en cada planilla; más trabajo. |
| 4. API de Google con login | Login con la cuenta de Google y lectura directa. | Lo más "integrado". | Proyecto en Google Cloud, firma SHA-1, librerías externas (hoy el proyecto no usa ninguna). No conviene. |

**Propuesta:** opciones 1 y 2 con el mismo lector de CSV (el link es opcional, con aviso de que queda público). La 3 queda para el futuro, si se quiere sincronizar.

### A.3 Qué se importa (por etapas)
1. **Contactos** (primera etapa), en la pantalla de Contactos, menú "Importar…".
   - Columnas: `nombre`, `numero` (obligatoria) y, opcional, `lista` (una o varias separadas por `,`: se crean las listas que no existan y se agregan los contactos).
   - Columna opcional `default` (sí/no): lo marca en el grupo Default.
2. **Envíos** (etapa posterior), en la pantalla principal, "Importar envíos".
   - Una fila por contacto: `fecha`, `hora`, `numero` (o `nombre` de un contacto existente), `texto`, `titulo` (opcional).
   - Las filas con el mismo `titulo` + `fecha` + `hora` se juntan en un solo envío; si los textos son distintos queda en modo "un texto por contacto".
   - Los contactos que no existan se crean.
3. **Variables desde la planilla** (más adelante): cada columna extra del contacto (ej. `deuda`, `turno`) se guarda con el contacto y se usa como `{deuda}`, `{turno}` en el texto. Responde la duda abierta "¿Más variables a futuro?".

### A.4 Reglas del lector
- Primera fila = encabezados. Se aceptan con o sin mayúsculas/tildes (`Número` = `numero`), en cualquier orden; las columnas que no se conocen se ignoran (o se ofrecen como variables, etapa 3).
- Separador `,` o `;` (Excel en Argentina usa `;`), detectado solo. Texto entre comillas con saltos de línea permitido.
- Números: se normalizan igual que hoy (`numeroWhatsapp`, formato 549…); filas sin número válido se informan como error.
- Fecha `dd/mm/aaaa` y hora `hh:mm` (24 h).
- **Antes de importar se muestra un resumen**: "N nuevos, M ya existen, K con error" (con la lista de errores) y se confirma.
- Duplicados: se reconoce el contacto por número normalizado. Propuesta: actualizar el nombre si en la planilla viene distinto (o preguntar una sola vez "actualizar / saltear").
- Igual que plantillas y listas: lo importado se **copia**; no queda enlazado a la planilla. Volver a importar suma y actualiza, nunca borra.
- Extra opcional: "Exportar contactos a CSV" (respaldo o para pasar a la planilla).

---

## B. SMS automático

### B.1 Objetivo
Que un envío pueda salir **por SMS sin tocar nada**: al llegar la hora, la app manda el SMS sola. Por WhatsApp eso no es posible sin accesibilidad; por SMS sí.

### B.2 Comportamiento propuesto
- **Canal por envío:** en la edición, "Enviar por: WhatsApp / SMS". (Más adelante, opcional: "SMS a los que no tienen WhatsApp".)
- **Modo SMS:**
  - *Automático:* al llegar la hora se envía a todos; no suena alarma. Queda una notificación "SMS enviado a 3 de 3".
  - *Preguntar antes:* notificación (o alarma) con botón "Enviar SMS", como hoy con WhatsApp; sirve para revisar antes.
- **Marca de enviado:** a diferencia de WhatsApp, acá la app **sí sabe** si salió (confirmación de Android), así que marca cada contacto sola. Si falla (sin señal, sin saldo, modo avión) avisa "No se pudo enviar a X" con "Reintentar" y "Abrir app de SMS".
- Variables, plantillas, listas, repetición y posponer funcionan igual que con WhatsApp.
- **Interruptor general en Ajustes:** "Permitir SMS automático" (si está apagado, los envíos SMS pasan a "preguntar antes"). Más "Enviar SMS de prueba" a un número.
- **Largo y costo:** contador en el editor ("2 SMS"). Ojo: con **á, í, ó, ú** (o emojis) cada SMS baja de 160 a 70 caracteres; la ñ y la é no afectan. Opción "Quitar tildes en SMS" para que salga más barato.
- **Doble SIM:** primera versión con la SIM predeterminada para SMS; elegir SIM 1/SIM 2 queda para después (necesita un permiso más).

### B.3 Datos técnicos
- Permiso `SEND_SMS` (se pide en el momento de usar SMS por primera vez). La app no está en Play Store, así que no aplica la restricción de Play para SMS. Si Android lo bloquea en una app instalada a mano, se habilita en *Info de la app > Permitir ajustes restringidos*.
- Envío con `SmsManager.sendMultipartTextMessage` y `PendingIntent` de resultado → marca de enviado o aviso de error.
- Base en Recordatorios (`SuspensionSms` / `enviarSmsPrueba`). **Propuesta:** el envío de SMS va al **núcleo** (`Sms.kt`, genérico), así Recordatorios lo reutiliza al migrar, como se hizo con la repetición.
- `Envio` suma `canal` ("wa" / "sms") y `smsAuto` (sí/no); los envíos viejos quedan como WhatsApp.

---

## C. Orden propuesto de etapas
1. Probar la v0.0.11 (pendiente).
2. **0.12 — Importar contactos** desde CSV (archivo + link opcional).
3. **0.13 — SMS** (núcleo `Sms.kt` + canal por envío + Ajustes).
4. **0.14 — Importar envíos** desde CSV.
5. Después: variables desde la planilla, Apps Script, elegir SIM.
- La migración de Recordatorios al núcleo puede ir antes o después de esto (si va después, ya se lleva también `Sms.kt`).

## D. A confirmar
- [ ] Sheets: ¿opciones 1 y 2 (archivo + link publicado) o solo archivo por privacidad?
- [ ] Sheets: ¿empezar por contactos (+ listas) y dejar envíos para después?
- [ ] Sheets: duplicados → ¿actualizar nombre o preguntar?
- [ ] SMS: ¿modo por defecto automático o "preguntar antes"?
- [ ] SMS: ¿canal por envío alcanza, o también "SMS a los que no tienen WhatsApp"?
- [ ] SMS: ¿"Quitar tildes" activado por defecto?
- [ ] ¿Qué va primero: Sheets, SMS o la migración de Recordatorios?
