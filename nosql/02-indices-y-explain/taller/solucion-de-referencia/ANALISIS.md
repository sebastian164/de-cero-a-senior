# Taller 02 — Índices y explain

Código: `src/test/java/t02/IndicesTest.java`. Lo medido queda en `salida/resultados.txt` al ejecutar las pruebas. Datos: 300.000 pedidos, 20.000 clientes, el cliente 1 con 12.000 pedidos (fechas con empates a propósito).

## Pasos 1 y 3. `explain("executionStats")` sin índices y con índices

| Consulta (patrón de acceso) | Sin índices | Con índices |
|---|---|---|
| Q1 mis pedidos, primera página (`clienteId`, orden `fecha`, `_id` desc, límite 20) | COLLSCAN + SORT: 300.000 documentos examinados, 20 devueltos | IXSCAN `I1`: 20 claves, 20 documentos, 20 devueltos (1,00) |
| Q2 PENDIENTE de más de 24 h, límite 100 | COLLSCAN + SORT: 300.000 / 100 | IXSCAN `I2`: 100 claves, 100 documentos, 100 devueltos (1,00) |
| Q3 pedido por `_id` | EXPRESS_IXSCAN `_id`: 1 / 1 | igual (1,00) |
| Q4 pagados de un cliente de más de 200 euros, por fecha, límite 20 | COLLSCAN + SORT: 300.000 / 20 | IXSCAN `I3`: 35 claves, 20 documentos, 20 devueltos (1,75) |

Q4 es la única con una proporción distinta de 1: 35 claves examinadas para 20 devueltos (1,75, menor que 2). Se examinan claves, no documentos: el rango de `total` está en el propio índice (último campo, regla ESR), así que las claves que no cumplen el rango se descartan sin leer el documento. Forzando `I1` (sin `total` en el índice) la misma consulta lee **162 documentos para devolver 20**.

## Paso 2. Índices y la consulta que justifica cada uno

| Índice | Consulta | Razón ESR |
|---|---|---|
| `I1 {clienteId:1, fecha:-1, _id:-1}` | Q1 y la paginación del paso 4 | Igualdad en `clienteId`; el orden es `fecha, _id` (el `_id` es el desempate) |
| `I2 {estado:1, fecha:1}` | Q2 | Igualdad en `estado`; el rango y el orden comparten `fecha` |
| `I3 {clienteId:1, estado:1, fecha:-1, total:1}` | Q4 | Igualdad en `clienteId` y `estado`; orden por `fecha`; rango de `total` al final |
| `I4 {total:1}` | "por si acaso hay un informe por importe" | Sin consulta que lo justifique: **se elimina en el paso 6** |

## Paso 4. Paginación por clave con desempate `(fecha, _id)`

* Página 1: 20 claves y 20 documentos. Página 500 (tras recorrer las 499 anteriores): 20 claves y 20 documentos. Mismo coste. Con `skip(499*20)`: **10.000 claves examinadas** para devolver 20 (17 ms frente a 0 ms).
* Estabilidad: recorrido completo de los 12.000 pedidos del cliente (615 páginas) mientras otro hilo insertaba 1.807 pedidos del mismo cliente (al principio del orden, en medio y con la misma fecha que pedidos existentes): **12.285 pedidos vistos, 0 repetidos, 0 de los 12.000 existentes perdidos**. Los 285 restantes son pedidos nuevos que quedaron por detrás del cursor, legítimamente.
* La alternativa `skip/limit` con el mismo escritor: 14.020 pedidos vistos, **3.254 repetidos**.

## Paso 5. Coste de escribir (100.000 pedidos, mejor de tres ejecuciones)

| Índices (además de `_id`) | Tiempo | Pedidos/s |
|---|---|---|
| 1 | 662 ms | 151.057 |
| 3 | 853 ms | 117.233 |
| 6 (uno multiclave sobre `lineas.productoId`) | 1.636 ms | 61.125 |

Pasar de 1 a 6 índices multiplica el tiempo por 2,5. Es una medida de esta máquina con MongoDB en Docker; lo que se conserva es la proporción.

## Paso 6. `$indexStats`

Tras 20 pasadas de las cuatro consultas: `total_1` 0 accesos; `clienteId_1_fecha_-1__id_-1` 1.840; `estado_1_fecha_1` 20; `clienteId_1_estado_1_fecha_-1_total_1` 20. `total_1` se **elimina**.

## Paso 7. Una consulta que ningún índice resuelve bien

Con un índice sobre `clientes.email`: `^cliente4999@` (anclada) examina 2 claves; `.*cliente4999@` examina **20.000 claves** (todo el índice) para devolver 1. Una expresión que empieza por `.*` no tiene prefijo por el que acotar el rango del índice: el índice se recorre entero (IXSCAN, pero equivalente a un COLLSCAN de claves).

## Paso 8. Consulta cubierta

`find({clienteId:1}, {_id:0, clienteId:1, fecha:1}).sort({fecha:-1}).limit(100)`: etapas `LIMIT, PROJECTION_COVERED, IXSCAN(I1)`, `totalDocsExamined: 0`, 100 claves.

## Criterios de aceptación

| Criterio | Dónde |
|---|---|
| Cada índice está justificado por una consulta | Tabla del paso 2 |
| Las consultas frecuentes examinan lo que devuelven, o hay razón escrita | Paso 3 (Q4 explicada) |
| La paginación es estable y de coste constante | Paso 4 |
| El coste de escritura está medido | Paso 5 |
| Ningún índice sobra sin justificación | Paso 6 |
