# Solución — n8-03 Transacciones, ACID y niveles de aislamiento

PostgreSQL 18.7, Spring Boot 4.1.1 (Spring Framework 7.0.9, Hibernate 7.4.5). Todo lo de abajo lo ejecutan las pruebas del módulo; las cifras son de una corrida del 2026-11-15.

Las pruebas `AnomaliasTest` usan dos conexiones JDBC intercaladas a mano, en el mismo orden que dos ventanas de `psql`. Para repetirlo en `psql`, abre dos sesiones (`docker compose exec postgres psql -U curso -d n8_03_anomalias`, desde la carpeta de la solución; la base `n8_03_anomalias` la crea `AnomaliasTest`, así que corre antes `mvn verify`) y escribe los pasos de abajo.

## 1. Las tres anomalías, paso a paso

Tabla: `productos (id, nombre, stock, precio, version)` con `(7, 'balón', 10, 100, 5)`.

**Lectura no repetible**

```
T1: BEGIN ISOLATION LEVEL READ COMMITTED;
T1: SELECT precio FROM productos WHERE id = 7;        -- 100
T2: UPDATE productos SET precio = 120 WHERE id = 7;    -- autocommit
T1: SELECT precio FROM productos WHERE id = 7;        -- 120: cambió dentro de la misma transacción
T1: COMMIT;
```

**Lectura fantasma**

```
T1: BEGIN ISOLATION LEVEL READ COMMITTED;
T1: SELECT count(*) FROM productos WHERE precio < 200;   -- 2
T2: INSERT INTO productos VALUES (9, 'guantes', 5, 80);
T1: SELECT count(*) FROM productos WHERE precio < 200;   -- 3
T1: COMMIT;
```

**Actualización perdida** (la del README)

```
T1: BEGIN;                    T2: BEGIN;
T1: SELECT stock … ; -- 10    T2: SELECT stock … ; -- 10
T1: UPDATE … SET stock = 9; COMMIT;
T2: UPDATE … SET stock = 9; COMMIT;     -- se vendieron 2 y se descontó 1
```

| Anomalía | READ COMMITTED | REPEATABLE READ | SERIALIZABLE |
|---|---|---|---|
| Lectura no repetible | Aparece | Desaparece | Desaparece |
| Lectura fantasma | Aparece | **Desaparece** (en PostgreSQL) | Desaparece |
| Actualización perdida | Aparece (stock 9) | Se detecta: el `UPDATE` de T2 falla con `40001 could not serialize access due to concurrent update` | Se detecta igual |

En PostgreSQL, `REPEATABLE READ` trabaja sobre una instantánea, así que tampoco ve filas nuevas: es más estricto que lo que exige el estándar SQL. La tabla del README lo dice bien ("Posible (según el motor)").

## 2. Sesgo de escritura: los médicos de guardia

```
T1: BEGIN ISOLATION LEVEL REPEATABLE READ;     T2: BEGIN ISOLATION LEVEL REPEATABLE READ;
T1: SELECT count(*) FROM medicos WHERE de_guardia;   -- 2
T2: SELECT count(*) FROM medicos WHERE de_guardia;   -- 2
T1: UPDATE medicos SET de_guardia = false WHERE nombre = 'Ana';
T2: UPDATE medicos SET de_guardia = false WHERE nombre = 'Beto';   -- otra fila: no se bloquean
T1: COMMIT;  T2: COMMIT;                                            -- ambos confirman: 0 de guardia
```

En `READ COMMITTED` y `REPEATABLE READ` ambos confirman y no queda nadie. En `SERIALIZABLE`, el `COMMIT` de T2 falla con `40001` y queda un médico. `REPEATABLE READ` no lo evita porque cada transacción escribe una fila distinta: no hay conflicto de escritura que detectar, el conflicto está en lo que se leyó.

## 3. Las tres correcciones de la actualización perdida

20 hilos venden una unidad cada uno sobre un stock de 100 (`TresCorreccionesTest`):

| Variante | Stock final (esperado 80) | Reintentos | Tiempo |
|---|---:|---:|---:|
| Leer, calcular y escribir sin protección | 99 | — | 71 ms |
| 1. `UPDATE … SET stock = stock - 1 WHERE … AND stock >= 1` | 80 | 0 | 49 ms |
| 2. `SELECT … FOR UPDATE` | 80 | 0 | 223 ms |
| 3. Columna de versión (`WHERE version = :leida`) | 80 | 190 | 200 ms |

- La operación atómica es la más simple y la más rápida: un solo viaje y la base serializa sobre la fila.
- El bloqueo pesimista es correcto pero serializa a los 20 hilos durante toda la transacción (incluido el "cálculo").
- La versión es correcta pero bajo mucha contención desperdicia trabajo: 190 reintentos para 20 ventas. Es buena cuando los conflictos son raros, no con un producto muy disputado.

## 4. Spring con 50 hilos

`SpringConcurrenciaTest`: 50 hilos llaman a un servicio `@Transactional` sobre un inventario de 100.

- `venderIngenuo` (carga la entidad, descuenta en memoria, Hibernate escribe al confirmar): **stock final 98**, deberían ser 50. Se perdieron 48 ventas.
- `venderAtomico` (`@Modifying` con `update … set stock = stock - :n where … and stock >= :n`): stock final exactamente 50, y cada llamada confirma que descontó (1 fila afectada).

## 5. Fallo de serialización y reintentos

Cinco médicos de guardia y cinco peticiones simultáneas de salida; la regla exige que quede al menos uno.

| Variante | Aprobadas | Rechazadas por la regla | Errores al usuario | Quedan de guardia |
|---|---:|---:|---:|---:|
| `READ COMMITTED` | 5 | 0 | 0 | **0** (regla rota) |
| `SERIALIZABLE` sin reintento | 1 | 0 | 4 | 4 |
| `SERIALIZABLE` con reintento | 4 | 1 | 0 | 1 |

Sin reintento la regla se cumple, pero cuatro usuarios reciben un `ConcurrencyFailureException` por una operación que habría salido bien al repetirla. El reintento se hace con `@Retryable` de Spring Framework 7 (`org.springframework.resilience.annotation`, activado con `@EnableResilientMethods`), hasta 10 reintentos con espera de 20 ms, multiplicador 1,5 y variación aleatoria. Va en un bean que envuelve al transaccional: cada intento es una transacción nueva. Si el reintento quedara dentro de la transacción, repetiría sobre una transacción ya abortada.
