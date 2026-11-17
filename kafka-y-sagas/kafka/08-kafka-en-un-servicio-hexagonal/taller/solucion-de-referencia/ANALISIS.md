# Kafka 08 - Dos hexágonos que hablan por Kafka

Pedidos NO se reescribió: es el servicio de pedidos del módulo hexagonal, la solución de referencia del taller 08 de hexagonal (`hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src`, paquete `tienda.*`, 26 archivos de dominio y aplicación). El `pom.xml` copia ese directorio, de solo lectura, a `target/hexagonal-base` y lo compila tal cual. Lo que el taller añade a Pedidos es UN archivo: `tienda/adaptadores/salida/kafka/MensajeroKafka.java`.

Inventario es otro hexágono (`src/main/java/inventario/`, paquetes `dominio`, `aplicacion`, `adaptadores`), sin ninguna clase de Pedidos. Pruebas: `TiendaTest` (9, broker y PostgreSQL reales; arrancan los adaptadores con el cliente oficial y `com.sun.net.httpserver`, sin Spring). Evidencia en `salida/evidencia.txt` (la escriben las pruebas al ejecutarse).

## Qué archivos existentes hubo que tocar

Ninguno. El servicio de pedidos ya escribía sus eventos en la bandeja de salida (`PublicadorDeEventos`, implementado por `AlmacenEnMemoria`) y ya exponía `entregarPendientes(Consumer<EventoDePedido>)`, que entrega los eventos y solo los quita de la bandeja si el destino no lanza. Eso es justo el gancho que pide el paso 1. Un solo hallazgo de diseño: el evento de la aplicación (`EventoDePedido`: id, tipo, pedido, cliente) es delgado y no dice qué producto ni cuántas unidades. Para no tocar la aplicación, el mensajero (un adaptador) completa el mensaje consultando el puerto de entrada `ConsultarPedido` y publica un mensaje por línea del pedido. La alternativa, ensanchar el evento, cambiaría `aplicacion/` y habría que decidirlo en Pedidos.

## Pasos y evidencia

| Paso | Resultado |
|---|---|
| 1. Mensajero con el cliente de Kafka | tema `pedidos.creados`, clave = id del pedido, valor = JSON con id de evento estable (`pedido-creado:<pedido>#<producto>`), tipo, versión y datos. Razón de la clave: un pedido, una partición, y los eventos de un pedido llegan en orden; entre pedidos no hace falta orden |
| 2. Dominio y aplicación sin cambios | `paso2_...`: los 26 archivos de `tienda/dominio` y `tienda/aplicacion` compilados son iguales byte a byte a los del directorio de origen; en `src/main/java` no existe `tienda/dominio` ni `tienda/aplicacion`; el único archivo nuevo del paquete `tienda` es `MensajeroKafka.java` |
| Criterios de arquitectura | 30 archivos de dominio y aplicación (de los dos servicios) sin import de Kafka, SQL ni `comun`; Kafka solo en archivos de adaptadores; ni `tienda.*` importa `inventario.*` ni al revés |
| 3. Inventario como hexágono | `Existencias` (la regla de reserva), `ReservarPorPedido` (puerto de entrada), puertos de salida `Almacen` y `UnidadDeTrabajo`; adaptadores `jdbc`, `kafka` y `http` |
| 4. Errores del consumidor | ilegible o fuera de contrato: tema `pedidos.creados.fallidos` con cabeceras `motivo` y `origen`, y se sigue (no se arregla reintentando); base caída (`BaseNoDisponible`): se reintenta con espera creciente sin confirmar; otra excepción: se propaga (no se descarta un error desconocido) |
| 5. Caso de uso sin Kafka | reservas de 40 y 60 aceptadas, de 61 y 1 rechazadas por falta de existencias; stock final 0 |
| 6. Idempotencia | el mismo evento tres veces (más uno distinto): 2 reservas, stock 85 (100 - 10 - 5): la marca "visto" y la reserva van en la misma transacción |
| 7. Consumidor muerto tras procesar y antes de confirmar | `Runtime.halt(137)` tras el tercer mensaje: 3 reservas hechas; tras reiniciar, 10 reservas de 10 pedidos distintos, stock 80 |
| 8. Kafka detenido | la API HTTP del servicio hexagonal (`ServidorHttp`, `Idempotency-Key`) respondió 201, 201, 201; el mensajero no pudo entregar nada; al volver Kafka entregó los 3, la bandeja quedó vacía, la clave es el id del pedido y los eventos de cada partición salen en el orden de la bandeja |
| 9. Campo nuevo | el mensajero con `conCanal=true` emite versión 2 con `"canal":"web"`; Inventario, sin cambios, reservó las 4 unidades (lector tolerante) |
| 10. Segundo adaptador de entrada | el adaptador HTTP de Inventario tiene 24 líneas de código, solo traducción, repite 0 líneas de negocio; el reintento por HTTP devuelve `YA_PROCESADO` |

## Límite honesto

La bandeja de salida de este Pedidos es la del servicio hexagonal: en memoria (`AlmacenEnMemoria`). Sobrevive a Kafka caído (paso 8) pero no a la muerte del proceso de Pedidos. Con un adaptador de persistencia real (el JPA del módulo hexagonal, sesión 07) la misma bandeja sería una tabla, y entonces rige lo de la sesión 07 de esta ruta (outbox con PostgreSQL), probado con `OutboxTest`.

**Conclusión.** Conectar Pedidos a Kafka fue escribir un adaptador (92 líneas) sin tocar un byte de dominio ni de aplicación. Cambiar el mecanismo (de Kafka a HTTP en Inventario) fue otro adaptador.
