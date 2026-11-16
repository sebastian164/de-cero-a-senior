# Solución — n8-06 NoSQL y caché

Spring Boot 4.1.1 (Spring Data Redis 4.1.1, Lettuce 7.5.2), Redis 8.10.3, PostgreSQL 18.7, MongoDB 9.0.8 con el driver síncrono 5.8.1. Cifras de una corrida del 2026-11-15.

El servicio `CatalogoProductos` lee de PostgreSQL con una consulta que tarda unos 20 ms (`pg_sleep(0.02)`, para simular una consulta costosa). El repositorio cuenta cada consulta que llega a la base. La caché es Redis (`spring.cache.type: redis`, `spring.cache.redis.time-to-live: 10m`).

## 1. Tiempos y tasa de aciertos

500 consultas, el 80 % concentrado en 10 productos y el resto repartido entre 100:

| | Tiempo | Consultas a la base |
|---|---:|---:|
| Sin caché | 10.684 ms | 500 |
| Con caché | 1.495 ms | 61 |

Tasa de aciertos 87,8 % (439 aciertos, 61 fallos), medida con las estadísticas de `RedisCache` (`RedisCacheManagerBuilderCustomizer.enableStatistics()`). Los fallos coinciden exactamente con las consultas a la base. El TTL real de una clave en Redis fue 598 s: el `10m` de la configuración se aplica.

## 2. Dato desactualizado: invalidación frente a TTL corto

- Se lee el producto 5 (1.005), se cambia el precio en la base sin pasar por la caché, y la caché sigue devolviendo 1.005.
- Con `@CacheEvict` en `actualizarPrecio`, la siguiente lectura trae 2.000.
- Con una caché de TTL de 1 s (`productos-ttl-corto`), el dato viejo se sirve dentro del segundo y el nuevo después.

Elegiría invalidar cuando todas las escrituras pasan por el mismo servicio: el dato se corrige en el acto. El TTL corto es la red de seguridad para lo que cambia por fuera (otro sistema, un script), a costa de más consultas a la base y de una ventana de datos viejos. Lo habitual es combinar ambos: invalidar en las escrituras propias y un TTL que acote el daño de una invalidación olvidada.

## 3. Estampida con 200 peticiones

Se carga la clave, se borra (como si expirara) y llegan 200 peticiones simultáneas:

| Variante | Consultas a la base |
|---|---:|
| `@Cacheable` sin protección | entre 87 y 200 según la corrida |
| `@Cacheable(sync = true)` con la caché de Redis por defecto | **200** |
| Bloqueo local por clave con doble comprobación | 1 |
| Dos instancias con `RedisCacheWriter.lockingRedisCacheWriter` | 1 |

Hallazgo: `sync = true` no mitiga nada con la caché de Redis que configura Spring Boot. El `RedisCacheWriter` por defecto no bloquea; `RedisCache.get(clave, cargador)` consulta, y si no encuentra, carga y guarda sin ninguna exclusión. Lo comprobé en el código de `DefaultRedisCacheWriter` de Spring Data Redis 4.1.1 y en la prueba.

Segundo hallazgo: en Spring Data Redis 4.1.1, `put` y `evict` de la caché son **asíncronos por defecto** con Lettuce (`DefaultRedisCacheWriter.writeAsynchronously()`): el método vuelve antes de que Redis aplique el cambio. Con la configuración por defecto, la doble comprobación del bloqueo por clave falló en 1 de cada 3 o 4 corridas (2 consultas en lugar de 1): el hilo que entraba después del primero releía la caché y aún no veía el valor recién guardado. Se corrigió configurando el escritor con `RedisCacheWriter.create(conexiones, c -> c.immediateWrites().collectStatistics())` (ver `AplicacionCatalogo`); después pasó 5 de 5 corridas seguidas. La misma asincronía vale para `@CacheEvict`: la invalidación puede llegar a Redis unos instantes después de que el método vuelve.

Mitigaciones que sí funcionaron:

- **Bloqueo local por clave** (`buscarConBloqueoPorClave`): un `ReentrantLock` por identificador y una segunda lectura de la caché tras obtenerlo. Una consulta por instancia de la aplicación.
- **`RedisCacheWriter.lockingRedisCacheWriter`**: el bloqueo vive en Redis, así que vale entre instancias. Con 200 peticiones repartidas en dos gestores de caché independientes llegó una sola consulta a la base. El costo: el bloqueo es por caché, no por clave, y cada escritura hace viajes extra a Redis.
- Otras del README que no se midieron aquí: expiración escalonada (TTL con variación) y refresco anticipado.

## 4. Autoinvocación con `@Cacheable`

Tres llamadas a `buscar(9)` desde fuera del bean: 1 consulta. Las mismas tres desde `buscarVarios`, que llama a `this.buscar`: 3 consultas, aunque la clave ya estaba en caché. La llamada interna no pasa por el proxy que aplica la caché.

## 5. Un blog en relacional y en documentos

`BlogModelosTest` carga los mismos datos en PostgreSQL (`autores`, `entradas`, `etiquetas`, `entradas_etiquetas`, `comentarios`) y en MongoDB (una colección `entradas` con las etiquetas y los comentarios dentro). Cada prueba responde la misma pregunta en los dos modelos y compara el resultado.

| Consulta | Relacional | Documentos |
|---|---|---|
| Una entrada completa con comentarios y etiquetas | Varias consultas o uniones | **Fácil**: un acceso por `_id` |
| Entradas con la etiqueta X | Fácil (unión con índice) | **Fácil** (índice multivalor sobre `etiquetas`) |
| Todos los comentarios de un autor | **Fácil**: `WHERE autor = …` | Difícil: agregación con `$unwind` y doble `$match` |
| Las etiquetas más usadas | **Fácil**: `GROUP BY` | Posible: `$unwind` + `$group` |
| Renombrar una etiqueta | **Fácil**: una fila | Difícil: `updateMany` con `arrayFilters` sobre cada documento que la copia (2 documentos aquí, millones en producción) |
| Integridad (comentario de un autor inexistente) | La base lo rechaza (23503) | Se acepta sin protesta, salvo que se defina validación de esquema |

El modelo de documentos gana cuando la entrada se lee entera y casi nunca se consulta "de lado". Pierde cuando aparecen consultas transversales (por autor de comentario) o datos compartidos que cambian (etiquetas).

## 6. Tres escenarios

| Escenario | Elección | Por qué |
|---|---|---|
| Carrito de compras | Clave-valor (Redis) con TTL, o una tabla en la base principal si el carrito debe sobrevivir días | Se lee y escribe completo por usuario, sin consultas transversales. Perderlo es molesto, no grave. Al confirmar el pedido pasa a la base relacional, que es la fuente de verdad |
| Historial de sensores | Series de tiempo: PostgreSQL con particiones por fecha (o TimescaleDB). Columnar ancho (Cassandra) si el volumen de escritura lo exige | Escrituras masivas de solo anexar, lecturas por sensor y rango de tiempo, borrado por antigüedad (se elimina la partición entera) |
| Red de contactos | Relacional mientras las consultas sean de uno o dos saltos (tabla de relaciones con índice). Grafos (Neo4j) si aparecen recorridos de profundidad variable ("amigos de amigos de amigos", caminos más cortos) | Cada salto en relacional es una unión; a partir de tres o cuatro saltos el modelo de grafos es más natural y rápido. Hasta entonces, otra base es costo operativo sin beneficio medido |
