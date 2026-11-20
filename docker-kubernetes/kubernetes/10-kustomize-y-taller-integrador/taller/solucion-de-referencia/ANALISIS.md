# Kubernetes 10: análisis del taller integrador

Kubernetes v1.37.0 (kind v0.33.0), Envoy Gateway v1.9.3, metrics-server v0.9.1, kubectl v1.36.1 (Kustomize v5.8.1). Las cifras salen de una ejecución de `probar.sh` y varían unos segundos entre ejecuciones. Defensa del diseño: [`DEFENSA.md`](DEFENSA.md).

## Estructura (parte 1)

`base/` (api, web, gateway y ruta, y un `configMapGenerator`) y dos superposiciones que solo contienen diferencias:

| | desarrollo | producción |
|---|---|---|
| Espacio de nombres | `bitacora-dev` (`warn=restricted`) | `bitacora-prod` (`enforce=restricted`) |
| Imagen | `bitacora-api:1.0.0`, `bitacora-web:0.1.0` | `bitacora-api:1.0.1`, `bitacora-web:0.1.0` |
| Copias | 1 / 1 | API: las gobierna el autoescalador (3 a 6); web: 2 |
| `MENSAJE` / `VERSION` | `Ambiente de desarrollo` / `dev` | `Bitácora en producción` / `1.0.1` |
| Recursos de la API | 100m / 192Mi | 200m / 256Mi (parche `recursos.yaml`) |
| Nombre de host | `dev.bitacora.localhost` (clase `eg-interno`, solo dentro del clúster) | `bitacora.localhost` (clase `eg`, puerto 50080 del equipo) |
| Extras | | HPA, dos PDB, `NetworkPolicy` |

Cambio de configuración: al cambiar `MENSAJE` en la superposición de desarrollo, el nombre del ConfigMap cambió (`bitacora-api-config-884gt4dmft` → `bitacora-api-config-85dk8ch74m`), el Deployment hizo una actualización gradual solo y `/api/info` sirvió el valor nuevo. Mientras el pod viejo termina (preStop y apagado ordenado) el proxy puede seguir usándolo unos segundos: la prueba registra cuánto tardó.

Secretos: ninguno en el repositorio. `guion/crear-secreto.sh <espacio>` genera `CLAVE_API` con `openssl rand` al crear cada ambiente; en producción real lo traería un gestor externo. Sin el Secret los pods no arrancan (`secretKeyRef`).

## Requisitos de producción (parte 2)

Comprobados por `probar.sh` contra el clúster: imágenes con versión fija, tres copias o más repartidas en 2 nodos, sondas de arranque, disponibilidad y vida, peticiones y límites, apagado ordenado (`preStop` + `ESPERA_APAGADO_MS`), Gateway con nombre de host, HPA y PDB, `restricted` exigido, red cerrada (un pod ajeno no llega a la API), y la decisión sobre el estado en `DEFENSA.md`.

## Reproducible (parte 3)

`guion/construir.sh produccion` crea el clúster (kind con tres nodos y el mapeo de puertos), carga las imágenes, instala metrics-server y Envoy Gateway con Helm, crea el Secret y aplica la superposición. La prueba **destruye el clúster y lo reconstruye con el guion**: entre 45 y 70 s con las imágenes y los charts en caché. Validación previa sin clúster: `guion/validar.sh` (`kubectl kustomize` + `kubeconform -strict` con los esquemas de Kubernetes y del catálogo de CRD, + `kube-linter`); no necesita un clúster y sirve para integración continua.

## Simulacro (parte 4)

Bucle de medición: `curl` cada 0,2 s contra `http://bitacora.localhost:50080/api/info`, desde el equipo.

| Prueba | Resultado medido | Lo esperado |
|---|---|---|
| Borrar un pod de la API | 0 errores | cero |
| Borrar todos los pods de la API a la vez (normal) | de 0 a 1 errores (503) en ~1 s | errores unos segundos: **no se cumple del todo**: los pods que terminan siguen atendiendo (`preStop` y espera de apagado) y Envoy Gateway los usa mientras no haya otros |
| Borrar todos de golpe (`--force --grace-period=0`, como un fallo) | de 1 a 4 errores (503) en 1 a 2 s | errores y recuperación sola: sí |
| Reiniciar el Deployment | 0 errores (en 1 de 4 ejecuciones hubo un único `000` entre 81 peticiones, sin causa aislada: un fallo suelto de la conexión desde el equipo, no una serie) | cero |
| Versión que nunca está lista | 0 errores; `ProgressDeadlineExceeded`, 3 copias viejas siguen | cero; se detiene |
| Volver atrás | 0 errores | cero |
| Vaciar un nodo de trabajo (el que no tiene el mapeo de puertos) | **0 errores** (160 peticiones en la ejecución completa final; 0 también en las 3 ejecuciones sueltas del taller con el `preStop` de 15 s) | cero: **se cumple** |
| Vaciar el nodo de la entrada (`entrada=si`, el del mapeo del puerto del equipo), 3 veces por ejecución | **0 errores en cada vez** (135, 164 y 160 peticiones en la ejecución completa final; 0 en los 9 vaciados de esas 3 ejecuciones y en 12 de 12 del experimento con `preStop` de 15 s). Con el `preStop` de 5 s: de 0 a 12 errores 503 en 3 de 12 vaciados | cero: **se cumple** con `preStop` de 15 s; no se cumplía con 5 s |
| Detener un nodo de golpe (`docker stop` de un nodo de trabajo sin el mapeo; 1 a 3 copias de la API, 1 del proxy y 1 del controlador) | `NotReady` a los 42 a 47 s; de 10 a 12 errores (casi todos `000`: tiempo agotado, curl 28, y respuesta vacía, curl 52; algún `503`); el último error a los 49 a 57 s; 15 s seguidos con 200 a los 64 a 68 s **con el nodo aún detenido** | errores breves: sí; **tarda** `node-monitor-grace-period` (40 s) en marcar el nodo `NotReady` |
| Pico de carga | 3 a 6 copias a los ~60 s; vuelve a 3 a los ~120 s tras parar | sube y baja |

Por qué tarda ~40 s la reacción al nodo caído: el kubelet envía latidos cada 10 s; el controlador de nodos espera `node-monitor-grace-period` (40 s) sin latidos antes de marcarlo `NotReady`; solo entonces los pods del nodo dejan de estar listos y salen de los Service. Hasta ese momento Kubernetes cree que el nodo está bien: `kube-proxy` sigue enviando al proxy de Envoy y a la copia de la API que estaban en él una parte de las conexiones, que no llegan (`000`, tiempo agotado a los 5 s del medidor). Pasados los 40 s los endpoints muertos salen, el tráfico va solo a las copias vivas y los errores terminan sin que nadie intervenga: la prueba no arranca el nodo hasta que el bucle lleva 15 s seguidos con 200, y lo comprueba con `docker inspect` (el nodo sigue detenido). Desalojar y recrear los pods lleva otros 300 s (la tolerancia por defecto de `NoExecute`), aunque ya no importa para el servicio. Se puede acortar bajando los latidos y las tolerancias, a costa de falsos positivos.

### El reparto de la entrada no sobrevive a un `drain`

`topologySpreadConstraints` solo actúa **al programar** un pod. Al vaciar un nodo, este queda cordonado y el planificador lo ignora, así que las dos copias del proxy y las dos del controlador de Envoy Gateway se programan en el único nodo de trabajo que queda; al hacer `uncordon` nada las recoloca. Se midió en una ejecución anterior (registro cada 4 s de en qué nodo estaba cada pod): tras vaciar `worker2` los dos proxies y los dos controladores estaban en `worker`, y tras vaciar `worker`, los cuatro en `worker2`. Con los cuatro en el mismo nodo, detenerlo dejaba **toda** la entrada fuera: 24 de 26 y 27 de 27 peticiones fallidas durante 56 y 65 s, hasta que la prueba arrancaba el nodo. Ese amontonamiento era también la causa de los 503 al vaciar el nodo de la entrada (los proxies sin controlador que les actualizara los destinos mientras la API cambiaba de pod).

La prueba ahora hace lo que haría quien opera, y lo comprueba: antes de cada `drain` y antes del `docker stop` expulsa una de las dos copias si las dos están en el mismo nodo (el planificador la coloca en el otro) y **afirma** que los proxies están en 2 nodos distintos y los controladores también.

**La causa de los 503 que quedaban: un `preStop` de 5 s era más corto que el retraso de los proxies en enterarse.** Con el reparto restaurado pero vaciando el nodo justo después de que los pods nuevos quedaran listos, seguían saliendo errores. En experimentos aparte (mismo clúster y mismo bucle, vaciando alternativamente el nodo con el líder del controlador de Envoy Gateway y el otro, sin esperar tras restaurar el reparto) hubo errores en 3 de 12 vaciados y en 4 de 10 en una variante más dura (las tres copias de la API en el nodo vaciado): de 1 a 12 respuestas 503 entre los segundos 5 y 10 desde el inicio del `drain`, y una o dos `000` hacia el segundo 16. El cuerpo de esos 503 es `{"error":"el servicio no está listo"}`: lo responde **la propia API** mientras se apaga (5 s de `preStop` y luego 5 s de `ESPERA_APAGADO_MS` sin estar lista), o sea, los proxies seguían enviando peticiones a un pod que ya estaba terminando 5 s o más después de que su endpoint saliera del Service, y la `000` llega cuando el pod ya ha muerto. El retraso aparece cuando el controlador de Envoy Gateway acaba de cambiar de pod (los experimentos con error tenían el Lease del líder a nombre de un pod ya borrado, o un líder de 4 a 20 s; un líder nuevo tarda hasta 15 s en tomar el Lease si el anterior no lo suelta). La corrección está en el `preStop`, que debe durar más que el retraso en propagar la baja del endpoint: **15 s** en lugar de 5 (`terminationGracePeriodSeconds: 30` cubre 15 + 5 s de espera). Resultado con el mismo experimento, sin esperar nada tras restaurar el reparto y con el líder a veces en el nodo vaciado, a veces sin líder vivo, a veces con 4 s de edad: **0 errores en 12 de 12** vaciados. Lo que no se aisló es dónde exactamente se pierde ese tiempo (controlador, proxy o los dos); la medida correcta frente a un retraso desconocido es un `preStop` con margen. Coste: cada pod tarda 10 s más en terminar, y una actualización gradual de la API, también.

Con eso, el simulacro de la prueba completa dio **0 errores en todos los vaciados** (el del nodo de trabajo y los del nodo de la entrada), y el nodo detenido de golpe solo produce los errores de los 40 s de latidos.

Lo que esto no resuelve: el reparto no se restaura solo tras un mantenimiento. En un clúster real se usa el [descheduler](https://github.com/kubernetes-sigs/descheduler) (estrategia `RemovePodsViolatingTopologySpreadConstraint`) o se reinician los Deployment a mano; sin eso, un segundo mantenimiento seguido del primero deja la entrada otra vez en un solo nodo. El nodo con el mapeo de puertos del equipo sigue siendo un punto único ante una **caída** (no ante un vaciado): si se detiene, el puerto 50080 deja de responder; en una nube lo cubre el balanceador.
