# Sagas 07 — La tienda completa: simulacro y defensa

Siete servicios (Spring Boot 4.1.2, Kafka 4.3.1, PostgreSQL 17), **cada uno un hexágono** en su paquete `tienda.integrador.<servicio>` con `dominio`, `aplicacion` y `adaptadores` (`entrada/kafka`, `entrada/http`, `entrada/reloj`, `salida/jdbc`) y su raíz de composición en `configuracion`: **Pedidos** (HTTP + base propia), **Orquestador**, **Pagos**, **Inventario**, **Envíos** y, por coreografía, **Notificaciones** y **Fidelidad**. Un solo artefacto ejecutable (`tienda.integrador.Integrador`, `--tienda.servicio=<nombre>`) y un `compose/compose.yaml` que levanta los siete, Kafka y PostgreSQL con una orden. El orquestador es un servicio de aplicación (`ServicioDeSagas`) con cuatro puertos de salida (`RepositorioDeSagas`, `MensajesProcesados`, `BandejaDeSalida`, `UnidadDeTrabajo`) y sus `Transiciones` y `Estado` son idénticos a los de las sesiones 02 y 05 (salvo la línea `package`). Los participantes (Inventario, Pagos, Envíos) son servicios de aplicación detrás de puertos de entrada (`AtenderOrden`); los `@KafkaListener` solo traducen y llaman a un puerto. El verificador de invariantes es `verificador/Invariantes.java`. Pruebas: `ArquitecturaTest` (5, lee los `import`), `SimulacroTest` (12, siete procesos reales) y `ComposeTest` (2, contra los contenedores), con la salida en `salida/evidencia.txt` (la escriben las pruebas al ejecutarse).

La sesión 07 no reutiliza el código plano de la sesión 05 (paquete plano `tienda`): lo reconstruye con la estructura que pide el taller. Lo que sí se conserva idéntico es la tabla de transiciones.

## 1. Diagrama y recorrido

```
POST /pedidos ──> Pedidos ──[tx: pedidos + salida]──> compra.pedidos ──> Orquestador (grupo orquestador-pedidos)
                                                                              │ tx: sagas + procesados + salida
        compra.ordenes.inventario / .pagos / .envios  <──────────────────────┘
                 │ Inventario, Pagos, Envíos (cada uno con su base y su tabla de salida)
                 └──> compra.respuestas ──> Orquestador ──> compra.resultados ──┬─> Pedidos (CONFIRMADO / CANCELADO)
                                                                                ├─> Notificaciones (avisa al cliente)
                                                                                └─> Fidelidad (+10 puntos si CONFIRMADO)
                  Todo mensaje ilegible o inválido ──> compra.fallidos
```

**Confirmado:** `POST` (201, `PENDIENTE`) → `reservar-inventario` → `cobrar` → `programar-envio` → `SagaCompletada` → Pedidos `CONFIRMADO`, aviso y 10 puntos. **Cancelado por pago rechazado:** reservar → cobrar (rechazado) → `liberar-inventario` → `SagaCancelada(pago rechazado)` → `CANCELADO`, aviso, sin puntos. Los otros dos caminos tristes añaden `reembolsar` (envío imposible) o terminan sin cobro (sin existencias).

Estados reales medidos por `GET /pedidos/{id}` (prueba `caminosFuncionalesYCorrelacion`): `PENDIENTE` justo tras el `POST`; luego `CONFIRMADO`, o `CANCELADO` con motivo `pago rechazado`, `sin existencias` o `envío imposible`.

## 2. Tabla de transiciones de la saga

| Estado | Respuesta | Nuevo estado | Orden |
|---|---|---|---|
| (inicio) | — | RESERVANDO | `reservar-inventario` |
| RESERVANDO | inventario-reservado | COBRANDO | `cobrar` |
| RESERVANDO | sin-existencias | CANCELADA | — |
| COBRANDO | pago-aprobado | PROGRAMANDO_ENVIO | `programar-envio` |
| COBRANDO | pago-rechazado | LIBERANDO | `liberar-inventario` |
| PROGRAMANDO_ENVIO | envio-programado | COMPLETADA | — |
| PROGRAMANDO_ENVIO | envio-imposible | REEMBOLSANDO | `reembolsar` |
| REEMBOLSANDO | pago-reembolsado | LIBERANDO | `liberar-inventario` |
| LIBERANDO | inventario-liberado | CANCELADA | — |
| RESERVANDO (vence) | — | LIBERANDO | `liberar-inventario` (seguro aunque no hubiera reserva) |
| COBRANDO (vence) | — | REEMBOLSANDO | `reembolsar` (seguro: lápida si aún no había cobro) |

Lo que no está en la tabla se ignora. Reservar y cobrar son compensables: tras `envio-imposible` la tabla reembolsa (`REEMBOLSANDO`) y libera. El pivote es programar el envío: una vez `envio-programado`, la saga termina sí o sí (`COMPLETADA`) y no hay compensación. Las compensaciones se reintentan hasta agotar intentos y entonces el caso pasa a revisión humana.

## 3. Tres decisiones discutibles

1. **El orden de los pasos: reservar, cobrar, enviar.** Clasificación: reservar es compensable y barato (liberar); cobrar es compensable pero cuesta y se ve (reembolsar deja rastro y puede tardar días en el banco del cliente); programar el envío es el pivote, el punto sin retorno: va último porque, si ocurre, la saga ya no puede volver atrás y solo queda terminar. Lo que va antes del pivote son los pasos que pueden fallar de forma definitiva (sin existencias, pago rechazado, envío imposible), y cada uno tiene su compensación en orden inverso. Alternativa descartada: cobrar primero. Reduciría el tiempo de existencias bloqueadas, pero cobraríamos y reembolsaríamos a cada cliente sin existencias, que es el caso triste más común en las ventas limitadas (S8: 15 de cada 20). Coherente con `sagas/01` y `sagas/06`, donde el pivote también es el despacho.
2. **Qué orquestar y qué coreografiar.** Lo que tiene compensación y orden (inventario, pago, envío) está orquestado: un solo lugar sabe en qué estado está la compra. Notificaciones y Fidelidad solo reaccionan al resultado; ninguna falla puede cambiar el destino de la compra. Alternativa descartada: Fidelidad como paso de la saga. Pondría una pasarela de puntos en el camino crítico y obligaría a compensarlos.
3. **Aislamiento: contramedida contra la doble venta.** La reserva descuenta con `UPDATE stock SET disponibles = disponibles - n WHERE disponibles >= n` (una fila, atómico, con `CHECK (disponibles >= 0)`): dos compradores no pueden llevarse la última unidad y nada queda en negativo. Alternativa descartada: bloqueo `SELECT FOR UPDATE` del producto durante toda la saga; el aislamiento se compra con una reserva semántica que se libera, no con un cerrojo largo. Medido: cinco unidades, veinte compradores a la vez, diez rondas: exactamente 5 confirmados cada vez.
4. **Un puerto genérico de órdenes y JSON armado a mano.** El orquestador usa un único puerto de salida, `BandejaDeSalida.enviarOrden(destino, Orden)`, en lugar de uno por participante: así `Transiciones` queda idéntico al de las sesiones 02 y 05 y el cambio de destino es un dato, no un tipo. El coste es que el puerto conoce la idea de "destino"; la alternativa (tres puertos `OrdenesDeInventario`, `OrdenesDePagos`, `OrdenesDeEnvios` implementados por el mismo adaptador) es más fiel a "un puerto por participante" y no se hizo. Del contrato de Kafka 05 se cumplen sobre y versión; no hay archivos de esquema para órdenes, respuestas y resultados, y los adaptadores JDBC arman el JSON concatenando cadenas sin escapar: es una deuda conocida, y los datos de la prueba no llevan comillas.

**Una orden, pero tras `mvn package`.** El Dockerfile copia `target/sagas-07-1.0.jar`, así que levantar desde un clon limpio son dos órdenes: `mvn package` y `docker compose up --build`. Una construcción en dos etapas exigiría copiar el código fuente y las dependencias de Maven dentro de la imagen.

## 4. Qué garantiza y qué no

- **Garantiza:** toda saga termina (o queda en revisión con alerta) tras caídas de cualquier servicio, del broker o del publicador; ningún cobro doble; existencias nunca negativas y `libres + reservadas = iniciales`; los efectos se aplican una vez aunque los mensajes se dupliquen.
- **No garantiza:** aislamiento. Un cliente puede ver `PENDIENTE` mientras la saga corre (segundos en condiciones normales, tanto como dure la caída si algo está caído, hasta el vencimiento del paso); una existencia puede estar reservada a una compra que luego se cancela, y otro comprador verá "sin existencias" aunque minutos después vuelva a haber. Tampoco garantiza orden entre temas.
- **Si se pierde una base de datos:** la de Pedidos, el estado visible se reconstruye de `compra.resultados` (con retención suficiente); la del orquestador, las sagas en vuelo se pierden y habría que reconstruirlas de los temas de órdenes y respuestas; la de Pagos pierde la única prueba de qué se cobró: es la que debe tener réplica y copias. Sin copia de seguridad no hay garantía.

## 5. El simulacro (ejecutado)

Cada fila se ejecutó con el verificador después, y el verificador dijo "sin violaciones" cada vez. Resultados de `salida/evidencia.txt` en una ejecución:

| Prueba | Resultado ejecutado |
|---|---|
| Matar el orquestador y reiniciarlo | 20/20 `POST` aceptados con el orquestador muerto; tras reiniciar, 0 pedidos `PENDIENTE` |
| Matar Pagos dos minutos | 120 s reales con 40 pedidos: esperaron, reintentaron, vencieron y se cancelaron (los 40; ninguno cobró), cobros dobles 0 |
| Matar el broker un minuto | `POST /pedidos` respondió 201 en 40 de 40 intentos; 40 eventos esperaron en la tabla de salida de Pedidos; todo terminó al volver |
| Pausar el publicador de Pedidos | `/admin/publicador/detener` (no se mata el proceso): 12/12 aceptados, 12 `PENDIENTE`, 12 filas en la tabla; al reiniciarlo, todos terminan |
| Matar de verdad el proceso de Pedidos | `kill -9` (`destroyForcibly`) con 12 pedidos aceptados y 12 filas pendientes: las 12 filas siguen en la tabla tras la muerte; al arrancar Pedidos de nuevo salen y los 12 pedidos terminan (`S4b`). Lo mismo con `docker kill` del contenedor en el compose (`C2`) |
| Matar el publicador en el peor momento | el orquestador muere (`halt`, código 137) justo DESPUÉS de que Kafka confirme la 3.ª fila y ANTES de marcarla; al reiniciar la republica: en el tema hay más órdenes que ids distintos (duplicados), y aun así cobros dobles 0 y cada cobro vigente corresponde a un pedido confirmado (`S4c`) |
| Todo con una orden de Compose | `docker compose up --build`: siete contenedores, Kafka y PostgreSQL; 20 pedidos de los cuatro caminos (5 confirmados, 15 cancelados) y el verificador sin violaciones (`C1`) |
| Duplicar a mano cien mensajes | 120 copias exactas en 6 temas: cobros, reservas, despachos, puntos, notificaciones y versiones de las sagas, sin cambio |
| Un mensaje ilegible en cada tema | 8 registros en `compra.fallidos` (6 temas; `compra.resultados` lo leen 3 grupos); los 16 pedidos terminaron |
| Dos instancias de cada servicio | 14 procesos, 60 pedidos: 15 confirmados (uno de cada cuatro), 60 notificaciones, 15 registros de puntos |
| Cinco unidades a veinte compradores | diez rondas con diez productos: `5 5 5 5 5 5 5 5 5 5` confirmados |
| La compensación de Inventario falla siempre | tras 3 intentos los 4 casos en revisión: `casos_en_revision 4`; el pedido sigue `PENDIENTE` (honesto: la reserva no se liberó) |

El **verificador** (`verificador/Invariantes.java`) comprueba: confirmado = exactamente un cobro, una reserva y un despacho; cancelado = ningún cobro vigente, ninguna reserva ni despacho; ningún `PENDIENTE` por encima de un umbral; sin puntos para pedidos no confirmados; existencias nunca negativas y `libres + reservadas = iniciales`.

Correlación: una cabecera `X-Correlacion: CORR-777` aparece como `correlacion=CORR-777` en el registro de los siete servicios.

## 6. Guía de guardia

| Alerta | Qué significa | Primer paso |
|---|---|---|
| `sagas_vencidas > 0` 5 min | un participante está caído o muy lento | `GET /sagas/no-terminadas`; mirar el retraso del grupo del participante y su proceso |
| `casos_en_revision > 0` | una compensación agotó los intentos | abrir la saga, leer el motivo, arreglar la causa y reiniciar la compensación a mano; hoy |
| `filas_pendientes_salida` creciendo | el publicador o el broker no publican | comprobar el broker; el publicador reintenta solo; no tocar las filas |
| mensajes en `compra.fallidos` | evento ilegible o inválido | leer motivo y origen (tema, partición, posición), corregir al productor y republicar |
| `pedidos_pendientes` creciendo | la saga no avanza | seguir la correlación del pedido en los registros |

## 7. Con cuarenta sagas en lugar de una

La tabla de sagas, el vigilante de vencimientos, los reintentos, la bandeja de salida y la máquina de estados están escritos una vez, a mano. Repetirlos cuarenta veces es mantenerlos cuarenta veces. Con cuarenta sagas adoptaría un motor de flujos duradero (Temporal u otro) o, al menos, extraería el esqueleto a una biblioteca propia, con la definición declarativa de cada saga y un panel común de sagas atascadas. Se quedaría igual: la bandeja de salida, los consumidores idempotentes y el verificador de invariantes.
