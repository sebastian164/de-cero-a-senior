# Solución — n8-02 Índices y planes de ejecución

Ejecutado con PostgreSQL 18.7 en Docker (puerto 43432), con la configuración por defecto de la imagen. `IndicesTest` y `ConsultaJpaSinIndiceTest` hacen cada paso, comprueban el plan con `EXPLAIN (ANALYZE, BUFFERS)` y escriben las cifras en `target/mediciones.md`. Cada consulta se mide tres veces y se toma la más rápida (caché caliente). Las cifras de abajo son de una corrida real del 2026-11-15 y varían algo entre corridas; las aserciones solo comprueban relaciones (qué nodo usa el plan, cuál es más rápido), no valores absolutos.

## 1. Carga

`src/main/resources/carga.sql`: 1.000.000 de pedidos, 100.000 clientes (unos 10 pedidos cada uno), estados 2 % PENDIENTE, 8 % PAGADO, 20 % ENVIADO, 70 % ENTREGADO, dos años de fechas. Carga: 1,6 s.

## 2 y 3. Cinco consultas, sus planes y los índices mínimos

| Consulta | Sin índices | Plan | Con índices | Plan |
|---|---:|---|---:|---|
| Q1 pendientes recientes de un cliente | 17,05 ms | Parallel Seq Scan | 0,016 ms | Bitmap Index Scan on idx_pedidos_cliente_fecha |
| Q2 por correo sin importar mayúsculas | 63,68 ms | Parallel Seq Scan | 0,011 ms | Bitmap Index Scan on idx_pedidos_correo_lower |
| Q3 total vendido en un día | 14,08 ms | Parallel Seq Scan | 0,135 ms | Index Scan using idx_pedidos_creado_en |
| Q4 historial de 90 días de un cliente | 12,78 ms | Parallel Seq Scan | 0,007 ms | Index Only Scan using idx_pedidos_cliente_fecha |
| Q5 los diez pendientes más antiguos | 13,66 ms | Parallel Seq Scan | 0,008 ms | Index Scan using idx_pedidos_pendientes |

Índices (`src/main/resources/indices.sql`), cuatro para cinco consultas:

- `idx_pedidos_cliente_fecha (cliente_id, creado_en DESC) INCLUDE (estado, total)`. Igualdad primero (`cliente_id`), orden después (`creado_en`): dentro de cada cliente las filas ya están en el orden que pide `ORDER BY creado_en DESC`, y el rango de Q4 es un tramo contiguo. Al revés, `(creado_en, cliente_id)`, obligaría a recorrer todas las fechas para encontrar un cliente. El `INCLUDE` permite el recorrido de solo índice de Q4.
- `idx_pedidos_correo_lower (lower(correo))`. Q2 aplica `lower()` a la columna; solo un índice sobre la misma expresión sirve.
- `idx_pedidos_creado_en (creado_en)`. Rango por fecha para el reporte diario.
- `idx_pedidos_pendientes (creado_en) WHERE estado = 'PENDIENTE'`. Parcial: solo indexa el 2 % de las filas.

Q1 no necesita un índice propio: el compuesto por cliente le da los ~10 pedidos del cliente y el filtro por estado se aplica sobre esas pocas filas.

## 4. El costo de los índices

Insertar 100.000 filas: **84 ms** en una copia de la tabla solo con la clave primaria, **544 ms** con la clave primaria y los cuatro índices (6,5 veces más). Crear los cuatro índices sobre el millón de filas tomó 1,4 s.

## 5. Un índice que existe y no se usa

- **Baja selectividad:** con un índice sobre `estado`, `WHERE estado = 'ENTREGADO'` (70 % de las filas) sigue con `Parallel Seq Scan` (28,1 ms). Recorrer el índice y saltar a la tabla por cada fila cuesta más que leer la tabla entera, y las estadísticas de `ANALYZE` se lo dicen al planificador. El mismo índice sí se usa para `'PENDIENTE'` (2 %).
- **Función sobre la columna:** `WHERE date(creado_en AT TIME ZONE 'America/Bogota') = '2026-06-01'` ignora el índice de `creado_en` (`Parallel Seq Scan`, 60,2 ms). El índice ordena `creado_en`, no el resultado de la función. Escrito como rango sobre la columna (`creado_en >= … AND creado_en < …`) usa el índice: 0,08 ms.

## 6. OFFSET frente a cursor

- `ORDER BY id LIMIT 20 OFFSET 900000`: 68,7 ms. El plan muestra `Index Scan using pedidos_pkey … rows=900020`: lee y descarta 900.000 filas para devolver 20.
- `WHERE id > 900000 ORDER BY id LIMIT 20`: 0,010 ms. Salta directo al punto del índice.

## 7. Una consulta de JPA sin índice adecuado

Con `logging.level.org.hibernate.SQL=debug` (la prueba comprueba que el registro aparece en la salida), la consulta derivada `findByCorreoIgnoreCase` se envía así:

```sql
select p1_0.id, … from pedidos p1_0 where upper(p1_0.correo)=upper(?)
```

Spring Data traduce `IgnoreCase` con `upper()`, y el índice existente es sobre `lower(correo)`: el plan es `Parallel Seq Scan` (65,3 ms). Con un índice sobre `upper(correo)` baja a 0,027 ms. La alternativa es escribir la consulta con `lower()` explícito para reutilizar el índice existente. La lección: el SQL que genera el ORM se lee, no se supone.
