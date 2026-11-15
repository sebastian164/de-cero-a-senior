# Entorno de los talleres de HikariCP

Un PostgreSQL en un contenedor, **limitado a 30 conexiones a propósito**: con un límite tan bajo, los problemas de dimensionamiento aparecen con tres instancias y no hacen falta trescientas.

<!-- file: compose.yaml -->
```yaml
# PostgreSQL con un límite de conexiones muy bajo, para que los talleres choquen con él enseguida.
services:
  db:
    image: postgres:17
    command: ["postgres", "-c", "max_connections=30"]
    environment:
      POSTGRES_DB: tienda
      POSTGRES_PASSWORD: local          # solo para el taller
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d tienda"]
      interval: 5s
      timeout: 3s
      retries: 10
```

## Uso

```bash
docker compose up -d
docker compose exec db psql -U postgres -d tienda
```

Consultas que vas a usar en todos los talleres:

```sql
-- Cuántas conexiones hay y en qué estado
select state, count(*) from pg_stat_activity where datname = 'tienda' group by state;

-- Quién lleva más tiempo con una transacción abierta
select pid, state, now() - xact_start as abierta_hace, left(query, 60) as consulta
from pg_stat_activity
where datname = 'tienda' and xact_start is not null
order by xact_start;

-- El límite configurado
show max_connections;

-- Cortar todas las sesiones de la aplicación (para probar cómo se recupera el pool)
select pg_terminate_backend(pid) from pg_stat_activity
where datname = 'tienda' and pid <> pg_backend_pid();

-- Simular una consulta lenta
select pg_sleep(0.5);
```

## Estado

El archivo se validó con `docker compose config`. **No se levantó** al preparar el curso: no había motor de Docker. Las consultas son estándar de PostgreSQL, pero tampoco se ejecutaron.

Para los talleres que piden carga, cualquier herramienta sirve (k6, hey, wrk, JMeter). Lo que importa es poder fijar el número de usuarios concurrentes y leer el percentil 95.
