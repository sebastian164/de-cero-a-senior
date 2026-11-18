# Solución de referencia: taller 05 (doce factores e infraestructura como código)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

El script lee el README de la sesión, así que esta carpeta debe seguir dentro del repositorio del curso.

## Qué instalar

- Docker (Docker Desktop o Docker Engine con el plugin `compose`).
- [OpenTofu](https://opentofu.org/) 1.13 o superior (`tofu`) y AWS CLI v2 (`aws`).
- JDK 21 (`java`): el script compila la aplicación con el Maven Wrapper de `aplicacion/` y la ejecuta fuera de Docker.
- [Trivy](https://trivy.dev/) (`trivy`), para el análisis de secretos.
- `jq`, `curl` y `rsync`.

LocalStack, Redis y PostgreSQL los levanta el script con Docker. No necesitas una cuenta de AWS.

## Cómo ejecutarla y probarla

Desde esta carpeta:

```bash
./taller.sh
```

El script:

1. **Levanta** LocalStack (puerto 4566), Redis (6379) y PostgreSQL (5432) con `docker compose -p n10-05 -f compose.yaml up -d --wait`.
2. Crea un bucket de estado con versionado y aplica `infra/` con **estado remoto y bloqueo** en dos ambientes (`pruebas` y `produccion`) con el mismo código.
3. Comprueba la precondición contra el `tfvars` equivocado, el bloqueo con un segundo `plan` concurrente, la deriva (`plan -detailed-exitcode`) y el ciclo destruir y reconstruir.
4. Valida y planifica el fragmento `aws_db_instance` del README (en `iac-readme/`).
5. Arranca dos instancias de la aplicación (puertos 8084 y 8085): sin Redis la sesión se pierde al cambiar de instancia, con el perfil `redis` no; un archivo subido a una instancia lo baja la otra desde S3. Prueba también el perfil `doce-factores` y busca secretos con Trivy.
6. **Baja todo** al terminar (`docker compose down -v`, procesos Java, archivos temporales), incluso si algo falla. El estado de OpenTofu vive en el bucket de LocalStack y desaparece con él; el script trabaja en una copia temporal, así que esta carpeta no se ensucia.

Termina con código 0 solo si todas las comprobaciones pasan. Si un puerto está ocupado: `PUERTO_LOCALSTACK=14566 PUERTO_REDIS=16379 PUERTO_PG=15432 PUERTO_A=18084 PUERTO_B=18085 PUERTO_DOCE=18086 ./taller.sh`.

Para levantar y bajar los servicios a mano:

```bash
docker compose -p n10-05 -f compose.yaml up -d --wait
docker compose -p n10-05 -f compose.yaml down -v
```

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
