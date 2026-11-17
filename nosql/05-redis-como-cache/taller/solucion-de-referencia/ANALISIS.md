# Taller 05 — Redis como caché del catálogo

Código: `src/main/java/nosql/adaptadores/redis/` (`CatalogoConCache`, `CambiarPrecioInvalidando`, `PublicadorDeInvalidaciones`, `ClienteRedis`) y `src/test/java/nosql/CacheTest.java`. Lettuce 7.9.0, `redis:8.10.3`. Lo medido queda en `salida/resultados.txt` al ejecutar las pruebas.

`CatalogoConCache` decora **`ConsultarProducto`**, el puerto de entrada de consulta que devuelve una **`VistaDeProducto`** (precio y existencias), no un puerto de salida que devuelva las entidades con las que el dominio decide. Lo que se guarda en Redis es la vista (`1|precio|existencias`). Por construcción, ningún caso de uso de escritura recibe el decorador: `CacheTest.elNucleoNoConoceRedisYLaEscrituraNoRecibeLaCache` recorre por reflexión los constructores de `ServicioDePedidos`, `ServicioDePedidosConEspera`, `ServicioDeCatalogo` y `CrearPedidoAcotado` y comprueba que ninguno pide un `ConsultarProducto`; el precio con el que se crea un pedido sale siempre de `Catalogo` (MongoDB), nunca de la caché. Cache-aside con claves `pedidos:catalogo:v1:producto:{id}` (dueño: este adaptador; versión de formato: `v1`), TTL con azar del ±20 %, caché negativa (marca `NULO`, 30 s), carga única por proceso o cerrojo de recarga, filtro de Bloom opcional y, si Redis falla, lectura directa de la base. Si la carga única falla, los hilos que esperaban su resultado no heredan el error: cada uno lee de la base por su cuenta (`paso3b`: de 20 peticiones solo la que cargaba recibe el error).

**"Instancias".** En los pasos 3 y 4 una "instancia" es un decorador `CatalogoConCache` con su propia memoria (el mapa de cargas en vuelo) **dentro del mismo proceso** y contra el mismo Redis: lo que importa del paso es que las instancias no comparten memoria entre ellas, y eso se cumple. Con procesos reales (dos contenedores) lo mide el simulacro del taller 07: `FLUSHALL` en plena carga produce como máximo una lectura a MongoDB por producto e instancia.

## Paso 1. Aciertos y latencia (20.000 lecturas sesgadas sobre 1.000 productos, TTL 600 s para que la medida no dependa de la velocidad de la máquina)

Tasa de aciertos **95,0 %** (19.000 aciertos, 1.000 lecturas a MongoDB). Todas las claves de Redis tienen caducidad (0 sin TTL de 1.000). **Latencia media: 177 us con caché frente a 238 us leyendo directamente de MongoDB** en la última ejecución (en otras: 210 frente a 183 us, y 861 frente a 264 us). En esta máquina (MongoDB local en Docker, lectura por `_id`) la diferencia **no es sistemática**: el viaje a Redis cuesta lo que cuesta el viaje a MongoDB. La caché aquí protege a la base de la carga y de la estampida; solo adelanta latencia si la base es más lenta o está lejos.

## Paso 2. Cuánto tiempo se puede ver un dato viejo (TTL 3 s con azar del 20 %: máximo 3,6 s)

| Cómo cambia el precio | Tiempo hasta verlo |
|---|---|
| (a) Por el puerto de entrada, con borrado de la clave después de confirmar | 8 ms |
| (b) Directo en la base, sin pasar por el decorador | 2.909 y 3.571 ms en dos ejecuciones (hasta 3.600 ms: **el TTL con su azar es la cota del dato viejo**) |
| (c) Directo por otro proceso que usa el servicio, con invalidación por eventos desde la bandeja de salida (sondeo cada 100 ms) | 1 a 2 ms en las ejecuciones medidas (cota: el intervalo del sondeo, 100 ms) |

El borrado va **después** de confirmar la transacción: si fuera antes, una lectura intermedia volvería a guardar el valor viejo.

## Paso 3. Estampida (200 peticiones a la vez sobre una clave caducada, la base tarda 100 ms)

| Variante | Lecturas a la base |
|---|---|
| Sin protección | **200** |
| Carga única por proceso (una instancia) | 1 |
| Carga única por proceso, tres instancias | 3 |
| Cerrojo de recarga (`SET NX PX`), tres instancias | **1** |

Tres lecturas son una por instancia; el cerrojo de recarga la deja en una (los perdedores esperan sondeando la clave hasta un segundo y, si no aparece, leen de la base).

## Paso 4. Penetración (4.000 peticiones a identificadores inexistentes)

| Protección | Ids repetidos (500 distintos) | Ids al azar todos distintos |
|---|---|---|
| Ninguna | 4.000 lecturas | 4.000 |
| Caché negativa | 499 | **4.000** (no ayuda con ids siempre nuevos, y llena Redis) |
| Filtro de Bloom (1 %, 10.000 ids existentes) | 1 | 20 (los falsos positivos) |

Memoria (`MEMORY USAGE`) con 10.000 identificadores: filtro de Bloom 13.936 bytes; conjunto (`SET`) 425.611 bytes. Y lo que ocupa **la caché negativa** tras el ataque de 4.000 identificadores siempre nuevos: unos 337.500 bytes de claves `NULO` que no sirven para nada (25 veces el filtro, y crecen con el ataque; el filtro no crece).

## Paso 5. Redis colgado y Redis caído

* **Colgado** (`docker pause`: el socket sigue abierto y nadie contesta, el peor caso): con el cliente por defecto una lectura **sigue sin responder tras 8 s** (el tiempo de espera de Lettuce es de 60 s). Con tiempos de 300 ms la lectura responde desde la base en ~310 ms.
* **Caído** (`docker stop`), con tiempos de espera de 300 ms, `REJECT_COMMANDS` y caída a la base: 300 de 300 lecturas respondidas, media ~3,7 ms, p95 ~3,9 ms (la base tarda 3 ms), con 300 errores de Redis contados. Al volver Redis, la caché vuelve a dar aciertos sola.
* Por qué dos pruebas: con el contenedor **detenido**, Docker Desktop sigue aceptando la conexión TCP en el puerto publicado y la reinicia al primer comando, así que el cliente por defecto a veces ve `Connection reset` en lugar de bloquearse (una de cada tres ejecuciones antes de separar los casos). Un servicio colgado no tiene ese ruido y es, además, el que más cuesta en producción.

## Paso 6. `maxmemory 5mb` y `allkeys-lru`

Con 60.000 productos cargados: `evicted_keys` 28.700 a 47.700 según la ejecución (depende de cuánto pese cada clave y de la memoria que Redis tenga ya reservada), quedan entre 12.000 y 31.000 claves de 60.000, tasa de aciertos con lectura sesgada del 36 al 51 %, y **0 errores de Redis durante el llenado** (con `allkeys-lru` Redis desaloja, no rechaza escrituras; la prueba lo exige porque el decorador captura `RedisException` y un OOM habría quedado oculto). Antes de bajar `maxmemory` la prueba vacía Redis de forma **síncrona** y espera a que la memoria baje (una precaución barata; no se pudo demostrar que fuera la causa del `OOM` intermitente que se vio una vez en el taller 06 con `allkeys-lru`: vaciar de forma asíncrona 87 MB y bajar `maxmemory` a 5 mb al instante no lo provoca; ver el análisis del taller 06). La caché se degrada sin fallar: es el uso correcto de `allkeys-lru`. (La configuración se restablece al terminar.)

## Paso 7. Por qué no se cachea el repositorio de pedidos

Un lector lee `PENDIENTE` de la base; entre su lectura y su `SET` en la caché, otro paga el pedido y borra la clave; el lector guarda entonces el valor viejo. Resultado medido: la base dice `CONFIRMADO` y la caché `PENDIENTE` durante el TTL. Para un dato que se escribe sin parar, y sobre el que se decide (el servicio lee para escribir con la versión), una caché desfasada produce rechazos y decisiones equivocadas.

## Criterios de aceptación

| Criterio | Dónde |
|---|---|
| Ninguna clave sin caducidad | Paso 1: 0 de 1.000 |
| Tiempo máximo del dato viejo medido y escrito | Paso 2 |
| La estampida carga una vez por instancia, o menos | Paso 3 |
| Lo inexistente no llega a la base en cada consulta | Paso 4 |
| Con Redis caído el servicio responde, más lento | Paso 5 |
| El núcleo no conoce Redis, y la escritura no recibe la caché | `CacheTest.elNucleoNoConoceRedisYLaEscrituraNoRecibeLaCache`: 0 violaciones en los módulos 04 y 05 y en el núcleo; ningún caso de uso de escritura pide `ConsultarProducto` |
