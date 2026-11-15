# 05 — Caso: venta de boletas con alta concurrencia

Un problema dominado por la contención: muchos usuarios compitiendo por pocos recursos, donde vender de más es inaceptable.

## 1. Requisitos

**Funcionales**

- Ver eventos y disponibilidad de localidades.
- Seleccionar asientos; quedan reservados temporalmente (10 minutos) mientras se paga.
- Pagar con una pasarela externa. Si se paga, la reserva se confirma; si no, los asientos se liberan.
- Emitir la boleta.

**No funcionales**

- **Nunca vender el mismo asiento dos veces.** Consistencia fuerte en la reserva.
- Pico extremo al abrir la venta: cientos de miles de personas en el mismo minuto.
- La disponibilidad mostrada puede estar unos segundos desactualizada.
- Trato justo: quien llega primero, compra primero.

## 2. Estimación

- Evento de 50.000 asientos. Al abrir la venta: 500.000 usuarios en cinco minutos.
- Consultas de disponibilidad: decenas de miles por segundo.
- Intentos de reserva: miles por segundo, pero como máximo 50.000 tendrán éxito.

La mayoría de las peticiones van a fallar por diseño. El sistema debe **rechazar de forma barata y rápida**.

## 3. API

```
GET  /v1/eventos/{id}/disponibilidad          → mapa de asientos (puede venir de caché)
POST /v1/reservas   { eventoId, asientos[] }  → 201 { reservaId, expiraEn } | 409 no disponibles
POST /v1/reservas/{id}/pago   (Idempotency-Key)  → 200 confirmada | 410 reserva expirada
```

## 4. Datos

```
asientos( evento_id, asiento_id, estado, reserva_id, version )     estado: LIBRE | RESERVADO | VENDIDO
reservas( id, usuario_id, evento_id, estado, expira_en )
pagos( id, reserva_id, clave_idempotencia UNIQUE, estado )
```

## 5. La decisión central: reservar sin vender dos veces

| Opción | Cómo | Valoración |
|---|---|---|
| Actualización condicional | `UPDATE asientos SET estado='RESERVADO', reserva_id=:r WHERE asiento_id=:a AND estado='LIBRE'` y comprobar filas afectadas | Atómica, simple, sin esperas. Recomendada |
| Bloqueo pesimista | `SELECT … FOR UPDATE` | Correcta, pero genera esperas y contención |
| Bloqueo optimista | Columna `version` | Correcta; muchos reintentos bajo contención alta |
| Bloqueo distribuido (Redis) | Clave por asiento con expiración | Rápido; exige cuidado para que sea correcto y no reemplaza a la base |

```sql
BEGIN;
UPDATE asientos
   SET estado = 'RESERVADO', reserva_id = :reserva
 WHERE evento_id = :evento
   AND asiento_id = ANY(:asientos)
   AND estado = 'LIBRE';
-- si las filas afectadas son menos que las pedidas: ROLLBACK y responder 409
INSERT INTO reservas (...) VALUES (...);
COMMIT;
```

La base de datos es el árbitro. La corrección no depende de la caché ni de la aplicación.

## 6. Alto nivel

```
Usuarios ──► CDN ──► Sala de espera virtual ──► balanceador
                                                  │
                    ┌─────────────────────────────┼──────────────────────┐
                    ▼                             ▼                      ▼
          Servicio de catálogo           Servicio de reservas     Servicio de pagos
          (caché de disponibilidad)      (base transaccional)     (pasarela externa)
                                                  │                      │
                                         expiración de reservas ◄── eventos de pago
```

## 7. Profundización

**Sala de espera virtual.** Es la pieza que hace viable el sistema. Los usuarios entran a una cola y se admiten a la venta por tandas, al ritmo que el sistema soporta. Protege a la base, da trato justo y una experiencia predecible ("estás en la posición 12.400"). Sin ella, el pico tumba el sistema y nadie compra.

**Disponibilidad.** Se sirve desde una caché que se actualiza con los eventos de reserva. Puede estar unos segundos atrasada: el usuario lo descubre al intentar reservar (`409`) y elige otro asiento. Consistencia eventual para leer, fuerte para escribir.

**Expiración de reservas.** Cada reserva tiene `expira_en`. Un proceso periódico libera las vencidas (con `SKIP LOCKED` si hay varias instancias), y además la condición de la consulta trata como libre un asiento cuya reserva expiró. No depender de un único mecanismo.

**Pago.** Con clave de idempotencia. El caso difícil: el pago se aprueba cuando la reserva ya expiró y el asiento fue tomado por otro. Opciones: extender la reserva al iniciar el pago, o reembolsar automáticamente. Hay que elegir una y programarla.

**Contención.** Si todos quieren los mismos asientos, casi todos fallan. Alternativa para localidades sin asiento numerado: decrementar un contador de forma atómica (`UPDATE … SET disponibles = disponibles - :n WHERE disponibles >= :n`).

**Protección.** Límite de tasa por usuario, tope de boletas por compra, detección de automatización.

**Fallos.** Si cae la pasarela: las reservas siguen vivas mientras no expiren, y un cortacircuitos evita aceptar pagos que no se podrán procesar.

## Compromisos tomados

| Decisión | Se gana | Se cede |
|---|---|---|
| Sala de espera | Estabilidad y trato justo | El usuario espera |
| Disponibilidad desde caché | Soporta el pico de lecturas | Datos con segundos de retraso |
| Reserva en base relacional | Corrección garantizada | Menor rendimiento máximo que una solución en memoria |
| Reserva temporal | El usuario paga con calma | Asientos bloqueados sin venderse |

## Taller

1. Implementa la reserva con la actualización condicional. Lanza 1.000 hilos sobre 100 asientos y demuestra que se reservan exactamente 100.
2. Implementa las otras dos estrategias de base de datos y compara rendimiento y número de reintentos.
3. Implementa la expiración y demuestra que un asiento vuelve a estar disponible.
4. Resuelve en código el caso del pago aprobado tras la expiración.
5. Implementa una sala de espera sencilla que admita N usuarios por segundo.
6. Escribe el documento de diseño completo con tus compromisos.

Rama `taller/n11-05-boletas`.

## Preguntas senior

1. ¿Cómo garantizas que un asiento no se venda dos veces? Defiende tu mecanismo frente a los otros.
2. ¿Qué operaciones exigen consistencia fuerte y cuáles toleran la eventual?
3. ¿Qué problema resuelve la sala de espera?
4. ¿Qué pasa si el pago llega después de expirar la reserva?
5. ¿Cómo pruebas un sistema así antes del día de la venta?
