# Solución de referencia — taller 01

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Cómo ejecutarla

Reproduce los pasos 4, 5 y 6 contra el código de inicio (`../inicio/Pedidos.java`) sin modificarlo. Necesitas JDK 21. Desde esta carpeta:

```bash
javac -d salida ../inicio/Pedidos.java Reproducir.java
java -Djava.io.tmpdir=salida -cp salida Reproducir
rm -rf salida
```

Termina con código 0 si todas las afirmaciones del análisis se cumplen. El dibujo del paso 8 está en [`DIBUJO.md`](DIBUJO.md).

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
