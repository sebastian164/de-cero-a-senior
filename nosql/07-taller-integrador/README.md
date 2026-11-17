# 07 — Taller integrador: pedidos con MongoDB y Redis

Esta sesión no trae conceptos nuevos. Es el examen de la ruta: entregar tu servicio de pedidos funcionando sobre MongoDB y Redis, someterlo a un simulacro y defender sus decisiones.

## El encargo

Tu servicio de pedidos del [módulo hexagonal](../../hexagonal/), con:

| Pieza | Tecnología | Sesión |
|---|---|---|
| Pedidos, clientes, catálogo y existencias | MongoDB, replica set | 01 a 04 |
| Consulta del catálogo | Caché en Redis delante del puerto de consulta | 05 |
| `POST /pedidos` | Clave de idempotencia y límite de tasa en Redis | 06 |
| Cancelación de pedidos abandonados | Tarea programada en varias instancias, con cerrojo y turno | 06 |
| Eventos de pedido | Bandeja de salida en MongoDB, en la misma transacción | 04 |

## Requisitos

### Funcionales

1. `POST /pedidos` crea un pedido con varias líneas y descuenta existencias. Con la misma `Idempotency-Key`, responde lo mismo sin crear otro.
2. `GET /pedidos/{id}` y `GET /clientes/{id}/pedidos` (paginado por clave) devuelven pedidos y su estado.
3. `GET /productos/{id}` devuelve precio y existencias, desde la caché cuando se puede.
4. Pagar, enviar y cancelar respetan las transiciones de estado. (El servicio del módulo hexagonal tiene `aprobar`, `confirmar` y `cancelar`, sin envío: adapta los nombres a los estados de tu servicio.)
5. Una tarea cancela cada minuto los pedidos `PENDIENTE` de más de 30 minutos y devuelve sus existencias.

### De diseño

| Requisito | Sesión |
|---|---|
| Cada colección y cada documento justificados por una tabla de consultas con volúmenes | 01 |
| Ningún arreglo sin tope | 01 |
| Cada índice justificado por una consulta; las consultas frecuentes examinan lo que devuelven | 02 |
| Cada escritura usa la herramienta más barata que es correcta: operador, condición, versión o transacción | 03 |
| MongoDB y Redis aparecen solo en adaptadores; el dominio y la aplicación no los conocen | 04 a 06 |
| El contrato de cada repositorio pasa contra memoria y contra MongoDB real | 04 |
| La caché decora un puerto de consulta, con caducidad, invalidación tras confirmar, carga única y caché negativa | 05 |
| Toda clave de Redis tiene dueño, versión de formato y caducidad (o una razón escrita para no tenerla) | 05 y 06 |
| La idempotencia del borde y la de la transacción están separadas y cada una sabe qué protege | 06 |
| El cerrojo de la tarea tiene ficha y turno, y la escritura en MongoDB comprueba el turno | 06 |

### De operación

- Todo se levanta con una orden (`docker compose up`), con dos instancias del servicio.
- Si Redis cae, el servicio sigue creando y consultando pedidos (más lento); lo que deja de funcionar está decidido y escrito.
- Métricas: tasa de aciertos de la caché, reintentos por conflicto, peticiones rechazadas por límite, pedidos cancelados por la tarea, eventos pendientes en la bandeja.

## El simulacro

Con un generador de carga creando y consultando pedidos de forma continua, ejecuta cada prueba y completa la tabla:

| Prueba | Qué debe cumplirse |
|---|---|
| Veinte compradores a la vez por un producto con cinco unidades | Exactamente cinco pedidos; existencias en cero, nunca negativas |
| Cada `POST /pedidos` se envía tres veces seguidas con la misma clave | Cero pedidos duplicados, cero cobros duplicados |
| Matar una instancia a mitad de "crear pedido" | Nada a medias: ni pedido sin descuento ni descuento sin pedido |
| Detener Redis dos minutos | El servicio responde; al volver Redis, la caché se recupera sola |
| Vaciar Redis (`FLUSHALL`) en plena carga | Sin estampida contra MongoDB: las lecturas a la base están medidas |
| Cambiar cien precios directamente en MongoDB | El tiempo máximo hasta verlos está medido y coincide con lo que prometiste |
| Pausar (`docker pause`) la instancia que tiene el cerrojo de la tarea más que su vida | La otra instancia toma el relevo; ninguna cancelación se aplica dos veces |
| Un cliente que envía cien pedidos en diez segundos | Recibe `429` según el límite pactado; los demás clientes no lo notan |
| Detener el publicador de la bandeja de salida | Los pedidos se aceptan; sus eventos salen al reiniciarlo |

Después de **cada** prueba, ejecuta un verificador de invariantes que recorra MongoDB y compruebe:

- Las existencias de cada producto son las iniciales menos las líneas de los pedidos no cancelados.
- Ningún pedido está en un estado imposible.
- No hay dos pedidos con la misma clave de idempotencia.
- Cada pedido tiene su evento, entregado o pendiente.

El verificador es parte de la entrega. Un simulacro sin verificador es una demostración, no una prueba.

## La defensa

Un documento de tres páginas para quien vaya a operar el servicio:

1. **El modelo**: la tabla de consultas, los documentos y los índices, con el volumen previsto a tres años.
2. **Tres decisiones discutibles**, con la alternativa que descartaste y por qué. Como mínimo: qué incrustaste, dónde usaste transacción y dónde no, y qué cacheaste.
3. **Qué pasa si se pierde Redis**, uso por uso.
4. **Qué garantiza y qué no**: cuánto tiempo puede ver un cliente un precio viejo; qué pasa si el primario de MongoDB cae con `w: "majority"`.
5. **Guía de guardia**: para cada métrica de la lista de operación, qué valor es una alerta y cuál es el primer paso.
6. **Cuándo no usarías esto**: con qué cambio en los requisitos llevarías los pedidos a PostgreSQL.

## Criterios de aceptación

| Criterio | Cómo se comprueba |
|---|---|
| El sistema levanta desde cero con una orden | En un clon limpio |
| Los contratos pasan contra memoria y contra MongoDB | Salida de las pruebas |
| La tabla del simulacro está completa y el verificador pasa tras cada prueba | Salidas adjuntas |
| La venta de las cinco unidades da exactamente cinco pedidos | Repetida diez veces |
| El dominio y la aplicación no importan nada de `com.mongodb` ni de `io.lettuce` | Prueba de arquitectura |
| La defensa nombra límites reales | Lectura |

## Entrega

Rama `taller/nosql-07`, o un repositorio propio enlazado desde el pull request, con el código, el verificador, los resultados del simulacro y la defensa en `taller/solucion/`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas de cierre

Las que te harán en una entrevista senior cuando digas que has trabajado con MongoDB y Redis:

1. ¿Por qué elegiste MongoDB para los pedidos? ¿Qué consulta te haría arrepentirte?
2. Dibuja tus documentos y dime qué crece y hasta dónde.
3. Enséñame el `explain` de tu consulta más frecuente.
4. ¿Cómo sabes que no vendiste dos veces la última unidad?
5. Redis se reinicia a las tres de la mañana. ¿Qué pasa con la caché, con la idempotencia, con el límite y con la tarea?
6. ¿Qué cuesta operar dos bases de datos más, y cuándo no lo harías?
