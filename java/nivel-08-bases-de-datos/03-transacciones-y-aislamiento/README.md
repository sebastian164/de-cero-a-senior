# 03 — Transacciones, ACID y niveles de aislamiento

## Idea

Una transferencia bancaria tiene dos pasos: restar de una cuenta y sumar a otra. Si se hace solo el primero, desaparece dinero. Una transacción garantiza que los dos pasos ocurren o no ocurre ninguno, y que nadie ve el estado intermedio.

## Lo esencial

### ACID

| Propiedad | Garantiza |
|---|---|
| **Atomicidad** | Todo o nada |
| **Consistencia** | La base pasa de un estado válido a otro (se cumplen las restricciones) |
| **Aislamiento** | Las transacciones concurrentes no se interfieren |
| **Durabilidad** | Lo confirmado sobrevive a una caída |

### Anomalías de concurrencia

| Anomalía | Qué ocurre |
|---|---|
| Lectura sucia | Leo datos de una transacción que aún no confirma (y que puede revertir) |
| Lectura no repetible | Leo la misma fila dos veces y cambió entre lecturas |
| Lectura fantasma | Repito una consulta y aparecen filas nuevas |
| Actualización perdida | Dos transacciones leen, modifican y escriben; una pisa a la otra |
| Sesgo de escritura (*write skew*) | Dos transacciones leen el mismo conjunto, deciden con base en él y escriben filas distintas, rompiendo una regla |

### Niveles de aislamiento

| Nivel | Sucia | No repetible | Fantasma |
|---|---|---|---|
| `READ UNCOMMITTED` | Posible | Posible | Posible |
| `READ COMMITTED` | No | Posible | Posible |
| `REPEATABLE READ` | No | No | Posible (según el motor) |
| `SERIALIZABLE` | No | No | No |

Más aislamiento significa menos anomalías y también más bloqueos, más reintentos y menos rendimiento. **El valor por defecto varía:** PostgreSQL y Oracle usan `READ COMMITTED`; MySQL InnoDB, `REPEATABLE READ`. Los motores modernos implementan el aislamiento con **MVCC** (versiones de las filas): los lectores no bloquean a los escritores.

## Ejemplo: actualización perdida

```
T1: SELECT stock FROM productos WHERE id = 7;   -- lee 10
T2: SELECT stock FROM productos WHERE id = 7;   -- lee 10
T1: UPDATE productos SET stock = 9 WHERE id = 7;
T2: UPDATE productos SET stock = 9 WHERE id = 7;   -- se vendieron 2, se descontó 1
```

Tres correcciones:

```sql
-- 1. Operación atómica: la base hace el cálculo
UPDATE productos SET stock = stock - 1 WHERE id = 7 AND stock >= 1;

-- 2. Bloqueo pesimista
SELECT stock FROM productos WHERE id = 7 FOR UPDATE;

-- 3. Bloqueo optimista (columna de versión)
UPDATE productos SET stock = 9, version = 6 WHERE id = 7 AND version = 5;
-- si afecta 0 filas, alguien cambió el registro: reintentar o informar
```

## En Spring

```java
@Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
public void reservar(Long productoId, int cantidad) { ... }
```

- El aislamiento se pide por transacción; el motor decide cómo lo cumple.
- Con `SERIALIZABLE` (y a veces con `REPEATABLE READ`), el motor puede abortar una transacción por conflicto: la aplicación debe **reintentar**.
- `@Transactional(readOnly = true)` en las consultas.
- Mantén las transacciones cortas: sin llamadas remotas ni esperas de usuario dentro.

## Errores comunes

- Leer, calcular en la aplicación y escribir, sin protección ante concurrencia.
- Asumir que una transacción aísla por completo sin conocer el nivel por defecto.
- Transacciones largas que retienen bloqueos y conexiones.
- Subir todo a `SERIALIZABLE` sin lógica de reintento.
- Comprobar una regla con un `SELECT` y luego insertar, sin restricción única ni bloqueo.
- Creer que las pruebas con un solo usuario demuestran algo sobre la concurrencia.

## Taller

1. Con dos sesiones de `psql`, reproduce paso a paso: lectura no repetible, lectura fantasma y actualización perdida. Anota en qué nivel aparece y en cuál desaparece cada una.
2. Reproduce un sesgo de escritura: "siempre debe quedar al menos un médico de guardia" con dos médicos que piden salir a la vez.
3. Corrige la actualización perdida de las tres formas del ejemplo y compara.
4. En una aplicación Spring, lanza 50 hilos que descuentan del mismo inventario y demuestra el error; corrígelo.
5. Provoca un fallo de serialización y agrega reintentos.

Rama `taller/n8-03-transacciones`.

## Preguntas senior

1. Explica cada letra de ACID con un ejemplo de qué pasa si falta.
2. ¿Qué nivel de aislamiento usa tu base por defecto y qué anomalías permite?
3. ¿Qué es MVCC y qué ventaja da?
4. ¿Qué es el sesgo de escritura y por qué `REPEATABLE READ` no siempre lo evita?
5. ¿Por qué las transacciones deben ser cortas?
6. ¿Cómo garantizas que nunca se venda más inventario del disponible?
