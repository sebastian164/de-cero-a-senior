# Solución de referencia: taller TDD 04, la calidad real de las pruebas

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

Dos proyectos Maven autónomos (JDK 21 y Maven): `kata/` (la calculadora de cadenas, con pruebas de mutantes, de propiedades con jqwik y de regresión) y `bicis/` (el caso de uso de devolver una bicicleta, con las pruebas nuevas). Tu propia entrega va en `taller/solucion/`.

## Ejecutarla y probarla

```bash
(cd kata && mvn test)
(cd bicis && mvn test)
```

Cobertura (JaCoCo) y mutación (PIT) de los cinco juegos de la tabla de `ANALISIS.md`, con los informes HTML en `informes/`:

```bash
./medir.sh
diff resumen.esperado informes/resumen.txt   # sin salida: los números coinciden
```

`demostraciones.sh` repite dos demostraciones del análisis (un error introducido a propósito y un mutante equivalente) sobre copias temporales.
Si ejecutas `medir.sh`, borra después `kata/target`, `bicis/target` e `informes/`.

La política de pruebas del paso 11 está en [POLITICA.md](POLITICA.md) y las respuestas a cada paso en [ANALISIS.md](ANALISIS.md).
