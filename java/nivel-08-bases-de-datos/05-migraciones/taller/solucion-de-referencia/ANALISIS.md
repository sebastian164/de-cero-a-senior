# Solución — n8-05 Migraciones y evolución del esquema

Spring Boot 4.1.1, Flyway 12.4.0 (`spring-boot-starter-flyway` + `flyway-database-postgresql`), PostgreSQL 18.7. Cifras de una corrida del 2026-11-15.

## 1. Flyway en Spring Boot con tres migraciones

`src/main/resources/db/migration/V1__crear_clientes.sql`, `V2__crear_pedidos.sql`, `V3__agregar_estado_a_pedidos.sql`, y `application.yaml` con la configuración del README (`jpa.hibernate.ddl-auto: validate`, `flyway.enabled: true`). `MigracionesEnBaseLimpiaTest` arranca la aplicación sobre una base vacía: Flyway aplica las tres (`flyway_schema_history` = 1, 2, 3) y Hibernate valida la entidad `Pedido` contra el esquema resultante.

En Spring Boot 4 Flyway ya no entra solo con `flyway-core`: hace falta `spring-boot-starter-flyway` (o el módulo `spring-boot-flyway`), y para PostgreSQL además `flyway-database-postgresql`.

## 2. Editar una migración aplicada

`MigracionEditadaTest` aplica las tres, agrega un índice al final de `V2` y vuelve a migrar:

```
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 2
-> Applied to database : 170164471
-> Resolved locally    : 248646764
```

Flyway guarda la suma de verificación de cada script aplicado y la compara al arrancar (`validateOnMigrate`, activo por defecto y también en Spring Boot). El archivo ya no es el que se ejecutó, así que Flyway se niega a seguir: no sabe si la base tiene el índice o no. En producción eso significa que la aplicación no arranca. La corrección es una `V4__indice_pedidos_fecha.sql`. Devolver `V2` a su contenido original también lo arregla; `flyway repair` solo si de verdad se quiere aceptar el cambio.

## 3. Expandir y contraer: `nombre` → `nombre_completo`

Migraciones en `src/main/resources/expandir/`, versiones de la aplicación en `VersionesDeLaAplicacion` (v1 a v4). `ExpandirYContraerTest` aplica cada despliegue con `Flyway.target(...)` y comprueba que la versión anterior (todavía viva durante el despliegue) y la nueva crean y leen clientes.

| Despliegue | Migración | Aplicación nueva | Conviven y funcionan | Reversa |
|---|---|---|---|---|
| 1. Expandir | `V2`: `ADD COLUMN nombre_completo` (nula) | v2: escribe en ambas, lee `nombre` | v1 y v2 | Volver a v1; la columna nula sobra pero no estorba. Si hace falta, `DROP COLUMN nombre_completo` (nadie la lee todavía) |
| 2. Migrar | `V3`: rellena `nombre_completo` desde `nombre` (por lotes si es grande) | v3: escribe en ambas, lee `nombre_completo` | v2 y v3 | Volver a v2: ambas columnas siguen completas porque v3 escribe en las dos |
| 3. Cambiar | `V4`: `nombre` deja de ser obligatoria, `nombre_completo` pasa a serlo | v4: solo `nombre_completo` | v3 y v4 (v3 lee lo que escribe v4) | Volver a v3 y rellenar `nombre` desde `nombre_completo` para las filas que escribió v4 (`UPDATE … WHERE nombre IS NULL`) antes de restaurar el `NOT NULL` |
| 4. Contraer | `V5`: `DROP COLUMN nombre` | v4 (sin cambios) | v4 | No hay reversa sin pérdida: se hace solo cuando ya no hay vuelta atrás a v3. Antes del `DROP`, respaldo de la columna (`CREATE TABLE clientes_nombre_respaldo AS SELECT id, nombre …`) |

La prueba también muestra por qué la contracción va en un despliegue aparte: tras `V5`, v3 falla al insertar (`column "nombre" … does not exist`).

## 4. Columna obligatoria en un millón de filas

`ColumnaObligatoriaTest`: un hilo inserta pedidos sin parar mientras se ejecuta cada DDL, y se mide la peor espera que sufrió:

| Estrategia | Duración de la DDL | Peor espera del escritor |
|---|---:|---:|
| `ADD COLUMN … NOT NULL` sin valor por defecto | falla: `column "canal" … contains null values` | — |
| `ADD COLUMN canal … NOT NULL DEFAULT 'WEB'` (constante) | 1 ms | 1 ms |
| `ADD COLUMN token UUID NOT NULL DEFAULT gen_random_uuid()` (volátil) | 793 ms | 787 ms |
| Segura 1: `ADD COLUMN region VARCHAR(20)` (nula) | 1 ms | 1 ms |
| Segura 2: relleno por lotes de 50.000 (cada lote, transacción corta) | 1.334 ms | 2 ms |
| Segura 3: `ADD CONSTRAINT … CHECK (region IS NOT NULL) NOT VALID` | 0 ms | 1 ms |
| Segura 4: `VALIDATE CONSTRAINT` | 85 ms | 12 ms |
| Segura 5: `SET NOT NULL` | 2 ms | 4 ms |

- Desde PostgreSQL 11, una columna con valor por defecto **constante** se agrega sin reescribir la tabla: solo cambia el catálogo.
- Con un valor **volátil** (o calculado por fila) la tabla se reescribe con bloqueo exclusivo: el escritor queda detenido todo ese tiempo (787 ms con un millón de filas pequeñas; crece con el tamaño).
- Cuando el valor hay que calcularlo, la forma segura es la de la tabla: nula, relleno por lotes, `CHECK … NOT VALID` (solo vale para filas nuevas), `VALIDATE` (recorre la tabla sin bloquear escrituras) y `SET NOT NULL`, que en PostgreSQL 12+ aprovecha el `CHECK` validado y no vuelve a recorrer la tabla (2 ms).
- Lo que salió al ejecutarlo: el `VALIDATE` falló la primera vez porque la versión vieja de la aplicación seguía insertando filas sin región. El orden correcto es desplegar primero la versión que ya escribe la columna y después rellenar y restringir. La prueba final usa ese orden.
- Siempre con `SET lock_timeout`: si la DDL no consigue el bloqueo pronto, que falle en vez de dejar en cola a todos los que llegan detrás.

## 5. Plan de reversa

En la tabla del paso 3 para expandir y contraer. Para la columna obligatoria: los pasos 1 a 4 se revierten con `DROP CONSTRAINT` / `DROP COLUMN region` mientras ninguna versión dependa de ella; el paso 5 con `ALTER COLUMN region DROP NOT NULL`.

## 6. Comprobación en integración continua

- `.github/workflows/migraciones.yml`: levanta `postgres:18.7` como servicio y ejecuta `ci/verificar-migraciones.sh`.
- El script corre `MigracionesEnBaseLimpiaTest`: aplica todas las migraciones a una base recién creada y arranca la aplicación con `ddl-auto=validate`. Falla si una migración no aplica, si se editó una ya aplicada (la suma de verificación no coincide) o si las entidades no coinciden con el esquema.
- El flujo pasa `actionlint` 1.7.12 y el script se puede ejecutar en local contra el PostgreSQL de `compose.yml` (imprime "Migraciones verificadas sobre base limpia"). Esta carpeta no es la raíz de un repositorio: para usar el flujo en GitHub Actions, cópialo a `.github/workflows/` de tu repositorio y fija `vars.DIRECTORIO` a la carpeta del proyecto.
