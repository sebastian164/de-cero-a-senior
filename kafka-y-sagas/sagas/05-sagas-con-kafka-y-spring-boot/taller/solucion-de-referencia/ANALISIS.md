# Sagas 05 — La saga de compra sobre Kafka, PostgreSQL y Spring Boot

Cuatro servicios (orquestador, Pagos, Inventario y Envíos) que son **procesos reales** de una misma aplicación Spring Boot 4.1.2 (`tienda.Aplicacion`, se elige el rol con `--tienda.servicio=...`), cada uno con su esquema de PostgreSQL (`s05_<servicio>`), Kafka 4.3.1 y las pruebas en `SagasTest` (10) y `TransicionesTest` (2). Lo que midió cada paso queda en `salida/evidencia.txt`, que las pruebas escriben al ejecutarse.

## 1. Temas, claves y sobre (paso 1)

| Tema | Quién publica | Quién lee | Clave |
|---|---|---|---|
| `compra.ordenes.inventario`, `.pagos`, `.envios` | Orquestador | el participante | id de la saga |
| `compra.respuestas` | los tres participantes | Orquestador | id de la saga |
| `compra.resultados` | Orquestador (fin de la saga) | quien quiera (sesión 07) | id de la saga |
| `compra.fallidos` | los manejadores de errores | operación | la clave original |

Tres particiones por tema. Con la saga como clave, todas las órdenes y respuestas de una saga viajan por una partición y se leen en orden y, mientras no haya reequilibrio, por la misma instancia. Entre temas no hay orden, y por eso siguen las lápidas de la sesión 04. Sobre: `id`, `tipo`, `saga`, `correlacion` y los datos.

## 2. Lógica pura (paso 2)

`Transiciones` es una función `(estado, respuesta) -> paso` sin Spring ni Kafka. `TransicionesTest` la prueba sin infraestructura; los mismos casos de las sesiones 02 y 04 (cuatro caminos, respuestas repetidas y fuera de lugar se ignoran) pasan sin cambios. Criterio "las pruebas de la lógica no necesitan infraestructura": cumplido.

## 3 y 4. Orquestador y participantes (pasos 3 y 4)

Orquestador: tabla `sagas` (con `version`), `procesados` y `salida`. Cada respuesta es **una transacción local**: se comprueba `procesados`, se cambia el estado con `UPDATE ... WHERE version = ?` y se deja la orden siguiente en `salida`. El `Salida` publica con `acks=all` e idempotencia y conserva el orden por clave (no publica una fila si hay otra anterior sin publicar con la misma clave).

Participantes: el patrón común está en `Participante.atender`: en una sola transacción, (1) ¿ya vi la orden? si sí, repito la respuesta guardada; (2) efecto con su propia idempotencia (`cobros.saga_id` es clave primaria: **clave de idempotencia del cobro**; `reservas.saga_id`, `despachos.saga_id`); (3) respuesta en `salida`. Pagos simula la pasarela: el importe 666 se rechaza.

## 5. Los cuatro caminos con el estado en las bases

```
feliz            COMPLETADA  cobros vigentes 1, reservas vigentes 1, despachos 1
pago-rechazado   CANCELADA   0, 0, 0   (motivo: pago rechazado)
sin-existencias  CANCELADA   0, 0, 0   (motivo: sin existencias)
envio-imposible  CANCELADA   0, 0, 0   (motivo: envío imposible)
```

## 6. Caídas (`kill -9` con 24 sagas en vuelo; criterio "toda saga termina")

| Quién cae | Resultado ejecutado |
|---|---|
| Orquestador | 0 sagas no terminadas tras reiniciar; violaciones de la invariante: ninguna |
| Pagos | 0 no terminadas; cobros 12 (uno por saga como máximo) |
| Pagos, justo tras procesar y antes de confirmar la posición | la orden se reentrega, se reconoce y no cobra otra vez: 12 cobros |
| Publicador | dos pruebas: apagado (`Salida.activa`: las filas esperan en la tabla, sesión 07) y MUERTE en el peor momento (`paso6_muereElPublicadorEntrePublicarYMarcar`: el orquestador hace `halt` tras publicar la 3.ª fila y antes de marcarla; al reiniciar la republica; hay órdenes duplicadas en los temas y aun así las 8 sagas terminan, 8 cobros, 0 dobles, suma de versiones 24) |
| Broker (`docker stop`) | 24 órdenes esperaron en la tabla de salida; al volver, todas terminaron |

Por qué nada se pierde: el estado y la salida están en la misma transacción de la base; Kafka solo recibe lo que ya es duradero; las posiciones se confirman después de procesar, y lo reentregado es inofensivo por las claves de idempotencia. **Ningún cobro doble** (criterio): se cuenta en la base de Pagos y es como máximo uno por saga.

## 7. Dos instancias del orquestador

40 sagas con dos instancias del grupo: terminan todas y la versión máxima de una saga fue 5, dentro de las 3 a 6 transiciones del camino más largo: ninguna transición se aplicó dos veces. **Conflicto forzado:** dos instancias leen la misma versión y reciben dos respuestas *distintas* a la vez; solo una escribe, la otra detecta `UPDATE` con 0 filas (1 conflicto contado), **relee** y encuentra la respuesta ya fuera de lugar para el estado nuevo, así que la ignora. Se emitió una sola orden de cobro.

## 8. Tiempo límite (Inventario detenido)

Las órdenes de reservar esperan en el tema. El orquestador reintenta, vence (`sin respuesta a tiempo`) y compensa con `liberar-inventario` (seguro aunque no haya reserva: deja una lápida). Cuando Inventario vuelve, lee del tema primero las reservas acumuladas y después las liberaciones: las reservas llegan a una saga ya cancelada o con lápida y no prosperan. Resultado: 6 sagas `CANCELADA`, 0 reservas vigentes, existencias 100 de 100.

## 9. Mensaje defectuoso

Seis respuestas ilegibles publicadas a mano (tres particiones) en `compra.respuestas`: las seis acabaron en `compra.fallidos`, las 12 sagas de verdad terminaron. El manejador manda directo a fallidos lo que no se puede leer (no se reintenta) y reintenta lo pasajero.

## 10. Pruebas de integración

El paso 10 pide Testcontainers y se hizo así: `ContenedoresTest` crea su propio Kafka (`apache/kafka:4.3.1`) y su propio PostgreSQL 17 con Testcontainers 2.0.5, arranca los cuatro servicios como procesos contra ellos y comprueba un camino feliz (COMPLETADA, un cobro vigente) y uno de compensación (envío imposible: reserva, cobro, reembolso, liberación; CANCELADA). Los pasos 6 y 7 (matar de verdad, dos orquestadores) usan el broker y la base de Compose y **procesos** en vez de contexto en el mismo JVM, porque exigen matar el proceso.

## 11. Observabilidad y dos alertas

`GET /metricas` del orquestador (texto estilo Prometheus) y `GET /sagas/no-terminadas`:

```
sagas_por_estado{estado="..."}   sagas_vencidas   casos_en_revision   filas_pendientes_salida{servicio="orquestador"}
```

Con los servicios detenidos (prueba `paso11`): `{RESERVANDO=5}`, 5 vencidas, 0 en revisión, 5 filas en la tabla de salida.

**Alerta 1:** `sagas_vencidas > 0` durante 5 minutos (un participante está caído o lento: primer paso, mirar el retraso del grupo del participante). **Alerta 2:** `casos_en_revision > 0` (una compensación agotó sus intentos: intervención humana, hoy mismo).

## 12. Qué no hace este diseño

- Un mensaje de `compra.fallidos` no se reprocesa solo.
- La tabla `salida` no se limpia: en producción hace falta una retención.
- La publicación puede duplicar un mensaje (se publicó pero no se marcó): se resuelve con consumidores idempotentes, no con el publicador.
