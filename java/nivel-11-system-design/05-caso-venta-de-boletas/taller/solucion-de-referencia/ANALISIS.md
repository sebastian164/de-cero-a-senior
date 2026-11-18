# Taller 11-05: venta de boletas

Código: `src/main/java/boletas/` (JDBC + HikariCP sobre PostgreSQL 16 real). Las pruebas están en `src/test/java/boletas/` y se ejecutan con `./taller.sh` (ver `README.md`).

## 1. Reserva con actualización condicional

Cada reserva es **una transacción** (`Reservas.reservarVarios`, la del README ampliada con vencimiento): `UPDATE asientos SET estado='RESERVADO', reserva_id=?, reservado_hasta=... WHERE evento_id=? AND asiento_id = ANY(?) AND (libre o vencido)`; si las filas afectadas son menos que las pedidas, `ROLLBACK` y se responde 409 (`SIN_RESERVA`); si no, `INSERT INTO reservas` y `COMMIT`. **1.000 hilos sobre 100 asientos** (cada hilo recorre los asientos desde un punto al azar hasta conseguir uno): **exactamente 100 reservas exitosas**, 100 asientos `RESERVADO`, 100 reservas distintas y **100 filas en `reservas`**: los 900 intentos fallidos no dejan reservas huérfanas (una versión anterior insertaba la reserva antes de tomar el asiento y dejaba 900 en `ACTIVA`). Ningún asiento se vende dos veces. La corrección la garantiza la base, no la aplicación.

La transacción del README, **tal cual** (`UPDATE ... SET estado='RESERVADO', reserva_id=:reserva ... AND estado='LIBRE'`, ROLLBACK si faltan filas, INSERT de la reserva), vive en `Reservas.reservarVariosComoEnElReadme`; la prueba `elSqlDeLaTransaccionEsElDelReadme` lee el README, extrae la sentencia y comprueba que es idéntica (normalizando `:parametros` y espacios) a la que ejecuta el código, y `laTransaccionDelReadmeReservaVariosAsientosOCancelaTodo` comprueba que pedir {3, 4, 5} con el 3 ocupado no toma el 4 ni el 5 ni deja fila de reserva.

## 2. Las otras dos estrategias, comparadas

| Estrategia | Reservas | Tiempo (1.000 hilos, pool de 40) | Reintentos de lectura |
|---|---|---|---|
| Condicional (UPDATE atómico) | 100 | ≈ 4 a 8 s | 0 |
| Bloqueo pesimista (`SELECT … FOR UPDATE`) | 100 | ≈ 6 a 18 s | 0 (pero las peticiones hacen cola) |
| Bloqueo optimista (columna `version`) | 100 | ≈ 4 a 7 s | 46 a 100 |

Las tres son correctas. La pesimista es la más lenta (dos viajes y retención de la fila); la optimista iguala a la condicional con poca contención por asiento, pero genera reintentos que crecen cuando todos quieren los mismos asientos; la condicional es la más simple y sin esperas ni reintentos. Los tiempos absolutos dependen de la máquina (compartida con otros procesos) y **el orden entre estrategias no es estable** entre ejecuciones: lo que sí es estable es la corrección (100 reservas, 100 filas, 0 huérfanas) y los reintentos del optimista. No se mide la espera del pesimista por separado: solo se afirma que las peticiones al mismo asiento se serializan.

## 3. Expiración

La condición de la consulta trata como libre un asiento `RESERVADO` con `reservado_hasta` pasado, y además un proceso periódico (`liberarVencidos`, con `FOR UPDATE SKIP LOCKED` para varias instancias) lo devuelve a `LIBRE` y marca `EXPIRADA` la fila de `reservas` cuyo plazo pasó. Prueba: A reserva con 1 s de vida, B falla; tras 1,3 s B lo toma **sin esperar a la limpieza**; otro asiento vencido lo libera el proceso (devuelve 1) y su reserva queda `EXPIRADA`. No se depende de un solo mecanismo.

## 4. Pago aprobado tras la expiración (resuelto en código)

Política elegida: (1) al **iniciar** el pago se extiende la reserva (el usuario no pierde el asiento mientras la pasarela responde); (2) si aun así la aprobación llega tarde: si el asiento sigue libre o es de esa reserva, **se vende**; si lo tomó otra persona, **se reembolsa automáticamente** y el asiento ajeno no se toca. Al iniciar el pago también se extiende `reservas.expira_en`; al resolverse, `reservas.estado` pasa a `CONFIRMADA` (venta) o `REEMBOLSADA`. El pago lleva clave de idempotencia (`UNIQUE`): repetir la misma clave devuelve el mismo resultado sin repetir efectos. Casos probados: A (venció, nadie lo tomó → CONFIRMADO), B (venció y lo tomó otra persona → REEMBOLSADO; el asiento sigue con la otra reserva), C (pago iniciado → la extensión impide que otro lo tome → CONFIRMADO), D (la pasarela rechaza → RECHAZADO).

## 5. Sala de espera

`SalaDeEspera(200)`: 800 usuarios llegan a la vez, se admiten **200 por segundo en orden de llegada** (medido: `[200, 200, 200, 200]` por ventana de 1 s; posición 1 → primer admitido). El sistema detrás nunca ve más de lo que soporta y el trato es justo. En producción la fila sería un servicio externo con una ficha firmada para entrar a la venta.

## 6. Documento de diseño

**1. Requisitos.** Ver eventos y disponibilidad, reservar 10 minutos, pagar, emitir boleta. Fuera: reventa, mapas de asientos interactivos avanzados. No funcional: **nunca vender dos veces** (consistencia fuerte en la reserva); pico de cientos de miles en minutos; la disponibilidad puede estar atrasada segundos; trato justo.

**2. Estimaciones.** Evento de 50.000 asientos; 500.000 usuarios en 5 minutos: 500.000 ÷ 300 s ≈ **1.700 usuarios nuevos/s**, y si cada uno consulta disponibilidad 10 veces ≈ 17.000 lecturas/s. Reservas: a lo sumo 50.000 exitosas; en la prueba, 1.000 hilos (≈ 100.000 sentencias condicionales en el peor caso) terminaron en ≈ 7 s con un pool de solo 40 conexiones y la base en la misma máquina, así que la base no es el límite a este volumen. Una **sala de espera a 500 admitidos/s** reparte los 500.000 usuarios en ≈ 1.000 s (≈ 17 minutos), y los 50.000 asientos se agotan mucho antes para casi todos. La inmensa mayoría de peticiones fallan por diseño: hay que rechazarlas barato.

**3. API.** `GET /v1/eventos/{id}/disponibilidad` (caché), `POST /v1/reservas` → 201 o 409, `POST /v1/reservas/{id}/pago` con `Idempotency-Key` → 200 o 410.

**4. Datos.** `asientos(evento_id, asiento_id, estado, reserva_id, reservado_hasta, version)`, `reservas`, `pagos(clave_idempotencia UNIQUE)`.

**5. Alto nivel.** CDN → sala de espera → balanceador → catálogo (caché de disponibilidad), reservas (PostgreSQL), pagos (pasarela externa con cortacircuitos).

**6. Compromisos.**
| Decisión | Alternativa | Costo |
|---|---|---|
| UPDATE condicional como árbitro | Bloqueo pesimista / optimista / Redis | La contención por los mismos asientos sigue existiendo; la sala de espera la absorbe |
| Reserva vencida cuenta como libre en la propia consulta | Solo proceso de limpieza | La consulta lleva una condición más; se evita depender de un reloj de otro proceso |
| Extender la reserva al iniciar el pago | Solo reembolsar tarde | Un asiento puede quedar bloqueado más tiempo si la pasarela se cuelga (límite de extensión: 2 minutos) |
| Disponibilidad en caché con consistencia eventual | Leer siempre de la base | El usuario ve un asiento libre que ya no lo está: 409 y elige otro |
