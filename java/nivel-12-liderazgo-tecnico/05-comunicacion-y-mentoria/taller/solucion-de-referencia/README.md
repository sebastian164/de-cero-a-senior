# Solución de referencia: taller 12-05 (comunicación y mentoría)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Qué es

Los materiales del taller en [`ANALISIS.md`](ANALISIS.md): la defensa de diez minutos, la misma propuesta para dos audiencias, el guion de mentoría, tres retroalimentaciones y el balance del curso. Lo que pide hablar con otra persona no se puede ejecutar solo: se entregan los materiales y queda por hacer con una persona real.

Lo único ejecutable es el README de `servicio-pedidos/`, el que debería permitir a alguien nuevo arrancar el servicio sin ayuda.

## Qué instalar

Docker (Docker Desktop o Docker Engine con el plugin `compose`), JDK 21, `curl` y `jq`.

## Cómo ejecutarla y probarla

Desde esta carpeta:

```bash
./verificar-readme.sh
```

Extrae los comandos del propio `servicio-pedidos/README.md`, los ejecuta tal como están escritos (levanta la aplicación y PostgreSQL con Compose en el puerto 8080, crea un pedido con `curl` y comprueba que llegó a la base, ejecuta `./mvnw verify` y provoca el fallo descrito en "Si algo falla") y **lo baja todo al terminar**. Si el puerto 8080 está ocupado: `PUERTO_APP=18080 ./verificar-readme.sh`. Termina con código 0 solo si todo pasa.

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
