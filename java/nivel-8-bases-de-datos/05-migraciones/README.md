# 05 — Migraciones y evolución del esquema

## Idea

El código tiene Git: cada cambio queda registrado, se puede revisar y reproducir. La base de datos necesita lo mismo. Sin ello, el esquema de producción es el resultado de comandos que alguien ejecutó a mano y que nadie recuerda.

## Lo esencial

- **Migración:** un script versionado que lleva el esquema de una versión a la siguiente.
- **Herramientas:** Flyway (scripts SQL numerados: `V7__agregar_estado_a_pedidos.sql`) y Liquibase (registros de cambios en XML, YAML o SQL). Ambas guardan en una tabla qué migraciones se aplicaron.
- Las migraciones viven en el repositorio, se revisan como código y se ejecutan automáticamente al desplegar.
- **Una migración aplicada es inmutable.** Un error se corrige con otra migración, nunca editando la anterior.
- **`ddl-auto=update` de Hibernate no es una herramienta de migración.** En producción: `validate` o `none`.

### El problema real: cambiar sin detener el servicio

Durante un despliegue conviven la versión vieja y la nueva de la aplicación contra la misma base. Un cambio de esquema incompatible rompe una de las dos.

**Patrón expandir y contraer:**

| Fase | Qué se hace | Ejemplo: renombrar `nombre` a `nombre_completo` |
|---|---|---|
| 1. Expandir | Agregar lo nuevo sin quitar lo viejo | Agregar la columna `nombre_completo` |
| 2. Migrar | La aplicación escribe en ambas; se copian los datos existentes | Escritura doble y relleno por lotes |
| 3. Cambiar | La aplicación lee de lo nuevo | Desplegar la versión que usa `nombre_completo` |
| 4. Contraer | Eliminar lo viejo, cuando ya nadie lo usa | Eliminar `nombre` en un despliegue posterior |

Cada paso es compatible con la versión anterior de la aplicación y se puede revertir.

### Cambios peligrosos

| Cambio | Riesgo | Forma segura |
|---|---|---|
| Agregar columna `NOT NULL` sin valor por defecto | Falla o bloquea la tabla | Agregar nula, rellenar, luego restringir |
| Crear un índice | Bloquea escrituras | `CREATE INDEX CONCURRENTLY` |
| Renombrar o eliminar columna | Rompe la versión en ejecución | Expandir y contraer |
| Cambiar el tipo de una columna | Reescribe la tabla | Columna nueva y migración gradual |
| `UPDATE` de millones de filas | Bloqueo prolongado y transacción enorme | Por lotes |
| Agregar clave foránea | Valida toda la tabla con bloqueo | `NOT VALID` y validar después |

## Ejemplo

```sql
-- V12__agregar_canal_a_pedidos.sql
ALTER TABLE pedidos ADD COLUMN canal VARCHAR(20);

-- V13__rellenar_canal.sql  (en lotes si la tabla es grande)
UPDATE pedidos SET canal = 'WEB' WHERE canal IS NULL;

-- V14__canal_obligatorio.sql
ALTER TABLE pedidos ALTER COLUMN canal SET NOT NULL;
```

```yaml
spring:
  jpa.hibernate.ddl-auto: validate     # Hibernate comprueba; no modifica
  flyway.enabled: true
```

## Errores comunes

- `ddl-auto=update` en producción.
- Editar una migración ya aplicada (cambia su suma de verificación y falla el arranque).
- Migraciones probadas solo con tablas vacías.
- Cambios de esquema y de código incompatibles en el mismo despliegue.
- Sin plan de reversa.
- Migraciones largas ejecutadas al arrancar con varias réplicas a la vez.
- Datos de prueba mezclados con migraciones de esquema.
- Cambios manuales en producción que el repositorio desconoce.

## Taller

1. Configura Flyway en un proyecto Spring Boot con tres migraciones.
2. Edita una migración ya aplicada y explica el error.
3. Aplica expandir y contraer para renombrar una columna en uso, en cuatro despliegues. En cada paso, comprueba que la versión anterior de la aplicación sigue funcionando.
4. Agrega una columna obligatoria a una tabla con un millón de filas sin bloquearla más de un instante.
5. Escribe el plan de reversa de cada paso.
6. Agrega al flujo de integración continua una comprobación que aplique las migraciones a una base limpia.

Rama `taller/n8-05-migraciones`.

## Preguntas senior

1. ¿Por qué no usar `ddl-auto=update`?
2. Explica expandir y contraer con un ejemplo distinto al de la lección.
3. ¿Cómo despliegas un cambio de esquema sin tiempo de inactividad?
4. ¿Cómo reviertes una migración que eliminó datos?
5. ¿Cómo migras una tabla de cientos de millones de filas?
6. ¿Quién ejecuta las migraciones cuando hay diez réplicas arrancando a la vez?
