# Solución de referencia — taller 03

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Cómo ejecutarla y probarla

Necesitas JDK 21. Desde esta carpeta:

```bash
javac -Xlint:all -Werror -d salida $(find src pruebas -name '*.java')
java -cp salida tienda.TodasLasPruebas
rm -rf salida
```

Resultado esperado: **66 comprobaciones, 0 fallos**.

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
