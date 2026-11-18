# Taller 10-04: CI/CD en local con `act`

Todo corre con `taller.sh`: GitHub Actions ejecutado con `act` 0.2.97 (imagen `catthehacker/ubuntu:act-24.04`), un clúster kind propio (con la aplicación desplegada como en la sesión 03) como entorno, un registro de imágenes `registry:2` y un repositorio git `bare` con un gancho `pre-receive` en lugar de la protección de rama de GitHub. Los workflows son `ci.yml` y `cd.yml`; las cifras de este documento son de una ejecución en una máquina de 12 núcleos.

## 1. Pipeline de pull request (`ci.yml`): compilar, probar, analizar y construir la imagen

Cuatro trabajos en paralelo: `probar` (`./mvnw -B verify`, 3 pruebas), `analizar` (compilación con `-Xlint:all -Werror` y `trivy rootfs --exit-code 1` sobre las dependencias, imagen `aquasec/trivy:0.76.2`: **cualquier hallazgo HIGH o CRITICAL deja el trabajo en rojo**), `migraciones` (PostgreSQL vacío, aplica Flyway desde cero con la aplicación y comprueba `flyway_schema_history`) e `imagen` (`docker build`). Los cuatro terminan en verde con el `pom.xml` del proyecto, que ya lleva fijadas las versiones corregidas de snappy-java y lz4-java (hallazgo del taller 10-02). El taller incluye el caso contrario: una rama que quita ese parche deja `analizar` en rojo y Trivy nombra las dependencias. Una trampa que conviene conocer: `trivy fs` sobre un directorio de JAR sueltos no analiza nada (informa «Not scanned») y sale con 0; por eso el pipeline copia los JAR con `docker cp` a un volumen y usa `trivy rootfs --exit-code 1`.

## 2. Rama principal protegida

`branch-protection.json` es la carga útil de la API de GitHub (verificaciones obligatorias `probar`, `analizar`, `migraciones`, `imagen`, una aprobación, también para administradores); no se aplicó (requiere GitHub). La misma regla se reprodujo con un gancho `pre-receive` en un repositorio remoto local: un cambio que rompe una prueba deja `probar` en rojo y `git push origin cambio:main` se **rechaza**; uno con el pipeline en verde pero sin revisión también se rechaza; con las cuatro verificaciones y una revisión se fusiona.

## 3. Despliegue automático (`cd.yml`)

Construye **una** imagen etiquetada con el commit, hace **`docker push` a un registro real** (`registry:2` en `localhost:5001`), toma el digest (`RepoDigests`) y despliega `localhost:5001/pedidos@sha256:...` con `kubectl set image` dentro del nodo kind, que la descarga del registro (un `hosts.toml` de containerd lo mapea). El taller comprueba que el registro contiene la imagen del commit, que el Deployment ejecuta exactamente ese digest y que el registro recibió la petición de ese digest desde el nodo. Tiempo del despliegue correcto: unos 22 s. Lo único que sigue sin existir es el nombre `registro.example` y la autenticación por OIDC.

## 4. Migraciones en el flujo

Se validan en cada pull request con una base vacía (trabajo `migraciones`) y, en producción, las aplica Flyway al arrancar cada pod. Si la migración falla, el pod no queda listo y el despliegue progresivo se detiene (se vio en el taller 10-03 con una versión que no arranca). Regla: toda migración debe ser compatible con la versión anterior de la aplicación, porque la reversa de la imagen no revierte el esquema.

## 5. Duración y cómo reducirla

| Ejecución | Segundos |
|---|---|
| Caché de Maven vacía, trabajos en paralelo | 32 |
| Con caché de Maven, trabajos en paralelo | 20 |
| Con caché, trabajos en serie (`--concurrent-jobs 1`) | 39 |

Las cifras cambian de una ejecución a otra según la carga de la máquina; lo estable es la relación: el pipeline completo cabe en menos de un minuto y medio, el paralelismo casi duplica la velocidad frente a la ejecución en serie y la caché de Maven **se restaura** en los trabajos que la usan (`tar -xf …cache.tzst` en el registro de `act`). La ganancia por la caché es modesta porque las capas de Docker y la descarga de Maven son rápidas en local; contra un repositorio remoto pesa más. Un caso a vigilar: si la base de vulnerabilidades de Trivy se descarga dentro del pipeline (unos 3 GB la primera vez), el trabajo `analizar` domina el tiempo; por eso el taller la precalienta en una carpeta de tu máquina y el pipeline usa `--skip-db-update --skip-java-db-update`.

## 6. Prueba de humo y reversa automática

Tras `rollout status`, el pipeline consulta `/actuator/health/readiness`, `/api/info` (debe devolver el mensaje del ConfigMap) y crea un pedido a través del Service. Con `romper=true` se despliega una versión que arranca y pasa las sondas pero con una configuración distinta (`APP_MENSAJE=version-rota`): la prueba de humo **falla** y el paso `if: failure()` ejecuta `rollout undo`; el Deployment vuelve a la imagen anterior (otro digest, porque cada commit cambia un recurso del JAR: un comentario en Java no cambia el bytecode y daría la misma imagen) y la variable de la versión rota desaparece del Deployment.

## 7. Las cuatro métricas de entrega (`dora.sh`, sobre un proyecto real con versiones: google/gson)

Se calculan sobre un proyecto de código abierto con versiones publicadas, con el historial fijado en un commit (`e67037d17f`) y solo commits y etiquetas (clon parcial en una caché local). Proxies declarados, porque no se tiene el entorno de producción de ese proyecto: despliegue = versión publicada (etiqueta con número de versión) en la rama principal; tiempo de entrega = desde la fecha del commit hasta la fecha de la primera versión que lo contiene; fallo = versión X.Y.Z seguida en menos de 30 días por una corrección X.Y.(Z+1); recuperación = de la versión con fallo a su corrección.

| Métrica | Valor |
|---|---|
| Frecuencia de despliegue | 0,2 versiones por mes (27 versiones en 127 meses) |
| Tiempo de entrega | mediana 75,7 días, p90 339,9 días (1.092 commits) |
| Porcentaje de versiones con fallo | 22 % (6 de 27) |
| Tiempo de recuperación | mediana 8,1 días |

Ojo con los atajos: el «tiempo de entrega» no es el intervalo entre commits (eso es la inversa de la frecuencia) y la recuperación se mide desde el fallo, no desde el commit previo. Las cifras describen a gson, una biblioteca en mantenimiento, no a un equipo de producto con despliegue continuo.

## Workflow del README

El YAML del README se extrajo tal cual. Como `pull_request`, el trabajo `construir` compila y prueba (`./mvnw verify`, 3 pruebas) y no publica nada. En `main` se ejecutó el mismo archivo cambiando **solo** el nombre `registro.example` por `localhost:5001`: construye y hace `docker push` y la etiqueta del commit aparece en el registro. El trabajo `desplegar` (`kubectl set image` con credenciales del clúster) no se ejecuta con ese archivo; el despliegue se cubre con `cd.yml`.
