# Taller 09 — JUnit 5: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Trabaja en `../solucion/` y vuelve aquí para comparar.

## Cómo ejecutarla

Requiere JDK 21. Desde esta carpeta:

```bash
mvn -B clean verify
```

Requiere además Maven 3.9 o superior. Ejecuta las 40 pruebas y mide la cobertura con JaCoCo (`target/site/jacoco/index.html`). Para el puntaje de mutación con PIT: `mvn -B test-compile pitest:mutationCoverage` (informe en `target/pit-reports/`). `sin-aserciones/` tiene dos pruebas que ejecutan todo el código y no comprueban nada: cámbialas por las de `src/test/java` y compara la cobertura con el puntaje de mutación.

El razonamiento, punto por punto, está en [ANALISIS.md](ANALISIS.md).
