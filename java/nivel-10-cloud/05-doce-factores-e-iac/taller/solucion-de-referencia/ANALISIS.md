# Taller 10-05: doce factores e infraestructura como código

Aplicación evaluada: `pedidos` (Spring Boot 4.1.2, Java 21). Todo lo marcado como comprobado lo ejecuta `taller.sh` con OpenTofu 1.13.9, LocalStack 4.14.0, Redis 7 y PostgreSQL 16.

## 1. Evaluación contra los doce factores

| # | Factor | Estado | Incumplimiento y corrección |
|---|---|---|---|
| 1 | Base de código | Cumple | Un repositorio, imágenes etiquetadas con el commit en CD |
| 2 | Dependencias | Cumple | `pom.xml` y `mvnw`; sin instalaciones manuales. Corregir: fijar snappy-java/lz4-java (hallazgo de Trivy en 10-02) |
| 3 | Configuración | Parcial | `application.yml` sin datos de conexión; la base llega por `SPRING_DATASOURCE_*`. Corregir: el ejemplo del README del perfil usaba `BD_URL`/`BD_USUARIO`/`BD_CLAVE`, que no son las variables que Spring lee por sí solo (ver el apartado «Hallazgos sobre el README») |
| 4 | Servicios de respaldo | Cumple | Base, Redis, Kafka y S3 por configuración |
| 5 | Construir, publicar, ejecutar | Cumple | Dockerfile multietapa; una imagen para todos los ambientes (visto en 10-02) |
| 6 | Procesos | **Incumple por defecto** | La sesión vive en la memoria de la instancia (medido: 1,1,1). Corrección: perfil `redis` (medido: 1,2,3). Los archivos van a S3, no al disco |
| 7 | Vinculación de puertos | Cumple | Tomcat embebido, `SERVER_PORT` |
| 8 | Concurrencia | Cumple | Réplicas del Deployment (3, 5); hilos virtuales |
| 9 | Desechabilidad | Cumple tras 10-03 | Arranque de ~2,3 s, sondas, apagado ordenado más `preStop` (0 fallos medidos) |
| 10 | Paridad | Cumple | PostgreSQL en desarrollo y en pruebas; Flyway en el pipeline |
| 11 | Registros | Cumple | Salida estándar (`kubectl logs`) |
| 12 | Procesos de administración | Parcial | Las migraciones corren al arrancar. Corrección: un `Job` aparte para migraciones destructivas |

Tabla mínima de incumplimientos con corrección: 3 (variables), 6 (sesión y archivos), 12 (tareas puntuales).

## 2. Sin estado local (comprobado)

Dos instancias del mismo JAR contra el mismo Redis y el mismo bucket de LocalStack:

| Escenario | Visitas pidiendo A, B, A | Archivo subido por A, bajado por B |
|---|---|---|
| Sin perfil `redis` | 1, 1, 1 (la sesión no existe en la otra instancia) | n/a |
| Con perfil `redis` | 1, 2, 3 | Idéntico contenido, y el objeto está en S3 |

## 3. Configuración y secretos fuera del repositorio (comprobado)

Trivy (escáner de secretos) no encuentra nada en `pedidos`, no hay claves en `application*.yml`, y la clave de la base en OpenTofu entra solo por `TF_VAR_clave_bd`. El repositorio podría ser público. El estado de OpenTofu contiene el secreto en texto claro, así que **no se deja en el disco**: `infra/backend.hcl` configura el backend `s3` (bucket `tofu-estado` con versionado, en LocalStack) con `use_lockfile = true`. `taller.sh` comprueba que el estado de los dos ambientes está en el bucket, que no existe ningún `terraform.tfstate` local ni el secreto en ningún archivo del directorio, y que un segundo `plan` concurrente falla con `Error acquiring the state lock` mientras un `apply` espera confirmación. En AWS real el bucket se cifra con KMS y se restringe por IAM; aquí el cifrado no se demuestra.

## 4-5. Infraestructura mínima y dos ambientes (comprobado)

`infra/main.tf`: bucket privado, rol con política mínima y secreto en Secrets Manager; los ambientes `pruebas` y `produccion` salen del mismo código con `pruebas.tfvars` y `produccion.tfvars` (versionado `Suspended` frente a `Enabled`), un workspace de estado por ambiente. Una precondición en el bucket (`terraform.workspace == var.ambiente`) rechaza aplicar el `tfvars` equivocado a un workspace (comprobado).

## 6. Deriva (comprobado)

`tofu plan -detailed-exitcode` devuelve 0 sin cambios. Tras suspender a mano el versionado y añadir una etiqueta `manual`, devuelve 2 y el plan muestra `status = "Suspended" -> "Enabled"` y la etiqueta a retirar. `apply` corrige y `plan` vuelve a 0.

## 7. Destruir y reconstruir (medido)

`taller.sh` lo mide: con LocalStack toma unos segundos (destruir ≈ 6 s, reconstruir ≈ 5 s). En AWS real, solo una base RDS Multi-AZ tarda de 10 a 20 minutos en crearse: la cifra local no sirve como estimación de producción; sirve para comprobar que el código reconstruye el mismo ambiente (el `plan` posterior sale sin cambios).

## Hallazgos sobre el README

- El ejemplo `aws_db_instance` no declaraba `username`, obligatorio; se añadió en el README (`tofu validate` lo exige).
- `aws_db_instance` no se puede aplicar en LocalStack Community (RDS es de pago): `tofu plan` sí funciona (3 recursos a crear) y es lo que se comprueba.
- El ejemplo del perfil `doce-factores` usa `BD_URL`, `BD_USUARIO` y `BD_CLAVE`: no son las variables que Spring lee por sí solo (`SPRING_DATASOURCE_*`), pero funcionan porque el archivo del perfil las referencia con `${...}`. `taller.sh` arranca la aplicación con ese perfil y comprueba que conecta.
