# Solución de referencia: taller 06 (el pool en un servicio hexagonal)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta es una solución de referencia, no la única válida.

Los adaptadores JDBC con HikariCP del servicio de pedidos del módulo [hexagonal](../../../../hexagonal/), debajo de su unidad de trabajo. El dominio y la aplicación no se copian: se compilan desde [`hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia`](../../../../hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia), y el esquema sale de [`hexagonal/07-persistencia-con-orm/taller/solucion-de-referencia`](../../../../hexagonal/07-persistencia-con-orm/taller/solucion-de-referencia). Necesitas ese código del curso en su sitio.

## Cómo ejecutarla y sus pruebas

Necesitas JDK 21, Maven, Docker, `k6`, `jq` y `curl`. Desde esta carpeta:

```bash
./medir.sh
```

`medir.sh` compila, levanta PostgreSQL con `compose.yaml` (puerto 5432; cámbialo con `DB_PUERTO=<puerto>`), ejecuta las pruebas (`mvn test`: conexiones falsas, contratos de pedidos e inventario contra PostgreSQL, bandeja de salida, unidad anidada con pool de 1 y la comprobación de arquitectura) y después las mediciones con k6 de los pasos 5 a 8 (20 s por medida). Escribe `resultados/resultados.md`. Dura unos 3 minutos y termina con código 0 solo si todos los criterios se cumplen.

Solo las pruebas, con la base levantada y en un esquema propio (otros talleres crean en `public` tablas `pedido` con otras columnas):

```bash
docker compose -p hikaricp-taller -f compose.yaml up -d --wait db
docker compose -p hikaricp-taller -f compose.yaml exec -T db psql -U postgres -d tienda -c "create schema if not exists t06"
DB_URL='jdbc:postgresql://localhost:5432/tienda?currentSchema=t06' DB_USUARIO=postgres DB_PASSWORD=local mvn -q -B test
```

Cuando termines, baja la base: `docker compose -p hikaricp-taller -f compose.yaml down -v`.

## Qué hay

- `src/main/java/tienda/adaptadores/salida/jdbc/`: `UnidadDeTrabajoJdbc` (el único sitio con `getConnection`) y los repositorios.
- `src/main/java/tienda/composicion/TiendaConPool.java`: la raíz de composición con el tamaño del pool.
- `medir.sh` y `herramientas/` (`pedidos.js` de k6, `Externo.java`, `lib.sh`).

El razonamiento y las cifras están en [ANALISIS.md](ANALISIS.md).
