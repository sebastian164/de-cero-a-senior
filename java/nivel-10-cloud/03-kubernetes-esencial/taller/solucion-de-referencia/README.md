# Solución de referencia: taller 03 (Kubernetes esencial)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

El script lee el README de la sesión, así que esta carpeta debe seguir dentro del repositorio del curso.

## Qué instalar

- Docker (Docker Desktop o Docker Engine), con al menos 4 GB de memoria.
- [kind](https://kind.sigs.k8s.io/) (`kind`) y `kubectl`.
- [k6](https://k6.io/) (`k6`) para la carga.
- `curl` y `jq`.
- No hace falta instalar Java ni Maven: la aplicación se compila dentro de Docker.

## Cómo ejecutarla y probarla

Desde esta carpeta:

```bash
./taller.sh
```

El script construye la imagen, **crea un clúster kind** llamado `pedidos` (API en el puerto 6443, Service publicado en el 8081), despliega la aplicación y PostgreSQL, y recorre los seis pasos del taller con comprobaciones. Dura unos 15 minutos, la mayor parte en las tres cargas del paso 6. **Al terminar, incluso si algo falla, borra el clúster** y las imágenes que creó. Usa un archivo de configuración temporal para `kubectl`: no toca tu `~/.kube/config`.

Termina con código 0 solo si todas las comprobaciones pasan. Si algún puerto o el nombre están ocupados, cámbialos: `CLUSTER=otro PUERTO_API=16443 PUERTO_SERVICIO=18081 PUERTO_FORWARD=18083 ./taller.sh`.

Para crear y borrar el clúster a mano (por ejemplo, para probar tus propios manifiestos):

```bash
kind create cluster --name pedidos
kind delete cluster --name pedidos
```

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
