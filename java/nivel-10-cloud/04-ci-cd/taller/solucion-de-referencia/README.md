# Solución de referencia: taller 04 (CI/CD)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

El script lee el README de la sesión, así que esta carpeta debe seguir dentro del repositorio del curso.

## Qué instalar

- Docker (Docker Desktop o Docker Engine), con al menos 6 GB de memoria y unos 15 GB de disco libres.
- [act](https://github.com/nektos/act) (`act`): ejecuta GitHub Actions en local. La primera vez el script descarga la imagen `catthehacker/ubuntu:act-24.04` (varios GB).
- [kind](https://kind.sigs.k8s.io/) (`kind`) y `kubectl`.
- `git`, `jq`, `python3`, `curl`, `rsync` y `perl`.
- Red en la primera ejecución: se descargan las acciones de GitHub, las bases de datos de Trivy (unos 3 GB, en `~/.cache/curso-de-cero-a-senior/trivy`) y un clon parcial de `google/gson` para `dora.sh`.
- No hace falta instalar Java ni Maven: todo se compila dentro de Docker.

## Cómo ejecutarla y probarla

Desde esta carpeta:

```bash
./taller.sh
```

El script **levanta** un registro de imágenes (`registry:2`, puerto 5001), un clúster kind llamado `pedidos` (API en el 6443, Service en el 8081) con la aplicación y PostgreSQL de la sesión 03, y un repositorio git local que hace de remoto. Después ejecuta con `act` los workflows `ci.yml` y `cd.yml` y comprueba cada resultado: pipeline en verde y en rojo, rama protegida, despliegue por digest, prueba de humo, reversa automática, las métricas de `dora.sh` y el workflow del README. Dura entre 10 y 20 minutos. **Al terminar, incluso si algo falla, lo baja todo**: clúster, registro, contenedores y volúmenes de `act`, e imágenes. Usa un archivo de configuración temporal para `kubectl`: no toca tu `~/.kube/config`.

Termina con código 0 solo si todas las comprobaciones pasan. Si algún puerto o el nombre del clúster están ocupados: `CLUSTER=otro PUERTO_REGISTRO=15001 PUERTO_SERVICIO=18081 PUERTO_API=16443 PUERTO_BD_CI=15433 ./taller.sh`.

Para crear y borrar el entorno a mano (por ejemplo, para probar tus propios workflows):

```bash
docker run -d --name registro -p 127.0.0.1:5001:5000 registry:2
kind create cluster --name pedidos
# ... tu trabajo ...
kind delete cluster --name pedidos
docker rm -f -v registro
```

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
