# 02 — Índices y planes de ejecución

## Idea

Para encontrar un tema en un libro de 800 páginas no lees todo: vas al índice del final, que está ordenado alfabéticamente y te dice la página. Un índice de base de datos es eso mismo. Y como en el libro, mantenerlo cuesta: cada página nueva obliga a actualizarlo.

## Lo esencial

- Sin índice, la base recorre toda la tabla (**recorrido secuencial**). Con un millón de filas, eso es la diferencia entre milisegundos y segundos.
- El índice por defecto es un **árbol B**: sirve para igualdad, rangos, ordenamiento y prefijos (`LIKE 'abc%'`).
- **Costo:** cada índice ocupa espacio y hace más lentos `INSERT`, `UPDATE` y `DELETE`.

### Tipos y variantes

| Índice | Uso |
|---|---|
| Simple | Una columna muy filtrada |
| Compuesto | Varias columnas que se filtran juntas. **El orden importa** |
| Único | Garantiza unicidad |
| Parcial | Solo un subconjunto de filas (`WHERE estado = 'PENDIENTE'`) |
| De cobertura | Incluye todas las columnas que la consulta necesita; no hay que ir a la tabla |
| De expresión | Sobre una función (`lower(correo)`) |
| GIN, GiST, hash | Texto completo, JSON, geoespacial |

### Regla del índice compuesto

Un índice sobre `(cliente_id, fecha)` sirve para filtrar por `cliente_id` y por `cliente_id + fecha`. **No** sirve para filtrar solo por `fecha`. Coloca primero las columnas de igualdad y después las de rango u orden.

### Qué impide usar un índice

- Aplicar una función a la columna: `WHERE lower(correo) = …` sin índice de expresión.
- Comodín al inicio: `LIKE '%texto'`.
- Conversión implícita de tipos.
- Baja selectividad: un índice sobre una columna con dos valores casi nunca ayuda.
- Estadísticas desactualizadas.

### Leer un plan

`EXPLAIN` muestra el plan estimado; `EXPLAIN ANALYZE` lo ejecuta y muestra tiempos reales. Busca:

- `Seq Scan` sobre tablas grandes con filtros selectivos.
- Diferencias grandes entre filas estimadas y reales.
- Ordenamientos y agrupaciones que se van a disco.
- Bucles anidados sobre muchas filas.

## Ejemplo

```sql
EXPLAIN ANALYZE
SELECT id, total FROM pedidos
WHERE cliente_id = 42 AND estado = 'PENDIENTE'
ORDER BY creado_en DESC
LIMIT 20;
-- Limit -> Sort (top-N heapsort) -> Gather
--   -> Parallel Seq Scan on pedidos  (actual time=1.016..17.076 rows=33.33 loops=3)
--        Rows Removed by Filter: 666633      ← recorre los 2 millones de filas
-- Execution Time: 27.433 ms

CREATE INDEX idx_pedidos_cliente_pendientes
    ON pedidos (cliente_id, creado_en DESC)
    WHERE estado = 'PENDIENTE';
-- Index Scan using idx_pedidos_cliente_pendientes  (actual time=0.030..0.126 rows=20.00 loops=1)
--   Index Cond: (cliente_id = 42)                    ← sin Sort: el índice ya viene ordenado
-- Execution Time: 0.135 ms
```

Los planes son los de PostgreSQL 18.7 con 2 millones de filas, 100.000 clientes y un 2 % de pedidos pendientes; los milisegundos dependen de tu máquina. El índice es compuesto (igualdad primero, orden después) y parcial (solo los pendientes, que son pocos): pequeño y exacto para esta consulta.

## Errores comunes

- Indexar todo "por si acaso".
- No indexar las claves foráneas (PostgreSQL no lo hace automáticamente).
- Índices compuestos con el orden equivocado.
- Índices duplicados o que nadie usa.
- Optimizar sin medir con datos de volumen real.
- `SELECT *` que impide los recorridos de solo índice.
- Paginación con `OFFSET` grande: la base lee y descarta todas las filas anteriores. Usa paginación por cursor (`WHERE id > :ultimo`).
- Crear índices en producción bloqueando la tabla (en PostgreSQL: `CREATE INDEX CONCURRENTLY`).

## Taller

1. Carga una tabla con al menos un millón de filas generadas.
2. Escribe cinco consultas típicas, obtén su plan y mide el tiempo.
3. Diseña los índices mínimos que las aceleren. Justifica el orden de las columnas en los compuestos.
4. Mide el costo: compara el tiempo de insertar 100.000 filas con y sin tus índices.
5. Demuestra un caso donde un índice existe y no se usa, y explica por qué.
6. Compara paginación con `OFFSET 900000` y por cursor.
7. Activa el registro de SQL en una aplicación Spring y encuentra una consulta de JPA sin índice adecuado.

Rama `taller/n8-02-indices`.

## Preguntas senior

1. ¿Cómo decides qué índices crear?
2. ¿Por qué importa el orden en un índice compuesto?
3. ¿Qué es un índice de cobertura y uno parcial?
4. Una consulta rápida se volvió lenta sin cambios de código. ¿Qué revisas?
5. ¿Qué costo tienen los índices y cómo detectas los que sobran?
6. Paginación por desplazamiento frente a paginación por cursor.
