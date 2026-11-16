# Taller 11 — TDD e integración: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Trabaja en `../solucion/` y vuelve aquí para comparar.

Dos proyectos independientes, cada uno con su propio `pom.xml`:

## Parte A: kata de precio de parqueadero (`parte-a-parqueadero/`)

Requiere JDK 21, Maven 3.9 o superior y git. Desde esta carpeta:

```bash
(cd parte-a-parqueadero && \
./ejecutar-ciclos.sh)
```

`ciclos/` tiene un paso por carpeta (rojo, verde, refactor). El script los aplica en orden sobre un repositorio temporal, comprueba que cada rojo falla y cada verde pasa, y muestra el historial. Con `./ejecutar-ciclos.sh /ruta/a/una-carpeta-nueva` conservas el repositorio para explorarlo con `git log`.

## Parte B: pruebas de integración (`parte-b-spring-integracion/`)

Requiere además Docker en marcha: las pruebas levantan PostgreSQL 16 con Testcontainers.

```bash
(cd parte-b-spring-integracion && \
mvn -B clean test)
```

Ejecuta 12 pruebas: `@DataJpaTest`, `@WebMvcTest`, `@SpringBootTest` y transacciones.

El razonamiento, punto por punto, está en [ANALISIS.md](ANALISIS.md).
