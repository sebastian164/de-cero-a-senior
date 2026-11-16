# Solución de referencia: AOP, proxies y `@Transactional` por dentro

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta es una solución de referencia, no la única válida.

Requisitos: JDK 21 y Maven 3.9 o superior.

## Base de datos

Las pruebas usan PostgreSQL 17 (usuario, clave y base `curso`/`curso`/`taller`). Levántala con Docker:

```bash
docker compose up -d --wait
```

Si el puerto 5432 está ocupado, usa otro: `PG_PORT=5433 docker compose up -d --wait` y pasa `-Dpg.url=jdbc:postgresql://localhost:5433/taller` a Maven. Al terminar: `docker compose down -v`.

## Ejecutarla y probarla

Desde esta carpeta:

```bash
mvn -B -ntp clean test
```

## Qué se hizo y por qué

Las decisiones, los resultados medidos y los hallazgos están en [ANALISIS.md](ANALISIS.md).
