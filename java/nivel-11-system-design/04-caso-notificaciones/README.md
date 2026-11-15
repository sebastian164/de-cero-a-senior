# 04 — Caso: sistema de notificaciones

Un problema dominado por la asincronía, los proveedores externos poco fiables y las garantías de entrega.

## 1. Requisitos

**Funcionales**

- Otros servicios piden enviar una notificación a un usuario.
- Canales: correo, SMS y notificación *push*.
- Plantillas con variables y varios idiomas.
- Preferencias del usuario: qué canales acepta, horario de silencio, baja de las promocionales.
- Programación para una hora futura.
- Consultar el estado: en cola, enviada, entregada, fallida.

**No funcionales**

- Una notificación aceptada no se pierde.
- Evitar duplicados: un usuario no debe recibir dos veces el mismo código.
- Las transaccionales (código de verificación) llegan en segundos; las promocionales pueden tardar.
- Tolerar la caída de un proveedor externo.
- Picos: una campaña a un millón de usuarios.

## 2. Estimación

- 10 millones de notificaciones al día: unas 115 por segundo en promedio.
- Una campaña de un millón en diez minutos: unas 1.700 por segundo.
- Los proveedores imponen límites de tasa: el SMS, por ejemplo, 100 por segundo.

El pico supera la capacidad de los proveedores. Conclusión inmediata: hace falta **una cola que absorba** y **límite de tasa hacia cada proveedor**.

## 3. API

```
POST /v1/notificaciones
{
  "idempotencia": "pedido-8812-confirmado",
  "usuarioId": "u-42",
  "tipo": "PEDIDO_CONFIRMADO",
  "prioridad": "ALTA",
  "datos": { "numero": "8812" },
  "programadaPara": null
}
→ 202 Accepted { "id": "n-9f3a", "estado": "EN_COLA" }

GET /v1/notificaciones/{id} → { "estado": "ENTREGADA", "canal": "PUSH", "intentos": 1 }
```

Se responde `202`: la petición fue aceptada, no ejecutada. La clave de idempotencia la pone quien llama.

## 4. Datos

```
notificaciones( id, clave_idempotencia UNIQUE, usuario_id, tipo, canal, estado,
                intentos, creada_en, programada_para, enviada_en )
preferencias( usuario_id, canal, habilitado, silencio_desde, silencio_hasta )
plantillas( tipo, canal, idioma, version, asunto, cuerpo )
```

## 5. Alto nivel

```
Servicios emisores
      │  POST (202)
      ▼
 API de notificaciones ──► base (registro + outbox)
      │
      ▼
   Colas por prioridad y canal
   ├── correo  ──► trabajadores ──► proveedor de correo
   ├── sms     ──► trabajadores ──► proveedor de SMS
   └── push    ──► trabajadores ──► servicios push
                      │
                      ├── reintentos con retroceso
                      └── cola de mensajes fallidos

 Proveedores ──► webhooks de estado ──► actualización del estado
```

Flujo: validar → comprobar la idempotencia → aplicar preferencias → resolver plantilla → guardar y encolar (Outbox) → el trabajador envía → se actualiza el estado.

## 6. Profundización

**No perder notificaciones.** Guardar el registro y el mensaje de cola en la misma transacción (Outbox). El trabajador confirma el mensaje solo después de que el proveedor aceptó.

**No duplicar.** Entrega "al menos una vez" implica reintentos. Defensas: clave de idempotencia con restricción única en la entrada; el trabajador comprueba el estado antes de enviar; y, cuando el proveedor la admite, se le pasa una clave de idempotencia. El caso residual (el proveedor envió y la confirmación se perdió) se acepta o se mitiga según el canal.

**Prioridades.** Colas separadas: un código de verificación no espera detrás de un millón de promociones. Trabajadores dedicados a la cola de alta prioridad.

**Proveedores poco fiables.** Tiempo límite, reintentos con retroceso exponencial, cortacircuitos por proveedor y **proveedor de respaldo**. Un adaptador por proveedor detrás de una interfaz común (`CanalDeEnvio`): patrones Adapter y Strategy.

**Límite de tasa.** Un limitador por proveedor (cubeta de fichas) para no superar sus cuotas. La cola es el amortiguador.

**Programadas.** Se guardan con su hora; un planificador las encola al vencer. Con varias instancias se usa `SKIP LOCKED` para que solo una tome cada lote.

**Preferencias y horario de silencio.** Se evalúan al enviar, no al aceptar: el usuario pudo cambiarlas entretanto.

**Observabilidad.** Tasa de entrega por canal y proveedor, tiempo desde la petición hasta el envío, tamaño de las colas, mensajes fallidos.

## Compromisos tomados

| Decisión | Se gana | Se cede |
|---|---|---|
| Respuesta `202` asíncrona | Absorbe picos; el emisor no espera | El emisor no conoce el resultado al instante |
| Al menos una vez | No se pierden | Riesgo residual de duplicado |
| Colas por canal y prioridad | Aislamiento | Más infraestructura |
| Proveedor de respaldo | Disponibilidad | Costo e integración doble |

## Taller

1. Implementa el núcleo en Java: la API, el modelo, una cola (en memoria o real) y trabajadores por canal con proveedores simulados que fallan al azar.
2. Implementa idempotencia, reintentos con retroceso y cola de mensajes fallidos.
3. Simula una campaña de 100.000 notificaciones y demuestra que las de alta prioridad no se retrasan.
4. Simula la caída de un proveedor y la conmutación al de respaldo.
5. Mata un trabajador a mitad de envío y demuestra que no se pierde nada. Cuenta los duplicados.
6. Escribe el documento de diseño completo.

Rama `taller/n11-04-notificaciones`.

## Preguntas senior

1. ¿Por qué `202` y no `200`?
2. ¿Cómo garantizas que no se pierdan notificaciones si el servicio cae?
3. ¿Se pueden eliminar del todo los duplicados? ¿Por qué?
4. ¿Cómo evitas que una campaña retrase los códigos de verificación?
5. ¿Qué haces cuando un proveedor está lento pero no caído?
