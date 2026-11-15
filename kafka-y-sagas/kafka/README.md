# Kafka

Siete talleres, en orden. Requisito: el [entorno](../entorno/) levantado.

| # | Sesión | Taller |
|---|---|---|
| 01 | [El registro de eventos](01-el-registro-de-eventos/) | Particiones, claves y retención, desde la consola |
| 02 | [Productores](02-productores/) | Tumbar brokers y contar qué se pierde con cada configuración |
| 03 | [Consumidores y grupos](03-consumidores-y-grupos/) | Provocar una pérdida y una repetición, y medir el retraso |
| 04 | [Garantías de entrega e idempotencia](04-entrega-e-idempotencia/) | Un consumidor con saldo exacto pese a todo |
| 05 | [Eventos como contrato](05-eventos-como-contrato/) | Los eventos de la tienda y su evolución sin romper |
| 06 | [Kafka con Spring Boot](06-kafka-con-spring-boot/) | El servicio de Inventario con política de errores |
| 07 | [Outbox transaccional](07-outbox-transaccional/) | Reproducir la doble escritura y eliminarla |

Las sesiones 03, 04 y 07 traen un ejemplo en Java puro que se ejecuta con `java Main.java`. El resto del código es ilustrativo. El detalle está en el [índice de la ruta](../README.md#estado-del-código).

Sigue con las [sagas](../sagas/).
