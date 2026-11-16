# Taller 04 — Refactoring: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Trabaja en `../solucion/` y vuelve aquí para comparar.

## Cómo ejecutarla

Requiere JDK 21. Desde esta carpeta:

```bash
./ejecutar-pasos.sh
```

Requiere además Maven 3.9 o superior y git. `pasos/` tiene el código completo de cada paso, el mensaje de su commit (`MENSAJE`) y el resultado esperado de `mvn test` (`ESPERADO`). El script los aplica en orden sobre un repositorio temporal, ejecuta las pruebas en cada paso y muestra el historial de commits. Si quieres explorar ese historial: `./ejecutar-pasos.sh /ruta/a/una-carpeta-nueva` y luego `git log` allí.

Para ejecutar solo las pruebas del resultado final: copia `pasos/09-corregir-redondeo-de-la-base/src` junto a `pom.xml` en una carpeta nueva y ejecuta `mvn test`.

El razonamiento, punto por punto, está en [ANALISIS.md](ANALISIS.md).
