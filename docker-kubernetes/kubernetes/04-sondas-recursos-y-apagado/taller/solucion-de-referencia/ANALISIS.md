
# Kubernetes 04: análisis

Kubernetes v1.37.0 (kind). Bucle de medición: un pod con `curl` a `/api/info` por el Service, 5 peticiones por segundo, contando las respuestas distintas de 200. Cifras de una ejecución de `probar.sh`; varían un poco en cada una.

## Tabla de errores antes y después de cada cambio

Todas con `RETARDO_ARRANQUE_MS=20000` y un `kubectl rollout restart` con el bucle corriendo.

| Cambio | Peticiones | Errores | Qué explica |
|---|---|---|---|
| 1. Sin sondas, `ESPERA_APAGADO_MS=0`, sin `preStop` | 129 | **76** | Los pods nuevos reciben tráfico en cuanto el contenedor arranca; durante 20 s responden 503 |
| 2. + sonda de disponibilidad | 384 | **3** | Ya no se manda tráfico a pods que no están listos; quedan los errores del apagado (paso 5) |
| 3. + sonda de arranque y de vida | 402 | **3** | Mismo resultado: las sondas de arranque y vida no cambian el tráfico, protegen del bloqueo |
| 5a. Sondas, sin apagado ordenado, 2 reinicios | 825 | **6** | El pod terminando recibe SIGTERM y sale de inmediato mientras el Service aún le manda peticiones en curso |
| 5b. + `ESPERA_APAGADO_MS=5000` y `preStop` de 5 s, 2 reinicios | 951 | **0** | El proceso sigue atendiendo unos segundos tras la señal; el tráfico ya dejó de llegar |

Cifras de una ejecución (la tabla varía unas decenas de peticiones y 1 o 2 errores entre ejecuciones). El criterio «cero errores» se mide **una sola vez** (2 reinicios con el bucle), sin reintentos, y en tres ejecuciones seguidas del taller dio 0 de 949 a 951 peticiones a la primera. Cada error, si lo hubiera, se anota con el paso y el código de salida de `curl`.

**Por qué la medición espera a que no quede ningún pod anterior.** `kubectl rollout status` vuelve cuando el último pod nuevo está listo, y en ese mismo instante se borra el último pod de la versión anterior. Si esa versión no tenía apagado ordenado (el caso de la fila 5a: sin `preStop` y con `ESPERA_APAGADO_MS=0`), ese pod muere de golpe cuando arranca el medidor, y la primera petición que el Service le manda no recibe respuesta: `curl` agota los 5 s (código 28). Antes la prueba repetía la medición hasta que salía limpia y atribuía ese 28 a «el equipo cargado»; era falso. Con el registro de pods segundo a segundo se vio que el error coincidía con la desaparición de ese último pod, y al medir justo después del `rollout status` salía 1 error con curl 28, y esperando 45 s salían 0. Ahora `medir_reinicio` espera a que la cuenta de pods (los `Terminating` también cuentan) sea igual a `spec.replicas`, y 5 s más, antes de iniciar el medidor. Un tiempo agotado (28) **sí** es un síntoma de apagado: una petición enviada a la IP de un pod ya borrado no se rechaza, se queda sin respuesta; por eso ya no se descarta ningún código.

## Umbrales justificados con mediciones

- Arranque real medido (`podStartTime` a `Ready`): **21, 20 y 21 s**. `startupProbe`: `periodSeconds: 2`, `failureThreshold: 30` = hasta **60 s**, el triple del arranque medido. Es el margen para un nodo cargado sin relajar la sonda de vida.
- `readinessProbe`: cada 2 s, 2 fallos: fuera del Service en 4 s como máximo.
- `livenessProbe`: `/salud/vivo` (no depende de nada externo), cada 5 s, 3 fallos: 15 s de bloqueo antes de reiniciar; solo empieza cuando la de arranque pasó.
- Apagado: `preStop` 5 s + `ESPERA_APAGADO_MS` 5 s + 2 s de parada = 12 s; `terminationGracePeriodSeconds: 30` deja holgura de sobra.

## Paso 4: la sonda de vida impaciente

Con la sonda de vida apuntando a `/salud/listo` (503 mientras arranca), periodo de 2 s, 2 fallos y **sin sonda de arranque**, en 50 s los contenedores acumularon 3 reinicios: `get pods` muestra `Running`, `0/1` y `RESTARTS` creciendo, luego `CrashLoopBackOff`. Los eventos dicen `Liveness probe failed: HTTP probe failed with statuscode: 503` y `Container api failed liveness probe, will be restarted`. Nunca llega a estar listo porque el arranque tarda 20 s y se mata a los 4 s.

## Paso 6: memoria

Con 192 Mi nada pasa; bajando el límite, **24 Mi y 32 Mi arrancan** (el montón de la JVM se ajusta al límite) y **20 Mi muere**: `lastState.terminated.reason: OOMKilled`, `exitCode: 137` (en `kubectl get pod -o jsonpath='{.status.containerStatuses[0].lastState.terminated}'` o en `kubectl describe pod`). El pod queda en `OOMKilled` y luego `CrashLoopBackOff`.

## Paso 7: CPU

`/api/carga?ms=300` gasta CPU durante un tiempo **fijo**, de modo que el límite no alarga mucho la respuesta: sin límite, unos 0,30 s y unos 8,7 millones de iteraciones por petición; con `limits.cpu: 100m`, entre 0,35 y 0,39 s y alrededor de un millón de iteraciones (unas 9 a 15 veces menos trabajo según la ejecución). La CPU no mata: frena. La aplicación hace menos en el mismo tiempo (y en una aplicación real, tarda más).

## Paso 8: más CPU de la que hay

`requests.cpu: 64`: el pod queda `Pending`; evento: `0/3 nodes are available: 1 node(s) had untolerated taint(s), 2 Insufficient cpu. preemption: 0/3 nodes are available: 3 Preemption is not helpful for scheduling.`

## Paso 9: calidad de servicio

`qos-guaranteed` (peticiones = límites) → `Guaranteed`; `qos-burstable` (solo peticiones) → `Burstable`; `qos-besteffort` (nada) → `BestEffort`. Con poca memoria en el nodo se desaloja primero a `BestEffort`, luego a los `Burstable` que más se pasan de su petición, y al final a los `Guaranteed`. Los pods de la API son `Burstable` (petición de memoria 192 Mi y límite 256 Mi).

## Criterio «ningún reinicio tras diez minutos»

La prueba deja la versión final desplegada y espera **600 s** (diez minutos reales), con el bucle de peticiones del paso 3 ya parado, y comprueba `RESTARTS=0` en todos los pods de la API. Los reinicios de los pasos 4 y 6 son de las pruebas provocadas a propósito y ocurren antes, con otros manifiestos.
