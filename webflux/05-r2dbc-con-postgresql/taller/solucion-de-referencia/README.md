# Solución de referencia: taller 05

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Necesita JDK 21, Maven y Docker. Dos proyectos: `r2dbc/` (la solución) y `jdbc/` (la comparación de rendimiento).

Levanta el entorno del curso (desde la raíz del repositorio):

```bash
docker compose -f webflux/entorno/compose.yaml up -d --wait
```

Crea la base `pedidos05` y carga el esquema y los datos (las pruebas los suponen):

```bash
cd webflux/05-r2dbc-con-postgresql/taller/solucion-de-referencia
docker compose -f ../../../entorno/compose.yaml exec -T postgres psql -U banco -d banco -c "create database pedidos05"
for f in esquema.sql datos.sql; do docker compose -f ../../../entorno/compose.yaml exec -T postgres psql -q -v ON_ERROR_STOP=1 -U banco -d pedidos05 < $f; done
```

Pruebas (8, contra PostgreSQL en `localhost:5433`):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21; export PATH=$JAVA_HOME/bin:$PATH   # o tu JDK 21
cd r2dbc && mvn test
```

Para arrancar cada versión (puerto 8080, una a la vez): `cd r2dbc && mvn -q package -DskipTests && java -jar target/t05-r2dbc-1.0.0.jar`, o la de `jdbc/` con `target/t05-jdbc-1.0.0.jar`.

El razonamiento, las mediciones y las decisiones están en [ANALISIS.md](ANALISIS.md).
