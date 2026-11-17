# Taller 03 — Consistencia en MongoDB

Código: `src/test/java/t03/ConsistenciaTest.java` y `ejecutar-replicaset3.sh` (paso 6 opcional). Lo medido queda en `salida/resultados.txt` y `salida/replicaset3.txt` al ejecutarlos.

## Qué herramienta para cada operación (la más barata que es correcta)

| Operación | Herramienta | Por qué no una más barata | Por qué no una más cara |
|---|---|---|---|
| Descontar existencias | **Actualización condicional** `updateOne({_id, existencias: {$gte: 1}}, {$inc: -1})` | Leer y escribir por separado vende de más (paso 1) | Un solo documento: el servidor ya lo hace atómico; una transacción cuesta 1,2 veces más en media y 1,5 en la mediana (paso 5) |
| Cambiar el estado del pedido | **Versión** (bloqueo optimista): el filtro incluye `version` y `$inc: {version: 1}` | La regla de transición se decide en Java con lo leído: sin versión, dos escrituras pasan (paso 2) | No toca más que un documento y los conflictos son raros: reintentar es barato |
| Crear pedido y descontar tres productos | **Transacción** multidocumento | Cuatro documentos que tienen que cambiar juntos: ningún operador atómico lo cubre (paso 3) | Es el único caso que la necesita |

## Paso 1. Existencias (5 unidades, 20 compradores, diez veces)

* Lectura y escritura separadas: **sobreventa en las 10 repeticiones** (20 vendidas de 5, existencias finales 4).
* Actualización condicional: **10 de 10 repeticiones con exactamente 5 vendidas y existencias 0**.

## Paso 2. Estado del pedido (300 intentos con "pagar" y "cancelar" a la vez)

* Sin versión: en **300 de 300** intentos las dos operaciones respondieron "hecho". Historial resultante, por ejemplo: `[CANCELADO, PAGADO]` con estado final `PAGADO`.
* Con versión: **0 de 300** con las dos en "hecho", 0 estados incoherentes (una sola transición en el historial, versión 1). La que pierde vuelve a leer, ve el estado nuevo y la regla de Java la rechaza. La cadena legal PENDIENTE → PAGADO → ENVIADO funciona y CANCELADO desde ENVIADO se rechaza.

## Paso 3. Crear pedido en una transacción

* Sin transacción, tercer producto sin existencias: A=9, B=9, C=0 y ningún pedido (descuentos sin pedido).
* Con transacción: A=10, B=10, C=0, pedidos=0. Con fallo inyectado tras el tercer descuento: A=10, B=10, C=10, pedidos=0. El camino feliz confirma: A=9, B=9, C=9, un pedido.

## Paso 4. 200 creaciones concurrentes sobre dos productos (120 unidades de cada uno), tres ejecuciones

| Ejecución | Terminaron | Rechazadas por existencias | Veces que se ejecutó el cuerpo | Repeticiones por conflicto |
|---|---|---|---|---|
| 1 | 120 | 80 | 1.702 | 1.502 |
| 2 | 120 | 80 | 1.684 | 1.484 |
| 3 | 120 | 80 | 1.701 | 1.501 |

Siempre 120 pedidos y existencias 0: nunca se vende de más. El coste de que los 200 choquen en dos documentos es de unas **7,5 repeticiones por petición**: `withTransaction` reintenta sin espera. Por eso la sesión 04 mide también la política de reintento.

## Paso 5. Coste (3.000 operaciones tras 500 de calentamiento)

| | Media y mediana | p95 |
|---|---|---|
| Actualización condicional | media 595 us, mediana 354 us | 1.459 us |
| Transacción de un solo documento | media 699 us, mediana 517 us | 1.571 us |

Las dos variantes se alternan en el mismo bucle para que el ruido de la máquina las afecte por igual. La transacción cuesta **1,2 veces más en media y 1,5 veces más en la mediana** cuando no hace falta (en otras ejecuciones, 1,2 a 1,4); lo que cambia entre ejecuciones son los microsegundos, no el orden.

## Paso 6 (opcional). Replica set de tres nodos

`ejecutar-replicaset3.sh` levanta tres `mongo:9.0.8` (`rs3`, prioridad 2 para `mongo1`), **aísla de la red** a los dos secundarios (un `docker pause` no sirve: el sistema operativo sigue recibiendo en el socket lo que el primario envía y el secundario lo aplica al despertar; se comprobó) y mata el primario.

| Caso | Resultado |
|---|---|
| A. `w:1`, secundarios sin red, el primario cae | La escritura **se confirma** (`acknowledged: true`); tras la elección el nuevo primario **no la tiene**; el antiguo primario, al volver, la deshace (rollback). Confirmada y perdida |
| B. `w:"majority"` con `wtimeout`, secundarios sin red, el primario cae | El cliente recibe `WriteConcernTimeout`; el documento no está en el nuevo primario. Se le dijo al cliente que no estaba confirmada, y no está: coherente |
| C. `w:"majority"`, tres nodos sanos, el primario cae | Confirmada y **sobrevive** en el nuevo primario |

## Criterios de aceptación

| Criterio | Dónde |
|---|---|
| Nunca se venden más existencias de las que hay | Paso 1: 10 de 10 |
| Un pedido nunca termina en un estado imposible | Paso 2 |
| Un fallo a mitad de "crear pedido" no deja nada | Paso 3 |
| Cada operación usa la herramienta más barata que es correcta, y está escrito por qué | Tabla de arriba |
| Los reintentos por conflicto están medidos | Paso 4 |
