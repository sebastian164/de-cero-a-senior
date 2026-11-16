# 06 — NoSQL y caché

## Idea

Un martillo es excelente para clavos y pésimo para tornillos. Las bases relacionales son la herramienta por defecto y resuelven la gran mayoría de los casos; las demás existen para necesidades específicas. Y una caché es tener a la mano una copia de lo que se consulta mucho: útil, siempre que la copia no quede desactualizada.

## Lo esencial

### Familias NoSQL

| Tipo | Modelo | Ejemplos | Bueno para |
|---|---|---|---|
| Clave-valor | Clave → valor opaco | Redis, DynamoDB | Caché, sesiones, contadores |
| Documentos | Documentos JSON anidados | MongoDB | Datos con estructura variable que se leen completos |
| Columnar ancho | Filas con muchas columnas, particionadas | Cassandra | Escrituras masivas, series de tiempo |
| Grafos | Nodos y relaciones | Neo4j | Redes, recomendaciones, rutas |
| Búsqueda | Índices invertidos | Elasticsearch, OpenSearch | Texto completo, registros |

Lo que se cede frente a una base relacional suele ser alguna de estas cosas: uniones, transacciones entre varios registros, esquema verificado o consistencia inmediata. Lo que se gana: escalado horizontal, flexibilidad de esquema o un modelo de consulta especializado.

**Regla práctica:** empieza con una base relacional (PostgreSQL además maneja JSON, texto completo y datos geográficos). Incorpora otra tecnología cuando un requisito concreto y medido lo exija. Cada base adicional es una cosa más que operar, respaldar y mantener consistente.

En NoSQL se modela **a partir de las consultas**, no de las entidades: primero se listan los patrones de acceso y después se diseña la estructura.

### Caché

**Patrones:**

| Patrón | Cómo funciona |
|---|---|
| *Cache-aside* | La aplicación consulta la caché; si falla, lee la base y guarda. El más común |
| *Read-through* | La caché carga el dato por sí misma |
| *Write-through* | Se escribe en caché y base a la vez |
| *Write-behind* | Se escribe en caché y la base se actualiza después |

**Decisiones:** qué se guarda, cuánto vive (TTL), cómo se invalida y cuánta memoria se le da.

**Los problemas difíciles:**

- **Invalidación:** el dato cambió y la caché no. Es la fuente de errores más sutil.
- **Estampida:** expira una clave muy consultada y cien peticiones van a la base a la vez. Mitigación: bloqueo por clave, expiración escalonada, refresco anticipado.
- **Penetración:** consultas repetidas de claves que no existen. Mitigación: guardar también los "no existe".
- **Caché local frente a distribuida:** una caché en memoria (Caffeine) es rapidísima pero cada réplica tiene su copia; una distribuida (Redis) es compartida pero implica red.

## Ejemplo

```java
@Service
class CatalogoProductos {

    @Cacheable(cacheNames = "productos", key = "#id")
    public ProductoDto buscar(Long id) {
        return repositorio.buscarDto(id);
    }

    @CacheEvict(cacheNames = "productos", key = "#id")
    public void actualizarPrecio(Long id, BigDecimal precio) { ... }
}
```

```yaml
spring:
  cache.type: redis
  cache.redis.time-to-live: 10m
```

`@Cacheable` funciona mediante un proxy: las llamadas internas no pasan por la caché. Y el TTL es obligatorio: sin él, un error de invalidación dura para siempre. Dos advertencias medidas con Spring Boot 4.1.1 y Redis: `@Cacheable(sync = true)` no evita la estampida con la caché de Redis (el escritor por defecto no bloquea), y con Lettuce las escrituras y las invalidaciones de la caché son asíncronas por defecto: `RedisCacheWriter.create(conexiones, c -> c.immediateWrites())` las hace inmediatas.

## Errores comunes

- Elegir MongoDB "porque no hay que definir esquema": el esquema existe, solo que disperso en el código.
- Agregar una caché para ocultar una consulta sin índice.
- Cachés sin TTL ni límite de tamaño.
- Guardar en caché datos que dependen del usuario con una clave que no lo incluye (fuga de datos entre usuarios).
- Guardar entidades JPA en caché.
- Tratar a Redis como base principal sin entender su persistencia.
- Dos fuentes de verdad sin un plan para mantenerlas coherentes.
- No medir la tasa de aciertos.

## Taller

1. Agrega una caché a un servicio de catálogo. Mide tiempos y tasa de aciertos.
2. Provoca un dato desactualizado y corrígelo con invalidación; después con TTL corto. Discute cuál elegirías.
3. Simula una estampida con 200 peticiones concurrentes al expirar una clave y mitígala.
4. Demuestra el error de autoinvocación con `@Cacheable`.
5. Modela el mismo dominio (un blog con entradas, comentarios y etiquetas) en relacional y en documentos. Lista las consultas que cada modelo hace fáciles y difíciles.
6. Para tres escenarios (carrito de compras, historial de sensores, red de contactos), elige el almacenamiento y justifica.

Rama `taller/n8-06-nosql-cache`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Cuándo justificas una base NoSQL en un proyecto?
2. ¿Qué cedes al dejar el modelo relacional?
3. Estrategias de invalidación de caché y sus riesgos.
4. ¿Qué es una estampida de caché y cómo se evita?
5. Caché local frente a distribuida: criterios.
6. ¿Cómo mantienes coherentes una base relacional y un índice de búsqueda?
