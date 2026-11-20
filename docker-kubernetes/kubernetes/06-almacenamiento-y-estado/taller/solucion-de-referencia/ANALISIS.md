# Kubernetes 06: análisis

Clúster kind v1.37.0; `StorageClass` `standard` (`rancher.io/local-path`, `WaitForFirstConsumer`, `Delete`).

| Paso | Medición |
|---|---|
| 1 | Con 3 copias y 5 notas, 20 lecturas dan 3 listas distintas: cada pod escribe en su propio sistema de archivos |
| 2 | Tras reiniciar el Deployment: 0 notas |
| 3 | Un solo PVC `ReadWriteOnce` en 3 copias: las 3 corren, **todas en el mismo nodo** (el PV `local-path` tiene afinidad a un nodo). Funciona por casualidad: si el planificador no tuviera esa restricción, las copias en otros nodos quedarían `ContainerCreating`/`Multi-Attach`; además las 3 copias escriben el mismo archivo sin coordinación |
| 4 | Solo hay `standard` (local-path): ofrece de verdad `ReadWriteOnce`; no `ReadWriteMany` |
| 6 | Una copia con volumen y `strategy: Recreate`: el pod viejo debe soltar el volumen antes de que arranque el nuevo; durante un reinicio medido hay errores (1 a 5 errores de 32 peticiones, 1 a 6 s). Con `RollingUpdate` la copia nueva no podría montar el volumen mientras la vieja siga viva |
| 7 | PostgreSQL 17.12 como StatefulSet con Service sin IP, Secret (`openssl rand`) y PVC de 1 GiB |
| 8 | Borrar el pod: vuelve `db-0` (UID nuevo) con las 3 filas. Borrar el StatefulSet: la petición `datos-db-0` sigue `Bound` y al recrearlo reaparecen los datos |
| 9 | `db-1` es una base distinta y vacía: ni siquiera tiene la base `bitacora`. Un StatefulSet no replica datos |
| 10 | `CronJob` de `pg_dump` hacia otro PVC; ejecutado a mano con `kubectl create job --from=cronjob/respaldo`, se restauró en un Postgres vacío: 4 filas restauradas = 4 en origen |

## Decisión del paso 5 (argumentada con costos)

| Camino | ¿Resuelve la inconsistencia? | Actualización | Si cae un nodo | Costo |
|---|---|---|---|---|
| Una copia + volumen | Sí | `Recreate`: hay corte en cada despliegue | Hay corte hasta que se reprograma, y con `local-path` los datos quedan en el nodo caído | Sin alta disponibilidad |
| StatefulSet, un volumen por copia | **No**: son 3 listas distintas igual que antes | Ordenada, una a una | Cada copia en su nodo | Complejidad sin beneficio: los datos no se replican |
| Base de datos externa | Sí | Sin corte para la API | La API sigue; la base tiene su propia alta disponibilidad | Hay que cambiar el código |

**Implementado para Bitácora tal como está:** una copia con su volumen y `Recreate` (el único camino que da una sola lista sin cambiar el código), con el corte medido y aceptado. **Correcto si pudiera cambiar el código:** que la API guarde las notas en PostgreSQL y quede sin estado, con tres copias, `RollingUpdate`, y la base administrada por el proveedor.

## Ninguna contraseña en el repositorio

El Secret `db` se crea con `kubectl create secret generic db --from-literal=password=$(openssl rand -hex 12)` en `probar.sh`; el `CronJob` y la base lo leen con `secretKeyRef`. La prueba hace `grep` de la solución para comprobarlo.
