# Taller 07-04 — Spring Data JPA a fondo (solución)

Spring Boot 4.1.1, Spring Data JPA 4.1.1, Hibernate 7.4.5.Final, PostgreSQL 17 (levantado con el `docker-compose.yml` de esta carpeta, puerto 5432 por defecto). `mvn test` ejecuta 13 pruebas. Las consultas se cuentan con un `StatementInspector` (`RegistroSql`) registrado por propiedad, además de `spring.jpa.show-sql=true`.

Datos: 50 pedidos, cada uno de un cliente distinto, con 2 líneas. 40 están `PENDIENTE`.

## 1. N+1 y tres correcciones

| Variante | Consultas para 40 pedidos con su cliente | Prueba |
|---|---|---|
| Ingenua (`findByEstadoOrderById` y recorrer `getCliente()`) | **41** (1 + N) | `ingenuoHace1MasNConsultas` |
| `JOIN FETCH` (la consulta del README) | **1** | `joinFetchHaceUnaConsulta` |
| `@EntityGraph(attributePaths = "cliente")` | **1** | `entityGraphHaceUnaConsulta` |
| Carga por lotes (`hibernate.default_batch_fetch_size=25`), misma consulta ingenua | **3** (1 + ⌈40/25⌉) | `CargaPorLotesTest` |

`JOIN FETCH` y `@EntityGraph` generan el mismo SQL (un `join cliente`). La carga por lotes no cambia la consulta: es la red de seguridad global para asociaciones que nadie pidió explícitamente.

## 2. `open-in-view=false`

El endpoint `/antes/pedidos/{id}` toca `pedido.getCliente().getNombre()` en el controlador. Con OSIV apagado lanza `LazyInitializationException: Could not initialize proxy [jpa.Cliente#…] - no session`. Arreglo: el caso de uso `ConsultaPedidos.detalle` (`@Transactional(readOnly = true)` + `@EntityGraph`) arma la respuesta dentro de la transacción; `/pedidos/{id}` responde 200. Con OSIV encendido el endpoint roto "funciona", pero hace 2 consultas, la segunda disparada desde la capa web: eso es lo que OSIV esconde.

## 3. Proyección a `record`

```
proyección: select p1_0.id,c1_0.nombre,p1_0.total from pedido p1_0 join cliente c1_0 on c1_0.id=p1_0.cliente_id where p1_0.fecha>=?
entidades:  select p1_0.id,p1_0.cliente_id,p1_0.estado,p1_0.fecha,p1_0.total from pedido p1_0 where p1_0.fecha>=?   (+10 consultas de cliente)
```
La proyección hace 1 consulta con 3 columnas y no deja entidades gestionadas (sin *dirty checking* al confirmar). Con entidades: todas las columnas y 11 consultas para mostrar lo mismo.

## 4. `Specification` con cuatro criterios opcionales

`PedidoFiltros.con(cliente, estado, totalMinimo, desde)`: cada criterio nulo devuelve `null` y `Specification.allOf` lo ignora. Probado con ningún criterio (50), uno solo, dos y los cuatro combinados.

## 5. Paginación en memoria con `JOIN FETCH`

Hallazgo: con Hibernate 7.4 `select p from Pedido p join fetch p.lineas order by p.id` + `Pageable` **ya no pagina en memoria**: Hibernate reescribe la consulta como una subconsulta paginada sobre `pedido` (`from (select … offset ? rows fetch first ? rows only) … join linea_pedido`). Para provocar el aviso hay que impedirle esa reescritura, por ejemplo ordenando primero por la colección (`order by l.producto, p.id`): entonces el SQL sale sin `offset`/`limit`, trae las 100 filas y escribe `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory`.

Solución que funciona en cualquier versión: paginar identificadores (`select p.id … order by p.id` con `Pageable`, que sí lleva `offset`) y luego cargar el detalle de esa página con `join fetch … where p.id in :ids`. Sin aviso.

Se corrigió el README (errores comunes y paso 5 del taller) para reflejar el comportamiento de Hibernate 7.4.

## 6. Detección de cambios

Dentro de una transacción, `em.find` + `setTotal(999_999)` sin `save`: al confirmar Hibernate emite exactamente **1 `UPDATE`** y el valor queda guardado. Con la entidad separada (cargada fuera de transacción) el mismo cambio no genera ningún `UPDATE`.
