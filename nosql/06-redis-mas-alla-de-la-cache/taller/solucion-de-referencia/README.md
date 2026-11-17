# Solución de referencia — taller 06

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Cómo ejecutarla y probarla

Necesitas JDK 21, Maven 3.9 o superior y Docker. Las pruebas usan el [entorno del curso](../../../entorno/) (MongoDB como replica set y Redis en los puertos 27017 y 6379); levántalo antes:

```bash
docker compose -p nosql -f ../../../entorno/compose.yaml up -d --wait
```

Compila el núcleo hexagonal y los talleres [04](../../../04-mongodb-en-el-banco-hexagonal/taller/solucion-de-referencia/) y [05](../../../05-redis-como-cache/taller/solucion-de-referencia/) desde sus carpetas, sin copiarlos. Las pruebas detienen el contenedor `redis` del entorno (si usas otro nombre de proyecto, `-Dinfra.proyecto=NOMBRE`) y el paso 7 levanta y baja por su cuenta un segundo Redis de 5 MB (`estado.yaml`, puerto 6380). Desde esta carpeta:

```bash
mvn -B test
```

Resultado esperado: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`. Lo medido queda en `salida/resultados.txt`.

Al terminar, borra lo generado y baja el entorno:

```bash
mvn clean && rm -rf salida
docker compose -p nosql -f ../../../entorno/compose.yaml down -v
```

Las decisiones, las mediciones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
