# Solución de referencia — taller 07

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Cómo ejecutarla y probarla

Necesitas JDK 21, Maven 3.9 o superior y Docker. El simulacro levanta por su cuenta todo el sistema con un `compose.yaml` propio (MongoDB, Redis y dos instancias del servicio en contenedores, en los puertos 28017, 26379, 18081 y 18082), y además `TransaccionHuerfanaTest` usa el MongoDB del entorno del curso (27017), así que levántalo antes:

```bash
docker compose -p nosql -f ../../../entorno/compose.yaml up -d --wait
```

Compila el núcleo hexagonal y los talleres 04, 05 y 06 desde sus carpetas, sin copiarlos. La imagen del servicio se construye con el jar y sus dependencias; déjalos listos y ejecuta las pruebas, desde esta carpeta:

```bash
./preparar-imagen.sh
mvn -B test
```

Resultado esperado: `SimulacroTest` (tabla del simulacro, con el verificador de invariantes tras cada prueba) y `TransaccionHuerfanaTest`, con `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`. Tarda varios minutos. Lo medido queda en `salida/`.

Para levantar el sistema a mano: `docker compose -p nosql-integrador up -d --build --wait` (tras `./preparar-imagen.sh`) y `docker compose -p nosql-integrador down -v` al terminar.

Al terminar, borra lo generado y baja el entorno:

```bash
mvn clean && rm -rf salida
docker rmi nosql-pedidos:1
docker compose -p nosql -f ../../../entorno/compose.yaml down -v
```

Las decisiones y las mediciones del simulacro están en [`ANALISIS.md`](ANALISIS.md); la defensa del diseño, en [`DEFENSA.md`](DEFENSA.md).
