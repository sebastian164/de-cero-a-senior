# Taller 07 — Integrador: pedidos con MongoDB y Redis

El servicio de pedidos del módulo hexagonal (`tienda.*`, tal cual) con los adaptadores de MongoDB (taller 04), la caché del catálogo (05) y la protección con Redis (06), en **dos instancias dentro de contenedores** detrás de un `compose.yaml`. Código: `src/main/java/integrador/Servidor.java` (la raíz de composición de una instancia), `src/main/java/nosql/adaptadores/mongo/VerificadorDeInvariantes.java`, `Dockerfile`, `compose.yaml` y `src/test/java/integrador/` (`SimulacroTest`, `GeneradorDeCarga`, `TransaccionHuerfanaTest`). Lo medido queda en `salida/` al ejecutar las pruebas (esa carpeta no se publica). La defensa está en [`DEFENSA.md`](DEFENSA.md).

## Cómo se levanta

`docker compose -p nosql-integrador -f compose.yaml up -d --build --wait` (una orden, desde cero: construye la imagen `eclipse-temurin:21-jre` con el jar del servicio (que lleva dentro los talleres 04 a 07) y levanta MongoDB 9.0.8 como replica set de un nodo con `transactionLifetimeLimitSeconds=15`, Redis 8.10.3 y `servicio-a` (:18081) y `servicio-b` (:18082)). Las instancias entran a MongoDB por `mongodb://mongo:27017/?directConnection=true`, como enseña `nosql/entorno/README.md`. Antes hay que dejar los jar en `target/libs` con `./preparar-imagen.sh` (hace `mvn package dependency:copy-dependencies`); las pruebas lo esperan hecho. Tiempo medido de `up --build --wait`: 14 s con la imagen ya construida o construyéndose en local.

## Requisitos funcionales

| Requisito | Cómo se cumple |
|---|---|
| `POST /pedidos` con varias líneas y `Idempotency-Key` | `AdaptadorHttp` → `CrearPedidoConCobro` → `CrearPedidoAcotado` → `ServicioDePedidosConEspera` (simulacro 1 y 2) |
| `GET /pedidos/{id}` y `GET /clientes/{id}/pedidos` paginado por clave | `AdaptadorHttp` y `HistorialMongo` (cursor `fecha\|id`); la carga de fondo pide páginas todo el tiempo |
| `GET /productos/{id}` desde la caché | `CatalogoConCache` sobre `ConsultarProducto` (simulacros de Redis y de precios) |
| Pagar, enviar y cancelar respetan las transiciones | El núcleo tiene `aprobar`, `confirmar` y `cancelar`: `POST /pedidos/{id}/confirmar` (o `/pagar`, mismo caso de uso). **No hay "enviar"**: el estado `ENVIADO` no existe en el módulo hexagonal |
| Tarea que cancela los PENDIENTE viejos y devuelve existencias | `Servidor` + `CerrojoRedis` + `CancelarAbandonados` (caso de uso del hexágono: dominio, unidad de trabajo y evento en la bandeja; umbral 30 min por defecto, 1 s en el simulacro) |

## Requisitos de diseño

| Requisito | Dónde |
|---|---|
| Cada colección y documento justificados por una tabla de consultas | `DEFENSA.md` §1 |
| Ningún arreglo sin tope | `lineas` ≤ 50: `CrearPedidoAcotado` (el `POST` con 51 líneas responde 422: `ServicioEnAmbosAdaptadoresTest.cincuentaYUnaLineasSeRechazan`, memoria y MongoDB) y validador de MongoDB (código 121: `elValidadorDeMongoRechazaLoQueLaAplicacionNoVio`); `historial` ≤ 20 con `$slice` y validador |
| Índices justificados | `EsquemaMongo`: `{clienteId, fecha, _id}` (C3), `{estado, fecha}` (C4), `{publicado, ocurridoEn}` (bandeja); la clave de idempotencia es el `_id` de `peticiones` |
| MongoDB y Redis solo en adaptadores | `s10_laTecnologiaSoloEstaEnAdaptadoresYComposicion`: 0 violaciones en 81 archivos del núcleo y de los módulos 04 a 07 |
| El contrato pasa contra memoria y contra MongoDB | Taller 04 (`ContratoDelNucleoTest`: los contratos del módulo hexagonal sin tocar) |
| La caché decora un puerto de consulta de vistas | Taller 05 (`ConsultarProducto`) |
| El cerrojo tiene ficha y turno, y la escritura comprueba el turno | Taller 06 (`CerrojoRedis`, `TurnosMongo`; el turno lo emite MongoDB) |

## El simulacro

Con una **carga de fondo continua** (`GeneradorDeCarga`: dos hilos que crean pedidos —reintentando con la misma clave si la conexión se corta—, los consultan, piden sus páginas, confirman y cancelan, por las dos instancias, durante todas las pruebas), y el **verificador de invariantes** tras cada prueba (con la carga en pausa un instante, para que no haya peticiones en vuelo). El verificador comprueba: existencias = iniciales − líneas de los pedidos no cancelados; estados dentro del enumerado; el historial es un camino legal del núcleo que termina en el estado actual y la versión es el número de guardados; no hay dos claves de idempotencia para el mismo pedido ni pedidos sin clave; cada pedido tiene sus eventos —`PedidoCreado` y exactamente uno por transición, ni más ni menos, **sin excluir ningún pedido**—; ningún evento de un pedido que no existe; cada cobro corresponde a la clave de un pedido y por su importe, y cada pedido tiene su cobro; el turno aplicado nunca supera al emitido.

| Prueba | Resultado | Verificador de invariantes |
|---|---|---|
| 20 compradores, 5 unidades (x10 repeticiones)                  | cada repetición: 5 pedidos 201, 15 rechazos 422, existencias 0                             | verificador: OK |
| 30 POST enviados 3 veces con la misma clave (90 envíos)        | 30 pedidos, 30 cobros, 0 duplicados                                                        | verificador: OK |
| docker kill de una instancia a mitad de 60 'crear pedido'      | 19 respondieron antes de morir, 41 cortados; reintentos con la misma clave: 60 pedidos en 2 s (3 respuestas 409/503/tiempo agotado) | verificador: OK |
| Redis detenido 120 s con carga continua                        | 4440 peticiones, 0 errores 5xx; latencia media 7 ms con Redis, 5 ms sin él; caché recuperada en 1119 ms (a) y 1140 ms (b) tras volver | verificador: OK |
| FLUSHALL en plena carga (50 hilos, 2 instancias)               | 8064 peticiones, 40 lecturas a MongoDB (máximo teórico: (10 productos de la prueba + 20 de la carga de fondo) x 2 instancias = 60) | verificador: OK |
| 100 precios cambiados directamente en MongoDB                  | todos visibles en 4665 ms (instancia a) y 4738 ms (b); promesa: TTL 4000 ms + 20 % de azar = 4800 ms | verificador: OK |
| un cliente envía 100 pedidos en 10 s (límite 20 por 10 s)      | 29 aceptados, 71 con 429 y Retry-After en 11 s; otros clientes: 5 de 5 con 201             | verificador: OK |
| publicador de la bandeja detenido, 10 pedidos, publicador reiniciado | pedidos aceptados con 201; 47 eventos pendientes; todos publicados 3 ms después de reiniciar | verificador: OK |
| docker pause del dueño del cerrojo (vida 2 s, umbral 1 s)      | pendientes de partida 2370, todos cancelados una vez y con un evento cada uno; contadores: dueño 0, la otra 2230 tras el relevo (17 s); turnos rechazados 1; pendientes al pausar 2230 | verificador: OK |
| prueba de arquitectura: 81 archivos (núcleo y módulos 04 a 07), 0 violaciones | ok | - |

Carga de fondo durante todo el simulacro: 393 pedidos creados, 782 consultas, 391 páginas, 130 confirmados, 131 cancelados, 0 rechazos 4xx, 0 respuestas 5xx, 14 cortes de conexión, 7 reintentos de creación con la misma clave.

### Notas sobre lo medido

* **Límite de tasa: más de 20 aceptados.** Es una ventana deslizante de 10 s y el cliente envía durante 10 s: las primeras caducan mientras envía y entran unas pocas más. Nunca hay más de 20 en ninguna ventana de 10 s. La prueba exige además que el limitador no estuviera degradado (`limiteRedisCaido` sin cambios): en una ejecución anterior una instancia seguía reconectando a Redis y dejó pasar 50.
* **Recuperación tras volver Redis: ~1,1 s por instancia.** En una primera versión se vieron 4 a 19 s porque la reconexión de Lettuce usaba su espera por defecto; ahora el servidor la acota (`ClientResources` con `reconnectDelay` de 500 ms). La prueba espera a que **cada instancia por separado** vuelva a dar aciertos y deje de acumular `erroresRedis` antes de seguir (`salida/recuperacion.txt`), y cada prueba que falla vuelca las `/metricas` de las dos instancias a `salida/metricas-al-fallar.txt`.
* **`FLUSHALL`**: se cuentan también los 20 productos que consulta la carga de fondo; el máximo teórico es (10 + 20) × 2 instancias.
* **`docker pause` y `docker kill` de verdad**, sobre los contenedores (`Compose.pausar`, `reanudar`, `matar`). Ya no se usa `kill -STOP` sobre un proceso de la máquina.
* **Contadores del dueño caducado.** `canceladosPorTarea` de una instancia no cuenta el lote que se abortó a medias por turno caducado (el contador se suma al terminar el lote). La prueba lo comprueba contra la base (pendientes de partida = cancelados nuevos = eventos `PedidoCancelado` nuevos) y trata el contador como informativo.
* **El relevo tras `docker pause` del dueño.** En una primera versión se vieron 61 a 90 s; era compatible con una transacción huérfana. Después se aisló (siguiente apartado) y el compose acota la espera a ~22 s. En el simulacro el relevo tardó 17 s (entre 3 y 17 s en las ejecuciones hechas: depende de si el dueño tenía una transacción abierta cuando se pausó; si la tenía, el relevo espera a que MongoDB la aborte).

## Transacciones huérfanas: cuánto tarda en recuperarse un producto tras matar una instancia

`TransaccionHuerfanaTest` abre una transacción en un proceso Java, escribe un producto, imprime `ABIERTA` y la prueba lo mata con `kill -9`. Después mide cuánto tarda otra transacción en poder escribir ese producto (cada intento falla con `WriteConflict`, `TransientTransactionError`, lo que el servicio traduce a `ConflictoDeConcurrencia`):

* transactionLifetimeLimitSeconds=5: una transacción nueva sobre el mismo documento falló 28 veces con WriteConflict y pudo escribir 7272 ms después del kill -9
* transactionLifetimeLimitSeconds=15: una transacción nueva sobre el mismo documento falló 85 veces con WriteConflict y pudo escribir 22012 ms después del kill -9
* transactionLifetimeLimitSeconds=60: una transacción nueva sobre el mismo documento falló 346 veces con WriteConflict y pudo escribir 89528 ms después del kill -9

La espera es **1,5 veces el límite** (el límite más el barrido que aborta las caducadas, que corre cada mitad del límite). Decisión: el compose fija `transactionLifetimeLimitSeconds=15`. Las transacciones del servicio duran milisegundos, así que 15 s es un margen de tres órdenes de magnitud; bajarlo a 5 s también cabría, pero deja poco margen ante una pausa de GC o una red lenta, y se pagaría abortando transacciones legítimas (el cliente recibe un 409/503 y reintenta). Con el valor por defecto (60 s) los productos de una instancia muerta quedarían ~90 s con 409/503. Está escrito como garantía y límite en `DEFENSA.md` §4.

## Criterios de aceptación

| Criterio | Estado |
|---|---|
| El sistema levanta desde cero con una orden | **Cumplido**: `docker compose up -d --build --wait` con MongoDB, Redis y las dos instancias del servicio en contenedores; el simulacro lo ejecuta desde cero (`docker compose down -v` antes). Requiere haber dejado los jar en `target/libs` (`preparar-imagen.sh`); no se probó en un clon limpio con red (la base de la imagen y las dependencias estaban en local) |
| Los contratos pasan contra memoria y MongoDB | Taller 04: los dos contratos del módulo hexagonal, sin modificar, en los dos adaptadores |
| Tabla del simulacro completa y verificador tras cada prueba | Tabla de arriba |
| Cinco unidades dan exactamente cinco pedidos, diez veces | Primera fila |
| Dominio y aplicación sin `com.mongodb` ni `io.lettuce` | `s10` y las pruebas de arquitectura de 04, 05 y 06 |
| La defensa nombra límites reales | `DEFENSA.md` §4 |
