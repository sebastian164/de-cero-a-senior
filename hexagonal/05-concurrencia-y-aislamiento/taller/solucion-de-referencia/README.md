# Solución de referencia — taller 05

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Cómo ejecutarla y probarla

Necesitas JDK 21. Desde esta carpeta:

```bash
javac -Xlint:all -Werror -d salida $(find src pruebas -name '*.java')
java -cp salida tienda.TodasLasPruebas
rm -rf salida
```

Resultado esperado: **108 comprobaciones, 0 fallos**.

La prueba con hilos, diez veces seguidas (paso 7), debe dar **40 comprobaciones, 0 fallos**:

```bash
javac -d salida $(find src pruebas -name '*.java')
java -cp salida tienda.DiezVeces
rm -rf salida
```

La prueba determinista del paso 1 (`paso1/Paso1.java`) se compila contra el adaptador de la sesión 04 y contra el de esta sesión. Con el de la 04 el último monitor se vende dos veces (`vendidas=2 quedan=0`); con el de la 05, una (`vendidas=1 quedan=0`):

```bash
javac -d salida $(find ../../../04-transacciones/taller/solucion-de-referencia/src -name '*.java') paso1/Paso1.java && java -cp salida Paso1
rm -rf salida
javac -d salida $(find src -name '*.java') paso1/Paso1.java && java -cp salida Paso1
rm -rf salida
```

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
