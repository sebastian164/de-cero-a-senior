# Taller 01 — Modelar por consultas (servicio de pedidos)

Código: `src/test/java/t01/ModeloTest.java` (JUnit 6 + controlador de MongoDB 5.14.0 contra `mongo:9.0.8`, replica set de un nodo).
Las pruebas escriben lo medido en `salida/resultados.txt` al ejecutarse. Se ejecuta con `mvn test` desde esta carpeta, con el [entorno](../../../entorno/) levantado.

## Paso 1. Consultas y volúmenes (tres años)

| # | Consulta o escritura | Frecuencia | Volumen que toca |
|---|---|---|---|
| C1 | Crear un pedido con sus líneas (y descontar existencias) | 10/s en pico | 1.000.000 de pedidos en 3 años; 1 a 5 líneas (máx. 50) |
| C2 | Leer un pedido por id | 50/s | un documento |
| C3 | Últimos 20 pedidos de un cliente | 30/s | 50.000 clientes; 20 pedidos de media, **un cliente muy activo con 20.000** |
| C4 | Pedidos PENDIENTE de más de 24 h (tarea de cancelación) | cada minuto | decenas de miles de PENDIENTE como mucho |
| C5 | Cambiar el estado de un pedido (pagar, enviar, cancelar) | 10/s | un documento; 2 cambios de estado por pedido de media |
| C6 | Leer precio y existencias de un producto | 200/s | 5.000 productos |
| C7 | Cambiar el precio de un producto | 1/min | un documento |

## Paso 2. Decisiones de modelado

| Relación | Decisión | Consulta y volumen que la justifican |
|---|---|---|
| Pedido y líneas | **Incrustar** (`lineas[]`, validador `maxItems: 50`) | C1 y C2 las leen siempre juntas; 1 a 5 líneas, máximo 50: el arreglo tiene tope |
| Pedido y cambios de estado | **Incrustar** (`historial[]`, `maxItems: 20`) | C5 los escribe con el pedido; 2 de media. El tope lo impone el validador |
| Pedido y cliente | **Referenciar** (`clienteId`) | C3 es "los pedidos de un cliente": 20.000 pedidos del cliente activo no caben dentro del cliente (paso 6). El nombre del cliente no se copia: C3 solo necesita el id |
| Línea y producto | **Referencia extendida**: `productoId` + `nombre` + `precio` copiados | C2 muestra el pedido sin consultar el catálogo; el producto cambia (C7) y el pedido no debe cambiar con él |

## Paso 3. El precio de la línea

**Copia.** La línea guarda el precio del catálogo en el momento de la compra, porque el pedido es un hecho: lo que el cliente aceptó pagar. Si el precio del catálogo cambia mañana, el pedido no se mueve. Se comprueba en las pruebas del taller 04 (`ServicioEnAmbosAdaptadoresTest`): se cambia el precio del catálogo a 9999 centavos y la línea sigue en 15.000.000 (`crearPedidoCopiaElPrecioDescuentaYAnunciaElEvento`). La referencia (leer el precio del catálogo al mostrar el pedido) falsearía el histórico y los totales.

## Paso 4. Volumen y tamaños (`$bsonSize`)

Carga real: 50.000 clientes, 5.000 productos y 1.000.000 de pedidos (el cliente 1 con 20.000), en 7 s con `insertMany` sin orden, lotes de 10.000.

| Colección | Tamaño medio | Tamaño máximo |
|---|---|---|
| clientes | 119 bytes | 120 bytes |
| productos | 73 bytes | 73 bytes |
| pedidos | 447 bytes | 621 bytes |

Máximo de líneas por pedido en los datos: 5; máximo del historial: 2. Ningún documento pasa de 16 KB (el límite es 16 MiB), y los dos arreglos tienen tope **impuesto por el validador** (50 y 20), no solo observado.

## Paso 5. Consultas con el volumen completo (índices `{clienteId, fecha}` y `{estado, fecha}`, 30 ejecuciones)

| Consulta | Media | p95 |
|---|---|---|
| C2 pedido por `_id` | 0,54 ms | 0,71 ms |
| C3 últimos 20 del cliente activo | 0,56 ms | 1,01 ms |
| C4 100 PENDIENTE de más de 24 h | 0,87 ms | 1,08 ms |

(La sesión 02 diseña y justifica estos índices con `explain`.)

## Paso 6. El antipatrón: pedidos dentro del cliente

Con el tamaño medio medido de un pedido del cliente activo (420 bytes) el límite previsto es 16 MiB / 420 = **39.991 pedidos**. Se insertaron pedidos con `$push` en lotes de 100 hasta que falló: falló tras **37.000** con el código 10334 (`Resulting document after update is larger than 16777216`). Desvío del 7,5 %: lo previsto y lo observado coinciden con la holgura que da trabajar con la media. El cliente activo (20.000 pedidos en tres años, ritmo de 18 al día) llegaría a ~37.000 en unos cinco años y medio: el fallo es lento y cae sobre el mejor cliente, que es el peor sitio. Además, cada `$push` reescribe un documento que crece.

## Paso 7. Validador `$jsonSchema`

La colección `pedidos` se crea con el validador (campos obligatorios, `estado` enumerado, `total >= 0`, `lineas` de 1 a 50, `cantidad >= 1`, `historial` de hasta 20). Rechazados con el código 121 (`Document failed validation`): pedido sin `clienteId`, con `total` -5, sin líneas, con estado `PERDIDO` y con 51 líneas. Un pedido bien formado se acepta.

## Relación con el servicio de los talleres 04 a 07

El modelo del integrador **es** este (mismos campos: `clienteId`, `estado`, `fecha`, `total`, `lineas[]` con `productoId`, `nombre` y `precio` copiados, `historial[]` con tope 20, `version`; mismo validador con `lineas` de 1 a 50, e índices `{clienteId, fecha, _id}` y `{estado, fecha}`), con dos diferencias que vienen del servicio del módulo hexagonal y no del enunciado: sus estados son `REQUIERE_APROBACION`, `PENDIENTE`, `CONFIRMADO` y `CANCELADO` (no `PAGADO` ni `ENVIADO`) y sus identificadores son cadenas. El validador del taller 04 (`EsquemaMongo`) usa el enumerado del núcleo; este taller usa el del enunciado del curso. Para el modelado da igual: el estado es una cadena de un enumerado con tope y cada cambio añade una entrada al historial.

## Criterios de aceptación

| Criterio | Dónde |
|---|---|
| Cada decisión cita una consulta y un volumen | Pasos 1 y 2 |
| Ningún documento tiene un arreglo sin tope | Paso 4: máximos medidos y topes del validador |
| El antipatrón falla donde se dijo | Paso 6: previsto 39.991, falló tras 37.000 |
| La colección rechaza documentos mal formados | Paso 7 |
| El precio de la línea tiene política explícita | Paso 3: copia |
