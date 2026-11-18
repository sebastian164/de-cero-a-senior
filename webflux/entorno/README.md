# Entorno de los talleres de programación reactiva

PostgreSQL y MongoDB en contenedores. Los puertos no son los habituales (5433 y 27018) para que el entorno conviva con los de otros módulos del curso.

| Servicio | Desde tu equipo | Bases | Sesiones |
|---|---|---|---|
| PostgreSQL 18.7 | `localhost:5433`, usuario y contraseña `banco` | `banco` (sesiones 07 y 08) y `cuentas` (sesión 05) | 05, 07, 08 |
| MongoDB 9.0.8 | `mongodb://localhost:27018/banco?directConnection=true` | `banco` | 06 |

<!-- file: compose.yaml -->
```yaml
# PostgreSQL y MongoDB para los talleres de programación reactiva.
# Puertos distintos de los habituales (5433 y 27018) para convivir con otros entornos del curso.
services:
  postgres:
    image: postgres:18.7
    environment:
      POSTGRES_DB: banco
      POSTGRES_USER: banco
      POSTGRES_PASSWORD: banco          # solo para el taller
    ports:
      - "5433:5432"
    volumes:
      - ./initdb:/docker-entrypoint-initdb.d:ro   # crea la base "cuentas" de la sesión 05
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U banco -d banco"]
      interval: 5s
      timeout: 3s
      retries: 10

  mongo:
    image: mongo:9.0.8
    # Los flujos de cambios (sesión 06) solo existen en un conjunto de réplicas. Uno de un solo nodo basta.
    # La caché se limita a 256 MB: en un portátil, MongoDB no necesita más para estos talleres.
    command: ["--replSet", "rs0", "--bind_ip_all", "--wiredTigerCacheSizeGB", "0.25"]
    ports:
      - "27018:27017"
    healthcheck:
      # Inicia el conjunto de réplicas la primera vez; después solo comprueba que responde.
      test: ["CMD", "mongosh", "--quiet", "--eval", "try { rs.status().ok } catch (e) { rs.initiate({_id: 'rs0', members: [{_id: 0, host: 'localhost:27017'}]}).ok }"]
      interval: 5s
      timeout: 5s
      retries: 20
```

<!-- file: initdb/01-bases.sql -->
```sql
-- Se ejecuta una sola vez, al crear el contenedor. La base "banco" ya la crea POSTGRES_DB.
create database cuentas owner banco;
```

## Uso

Desde esta carpeta. El nombre de proyecto `-p webflux` evita choques con otros entornos del curso:

```bash
docker compose -p webflux up -d --wait     # espera a que los dos estén "healthy"
docker compose -p webflux ps
docker compose -p webflux down -v          # detiene y borra los datos
```

Para mirar dentro:

```bash
docker compose -p webflux exec postgres psql -U banco -d banco
docker compose -p webflux exec mongo mongosh banco
```

Consultas útiles en PostgreSQL:

```sql
-- Conexiones abiertas por cada aplicación y en qué estado
select application_name, state, count(*) from pg_stat_activity where datname in ('banco', 'cuentas') group by 1, 2;

-- Quién está esperando un bloqueo, y quién lo tiene
select pid, wait_event_type, wait_event, left(query, 60) from pg_stat_activity where wait_event_type = 'Lock';
```

## Por qué MongoDB arranca como conjunto de réplicas

Los flujos de cambios de la sesión 06 solo existen en un conjunto de réplicas. Un conjunto de **un solo nodo** basta: se arranca con `--replSet rs0` y el *healthcheck* lo inicia la primera vez (`rs.initiate`). El miembro se anuncia como `localhost:27017`, la dirección que tiene **dentro** del contenedor. Por eso, desde tu equipo, la URI lleva `directConnection=true`: sin ella, el driver intentaría conectarse a `localhost:27017`, que en tu equipo es otro puerto (u otro MongoDB).

La caché de WiredTiger se limita a 256 MB: por defecto MongoDB toma la mitad de la memoria del contenedor, y en un portátil con varios entornos a la vez eso compite con todo lo demás.

## Estado

Al preparar el curso, este entorno se levantó con Docker 29 y se usó para ejecutar todos los ejemplos de las sesiones 05 a 08: las dos bases quedaron *healthy*, la base `cuentas` se creó con el script de `initdb` y el conjunto de réplicas quedó como `PRIMARY`. Las etiquetas `postgres:18.7` y `mongo:9.0.8` eran las últimas estables publicadas; usa las vigentes y fíjalas.
