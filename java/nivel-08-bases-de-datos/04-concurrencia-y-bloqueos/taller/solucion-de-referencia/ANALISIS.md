# Solución — n8-04 Control de concurrencia

Spring Boot 4.1.1 (Hibernate 7.4.5) sobre PostgreSQL 18.7. Cifras de una corrida del 2026-11-15; las pruebas comprueban relaciones, no tiempos exactos.

## 1. `@Version` y 409 con un mensaje útil

- `Producto` tiene `@Version long version`. `paso1_dos_transacciones_chocan_por_version`: A lee, B confirma, A escribe; al confirmar, A recibe `ObjectOptimisticLockingFailureException` y el precio queda en el de B.
- Por HTTP (`paso1_por_http_…`): `GET /productos/1` devuelve la versión en `ETag`. Dos usuarios leen el mismo `ETag`; el primero guarda con `If-Match` (200 y `ETag` nuevo), el segundo recibe:

```json
{"detail":"Otro usuario modificó este producto mientras lo editabas. Recarga para ver los datos actuales y vuelve a aplicar tu cambio.","instance":"/productos/1/precio","status":409,"title":"Edición concurrente","type":"https://errores.ejemplo/edicion-concurrente"}
```

con `Content-Type: application/problem+json`. La versión va y vuelve con el cliente: así se detecta también la edición de un formulario abierto diez minutos, que nunca compartió transacción con nadie.

## 2. Reintento de tres intentos

`ServicioConReintento.fijarPrecio` (que solo reintenta y delega en `OperacionesTransaccionales`, un bean aparte con `@Transactional`: el reintento envuelve a la transacción, como en n8-03; la prueba comprueba que cada intento corre en una sesión de persistencia distinta) usa `@Retryable(includes = ObjectOptimisticLockingFailureException.class, maxRetries = 2)` de Spring Framework 7: un intento más dos reintentos. "Fijar el precio en X" es idempotente. Con tres editores simultáneos los tres terminan (5 intentos en total). Tres intentos bastan siempre en ese caso: cada editor solo puede chocar con los otros dos.

## 3. Optimista frente a pesimista con 50 hilos

50 hilos venden una unidad cada uno (stock 100, 5 ms de "cálculo" dentro de la transacción):

| Estrategia | Ventas confirmadas | Fallidas | Intentos | Tiempo |
|---|---:|---:|---:|---:|
| Optimista sin reintento | 5 | 45 (409) | 50 | 201 ms |
| Optimista con 3 intentos | 9 | 41 | 142 | 459 ms |
| Pesimista (`FOR UPDATE`, límite de 3 s) | 50 | 0 | 50 | 1.096 ms |

Ninguna pierde ventas: lo confirmado siempre coincide con el stock. Pero sobre una fila muy disputada el optimista rechaza casi todo y los reintentos apenas ayudan, mientras el pesimista atiende a todos en fila (más lento, sin rechazos). Es la tabla del README hecha números: optimista para conflictos raros, pesimista para inventario muy disputado (y aquí, mejor todavía, el `UPDATE` atómico de la sesión 03).

**Tiempo límite del bloqueo:** con otra conexión reteniendo `FOR UPDATE` sobre la fila, `buscarParaActualizar` (con la pista `jakarta.persistence.lock.timeout = 3000`) falla con `PessimisticLockingFailureException` a los 3.059–3.247 ms. Hibernate 7.4.5 sí aplica la pista en PostgreSQL.

## 4. Interbloqueo

Dos transferencias cruzadas (1→2 y 2→1), cada una bloquea primero su origen; una barrera garantiza que ambas tienen su primer bloqueo antes de pedir el segundo. Resultado: `[OK, 40P01]`: PostgreSQL detecta el interbloqueo y aborta una (`deadlock_detected`). Corrección: bloquear siempre en orden de `id` ascendente. 40 transferencias en ambas direcciones, todas confirmadas, y la suma de saldos sigue en 2.000.

## 5. Cola con `SKIP LOCKED`

300 tareas y tres trabajadores, cada uno toma lotes de 10 con la consulta del README (`FOR UPDATE SKIP LOCKED`). Cada tarea procesada se inserta en `procesadas`, cuya clave primaria haría fallar un duplicado. Resultado: 300 filas, 300 distintas, repartidas 100, 90 y 110 entre los trabajadores. Ninguna se procesó dos veces y ninguno esperó a otro.

## 6. `synchronized` con dos instancias

`InventarioSincronizado.vender` es `synchronized` y hace leer-calcular-escribir. Con una instancia, 40 hilos dejan el stock en 60 (correcto). Con dos instancias (dos contextos de Spring independientes contra la misma base, 20 hilos en cada una) el stock queda en 79: se perdieron 19 ventas. `synchronized` usa el monitor de un objeto en la memoria de un proceso; cada réplica tiene su propio bean y su propio monitor, y nada coordina a las dos. La coordinación tiene que estar donde ambas se encuentran: la base de datos (operación atómica, bloqueo de fila, versión) o un bloqueo distribuido.

Nota: las dos "instancias" son dos contextos de Spring en la misma JVM, no dos procesos. Es equivalente para lo que se demuestra, porque `synchronized` depende del objeto, y cada contexto crea el suyo.
