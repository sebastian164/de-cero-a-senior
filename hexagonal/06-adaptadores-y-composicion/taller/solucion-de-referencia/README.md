# Solución de referencia — taller 06

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Cómo ejecutarla y probarla

Necesitas JDK 21. Desde esta carpeta:

```bash
javac -Xlint:all -Werror -d salida $(find src pruebas -name '*.java')
java -cp salida tienda.TodasLasPruebas
rm -rf salida
```

Resultado esperado: **137 comprobaciones, 0 fallos**.

Para usar la aplicación (puerto 8080 en modo HTTP):

```bash
javac -d salida $(find src -name '*.java')
printf 'crear k-1 ana teclado:2 raton:1\nconfirmar pedido-1\n' | java -cp salida tienda.Main consola
java -cp salida tienda.Main http 8080     # en otra terminal: curl como en el README de la sesión
rm -rf salida
```

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
