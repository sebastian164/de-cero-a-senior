# Kafka 06 — Inventario con Spring Boot: política de errores y pruebas

Servicio en `src/main/java/inventario/` (hexágono: dominio, aplicación y adaptadores `entrada`/`salida`; Spring Boot 4.1.2, Spring for Apache Kafka, PostgreSQL 17). Pruebas: `ReservarInventarioTest` (3 unitarias, sin Spring ni Kafka), `InventarioIntegracionTest` (9, contra el broker y la base de `compose.pruebas.yaml`, porque necesitan detener la base y medir tiempos sobre una infraestructura conocida) y `ContenedoresTest` (2, el paso 9 tal como lo pide el enunciado: Testcontainers 2.0.5 con `apache/kafka:4.3.1` y `postgres:17`, un evento que se reserva y uno inválido que va a fallidos mientras el siguiente se procesa). Mediciones en `salida/evidencia.txt` (la escriben las pruebas al ejecutarse).

| Paso | Resultado |
|---|---|
| 1 | `producto-1: 1000 -> 997`; se publicaron `InventarioReservado` e `InventarioInsuficiente`; el producto sin tocar siguió en 5 |
| 3. Idempotencia | el mismo evento tres veces: un solo descuento de 3 y un registro de evento procesado |
| 4/5a. Formato inválido | directo a fallidos en milisegundos, sin reintentos, con cabeceras `kafka_dlt-*` (excepción, tema, partición y posición de origen); el siguiente mensaje se procesó |
| 5b. Base bloqueada 15 s | los 5 eventos se reintentaron con espera creciente y al volver se aplicaron todos; fallidos: solo el inválido anterior |
| 5c. `NullPointerException` | se reintenta (2 pedidos x (1 + reintentos) = 18 ejecuciones) y luego va a fallidos |
| 6. `catch` que no relanza | 8 mensajes publicados; se aplicó solo el bueno, los 5 de la base caída se **perdieron**, fallidos 0 y retraso 0: todo en verde |
| 7. `concurrency` | 6 hilos con 3 particiones: tres ociosos. 300 eventos de 20 ms: 1 hilo 9,6 s; 3 hilos 3,3 s; 6 hilos 3,4 s: más hilos que particiones no ayudan |
| 8. Reintentos no bloqueantes | con la misma clave y el primero fallando: bloqueantes `[a1, a2]`, no bloqueantes `[a2, a1]`: **cambia el orden**. Sería un error grave si a1 es `PedidoCreado` y a2 `PedidoCancelado` de la misma tienda: se aplicaría la cancelación antes de la creación |
| 9. Integración | el evento publicado se refleja en la reserva; el que incumple el contrato llega a fallidos y el siguiente se procesa |
| 10. Apagado ordenado | despliegue a mitad de 300 eventos: reprocesados 0, descuentos efectivos 300 de 300 |
| 11. Métrica | `kafka.consumer.group.lag{group=inventario}` = 40 con el servicio apagado y 40 eventos publicados; 0 al volver. La métrica la expone el propio servicio (bean `RetrasoDelGrupo`, con referencia fuerte en el `Gauge`: con referencia débil el recolector la anulaba) |

**Qué excepciones van a cada grupo (paso 4).** `EventoInvalido` (formato o contrato roto) es **no reintentable**: ningún reintento lo arregla, va directo a fallidos. Todo lo demás (base caída, bloqueos, errores inesperados) se reintenta con espera exponencial (500 ms, x2, tope de 4 s, ocho reintentos: `ExponentialBackOffWithMaxRetries(8)`) y, si se agota, acaba en fallidos con la excepción en las cabeceras.

**Alerta del retraso (paso 11).** Con un retraso sostenido por encima de lo que tarda el grupo en procesar un minuto de tráfico normal durante más de 5 minutos (para esta prueba: más de 100 mensajes durante 5 minutos); el valor real sale de medir el rendimiento de tu servicio.
