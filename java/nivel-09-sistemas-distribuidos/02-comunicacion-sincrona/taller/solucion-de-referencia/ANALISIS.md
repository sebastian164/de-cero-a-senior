# Solución — n9-02 Comunicación síncrona

Dos servicios Spring Boot 4.1.1 reales, arrancados por `ComunicacionSincronaTest` en el mismo proceso pero con su propio contexto, su propio Tomcat y su propia configuración:

- **tienda** (puerto 43081, `tienda.yaml`): 10 hilos de Tomcat, llama a inventario con `RestClient`. Reintento con Resilience4j 2.4.0 (`resilience4j-spring-boot4`).
- **inventario** (puerto 43082, `inventario.yaml`): existencias (lento al azar o colgado, a voluntad de la prueba) y cobros en PostgreSQL 18.7 (puerto 43432).

Las cifras son de la corrida del 2026-11-15; cambian algo entre corridas y las aserciones solo comprueban rangos.

## 1. Sin tiempo límite, el primero agota sus hilos

`RestClient` con `JdkClientHttpRequestFactory` por defecto: sin tiempo de respuesta, espera para siempre. Con inventario colgado, 15 peticiones a `/productos-sin-limite/1` ocupan los 10 hilos de Tomcat de la tienda (las 5 restantes esperan en la cola de aceptación). **`/salud`, que no usa inventario, no respondió en 2 s.** Al liberar inventario todo vuelve a responder: el fallo de una dependencia se propagó a una ruta que no la usa.

## 2. Tiempos límite y reintentos con retroceso exponencial y variación

`conTiemposLimite(500 ms de conexión, 300 ms de respuesta)` (el ayudante que el README usa sin definir, implementado con `JdkClientHttpRequestFactory`) y `@Retry(name = "inventario")`: 3 intentos, espera base 100 ms, multiplicador 2, variación ±50 %, solo `ResourceAccessException` (tiempo vencido, red) y 5xx; los 4xx se ignoran.

| Medición | Resultado |
|---|---|
| Inventario colgado, 15 peticiones | `/salud` respondió en **939 ms**; las 15 devolvieron 504; inventario recibió 45 llamadas (15 × 3) |
| Esperas entre intentos (sin contar los 300 ms de espera vencida) | 150 ms y 107 ms: dentro de 100 ± 50 y 200 ± 100 |
| Inventario lento al azar (40 % tarda 1 s), 100 peticiones | Sin reintento **62** éxitos; con 3 intentos **93** (teórico 60 y 1 − 0,4³ = 93,6) |
| `GET /productos/404` | 404 propagado; **1** sola llamada a inventario |

## 3. La respuesta se pierde: el reintento duplica

Inventario confirma el cobro y tarda 1 s en responder (más que los 300 ms de la tienda). `POST /pagos/42` reintenta un POST no idempotente: la tienda responde 504, y **inventario registró 3 cobros del pedido 42**. El cliente cree que falló y se cobró tres veces.

## 4. Claves de idempotencia

La tienda genera una clave por operación de negocio y la repite en cada reintento (`Idempotency-Key`). Inventario (`Cobros.cobrarUnaVez`), en **una sola transacción**: `INSERT INTO claves_idempotencia ... ON CONFLICT (clave) DO NOTHING`; si no insertó, devuelve el resultado guardado; si insertó, cobra y guarda el resultado.

- Con la respuesta perdida: los tres intentos vencen igual (504), pero **hay 1 cobro**. Cuando la red se recupera, repetir con la misma clave devuelve el mismo resultado sin cobrar.
- **Simultáneas:** 20 pares de peticiones a la vez con la misma clave: 1 cobro por pedido y la misma respuesta en ambas. Funciona porque la segunda transacción **espera en el índice único** a que la primera confirme; entonces `ON CONFLICT DO NOTHING` no inserta y lee el resultado.

Como advierte el README, buscar y después guardar no evita el doble cobro: un fragmento `cobrar` que busca la clave y, si no está, llama a la pasarela y **después** guarda, falla con dos peticiones simultáneas: ambas pasan la búsqueda y **las dos llaman a la pasarela**; la restricción única solo hace fallar el segundo `guardar`, cuando el cobro ya se hizo. Se comprobó ejecutándolo: 2 llamadas a la pasarela y una `DataIntegrityViolationException`. La solución de este taller reserva la clave **antes** del efecto.

## 5. Presupuesto de tiempo de una cadena de tres servicios

El cliente final espera como máximo **2.000 ms**. Cadena: cliente → A → B → C. Reintentos en una sola capa (A), y cada límite menor que el de quien espera arriba, con margen para el trabajo propio y la red.

| Salto | Tiempo límite | Cuenta |
|---|---:|---|
| Cliente → A | 2.000 ms | Lo que acepta el usuario |
| A (trabajo propio, serialización, red) | ~200 ms | Reserva de A |
| A → B, con reintento | 2 intentos × 750 ms + 100 ms de espera = 1.600 ms | 200 + 1.600 = 1.800 ms < 2.000 (margen 200 ms) |
| B (trabajo propio) | ~150 ms | |
| B → C, sin reintento | 500 ms | 150 + 500 = 650 ms < 750 ms (margen 100 ms) |
| C | debe responder en < 500 ms | Su p99 debe estar muy por debajo; si no, el presupuesto es irreal |

Además, A envía a B el plazo restante (por ejemplo, una cabecera con la hora límite) para que B no siga trabajando en algo que A ya abandonó. Si B también reintentara 2 veces a C, C recibiría hasta 4 peticiones por cada una del cliente, y con tres capas reintentando 3 veces, 27.

## Un matiz que la solución no sufre y el README sí podría

En la solución el "cobro" es un `INSERT` local en la misma transacción que la reserva de la clave: si el proceso cae antes del *commit*, se revierten las dos cosas y el reintento empieza limpio. Con un efecto remoto (una pasarela de verdad) no es así: si el proceso cae después del cobro y antes del *commit*, la reserva de la clave se revierte pero el cobro ya ocurrió, y el reintento cobraría otra vez. La defensa es enviar a la pasarela la misma clave de idempotencia (el proyecto 5 lo hace: ver `PasarelaSimulada`, que deduplica por clave). El README lo dice ahora en una frase.
