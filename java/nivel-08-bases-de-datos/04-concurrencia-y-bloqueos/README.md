# 04 — Control de concurrencia: bloqueo optimista y pesimista

## Idea

Dos personas editan el mismo documento. Hay dos políticas: "quien lo abre lo bloquea y los demás esperan" (pesimista), o "todos editan, y al guardar se comprueba si alguien cambió algo mientras tanto" (optimista). La primera evita conflictos; la segunda los detecta.

## Lo esencial

| | Optimista | Pesimista |
|---|---|---|
| Supuesto | Los conflictos son raros | Los conflictos son frecuentes |
| Mecanismo | Columna de versión; se verifica al escribir | Bloqueo de la fila al leer (`SELECT … FOR UPDATE`) |
| Ante un conflicto | Falla la escritura; se reintenta o se informa | El segundo espera |
| Costo | Trabajo perdido cuando hay conflicto | Bloqueos, esperas, riesgo de interbloqueo |
| Escala | Mejor | Peor |
| Ideal para | Edición por usuarios, lecturas frecuentes | Inventario muy disputado, colas de trabajo |

### Optimista con JPA

```java
@Entity
class Producto {
    @Id Long id;
    int stock;
    @Version long version;      // Hibernate lo incrementa y lo comprueba en cada UPDATE
}
```

Si otra transacción modificó la fila, Hibernate lanza `OptimisticLockException` (en Spring, `ObjectOptimisticLockingFailureException`). La aplicación decide: reintentar automáticamente si la operación es segura de repetir, o devolver `409 Conflict` para que el usuario recargue.

### Pesimista con JPA

```java
interface ProductoRepository extends JpaRepository<Producto, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select p from Producto p where p.id = :id")
    Optional<Producto> buscarParaActualizar(Long id);
}
```

Siempre con tiempo límite: una espera indefinida propaga el problema a todo el pool de conexiones.

### Otras herramientas

- **Operación atómica en SQL:** `UPDATE … SET stock = stock - :n WHERE stock >= :n`. Suele ser la solución más simple y robusta.
- **Restricciones únicas** para impedir duplicados por concurrencia: es más fiable que comprobar antes de insertar.
- **`SKIP LOCKED`:** varios trabajadores toman tareas de una tabla sin pisarse.
- **Bloqueos consultivos** (*advisory locks*) y **bloqueos distribuidos** (Redis, base de datos) cuando hay varias instancias de la aplicación. Los `synchronized` de Java **no sirven** entre réplicas.

### Interbloqueos

Dos transacciones se esperan mutuamente. La base lo detecta y aborta una. Prevención: adquirir los bloqueos siempre en el mismo orden (por ejemplo, por identificador ascendente), transacciones cortas, y reintentar la abortada.

## Ejemplo: cola de trabajo con varios trabajadores

```sql
BEGIN;
SELECT id FROM tareas
WHERE estado = 'PENDIENTE'
ORDER BY creada_en
LIMIT 10
FOR UPDATE SKIP LOCKED;       -- cada trabajador toma tareas distintas, sin esperar
UPDATE tareas SET estado = 'EN_PROCESO' WHERE id = ANY(:ids);
COMMIT;
```

## Errores comunes

- Usar `synchronized` para proteger datos en una aplicación con varias réplicas.
- `@Version` sin manejar la excepción: el usuario ve un error 500.
- Reintentar automáticamente operaciones que no son idempotentes.
- Bloqueo pesimista sin tiempo límite.
- Bloquear filas en órdenes distintos según el camino del código.
- "Comprobar y luego insertar" sin restricción única.
- No propagar la versión al cliente en ediciones de larga duración (formularios): la versión debe ir y volver, por ejemplo como `ETag`.

## Taller

1. Agrega `@Version` a una entidad y provoca el conflicto con dos transacciones. Devuelve `409` con un mensaje útil.
2. Implementa reintento automático (tres intentos) para una operación idempotente.
3. Resuelve el mismo caso con bloqueo pesimista y compara el comportamiento bajo carga con 50 hilos.
4. Provoca un interbloqueo transfiriendo entre dos cuentas en direcciones opuestas y corrígelo ordenando los bloqueos.
5. Implementa una cola de tareas con `SKIP LOCKED` y tres trabajadores, y demuestra que ninguna tarea se procesa dos veces.
6. Explica por qué `synchronized` falla con dos instancias de la aplicación y demuéstralo.

Rama `taller/n8-04-concurrencia`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Cuándo eliges bloqueo optimista y cuándo pesimista?
2. ¿Cómo funciona `@Version` y qué haces cuando falla?
3. ¿Cómo previenes y cómo manejas los interbloqueos?
4. ¿Por qué un bloqueo en memoria no sirve en un sistema con varias réplicas?
5. ¿Cómo implementas la edición concurrente de un formulario que el usuario mantiene abierto diez minutos?
6. ¿Para qué sirve `SKIP LOCKED`?
