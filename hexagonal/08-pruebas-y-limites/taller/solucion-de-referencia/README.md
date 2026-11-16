# Solución de referencia — taller 08

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Cómo ejecutarla y probarla

Necesitas JDK 21. Desde esta carpeta:

```bash
javac -Xlint:all -Werror -d salida $(find src pruebas -name '*.java')
java -cp salida tienda.TodasLasPruebas
rm -rf salida
```

Resultado esperado: **156 comprobaciones, 0 fallos**.

Paso 3, romper la regla de dependencias cuatro veces y comprobar que la prueba de arquitectura avisa cada vez:

```bash
./romper-arquitectura.sh "$(mktemp -d)"
```

Paso 2, los contratos contra PostgreSQL 17 con JPA (necesita Maven y Docker; usa también la solución de la sesión 07 sin copiarla):

```bash
cd jpa && mvn -B clean test && mvn clean
```

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
