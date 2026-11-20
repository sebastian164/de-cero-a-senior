
# Kubernetes 07: análisis

Kubernetes v1.37.0 (kind), metrics-server v0.9.1. Cifras de una ejecución de `probar.sh`; en cada una varían unos segundos.

## Paso 1: servidor de métricas

`components.yaml` v0.9.1 más `--kubelet-insecure-tls`. Por qué: los kubelet de kind firman su servidor con una CA propia y sin la IP del nodo como nombre alternativo, así que metrics-server no puede verificar el certificado y `kubectl top` no responde. En un clúster local se desactiva la verificación; en uno real se arregla el certificado del kubelet.

## Pasos 2 a 4: autoescalador

Petición de CPU: 100m por copia; objetivo 50 % (50m). Razón: con 100 % no queda margen mientras arrancan las copias nuevas (unos 20 s), y `/api/carga` consume un núcleo entero por petición, de modo que cualquier carga real supera de sobra el objetivo.

| t (s) | Copias (actual/deseada) | CPU media sobre la petición |
|---|---|---|
| 0 | 1/2 | sin medir aún |
| 30 | 2/4 | 1636 % |
| 60 | 6/6 | 983 % |

Subida: **60 s** hasta el máximo (6). Tras parar la carga, con el comportamiento por defecto (ventana de estabilización de 300 s) las copias bajaron a 2 a los **360 s**: la CPU estaba en 1 % desde los 60 s, pero el HPA espera 300 s antes de bajar para no oscilar. Con `scaleDown.stabilizationWindowSeconds: 30` bajó a los **90 s**. Riesgo de bajar rápido: con carga a oleadas se quitan copias para volver a subirlas (aleteo) y cada subida cuesta un arranque de JVM.

## Paso 5: `replicas` y el autoescalador

Con la API a 6 copias por la carga, un `kubectl apply` de un manifiesto con `replicas: 2` deja `spec.replicas: 2` al instante (el HPA pedía 6): las 6 copias pasan a 2 en plena carga. El HPA lo corrige en unos segundos (a los 45 s ya había vuelto a 6), pero durante ese tiempo faltó capacidad. La pelea se repite en cada despliegue. Corrección: quitar `replicas` del manifiesto (el Deployment de `api.yaml`). Al quitarlo por primera vez, `kubectl apply` lo devuelve a 1 una vez (porque estaba en la última configuración aplicada) y el HPA lo sube de nuevo.

## Pasos 6 y 7: despliegue malo

Versión con `DIRECTORIO_DATOS=/proc/no-escribible` (el proceso vive, nunca está listo), 4 copias, bucle de medición a 5 peticiones por segundo:

| Estrategia | Peticiones | Errores | Copias listas al final |
|---|---|---|---|
| `maxUnavailable: 0`, `maxSurge: 1` | 379 | **0** | 4 de 4 |
| `maxUnavailable: 50%` | 208 | **0** | 2 de 4 |

La actualización queda en `Progressing=False, ProgressDeadlineExceeded` tras `progressDeadlineSeconds: 60`: Kubernetes no la revierte, solo avisa. `kubectl rollout undo` vuelve atrás. Con 50 % no hay errores *en esta prueba*, pero se pierde la mitad de la capacidad (2 de 4 listas) durante el fallo; con carga alta serían errores o lentitud.

## Paso 8: Recreate

33 errores de 90 peticiones, unos 10 s de corte (para 4 copias y un arranque de unos 5 s por el apagado ordenado). Es la opción correcta cuando dos versiones no pueden convivir (cambio de esquema incompatible, un volumen `ReadWriteOnce`, una licencia por instancia) y se acepta el corte.

## Paso 9: presupuesto de interrupción

Con `minAvailable: 2` y 4 copias, `kubectl drain` del nodo `referencia-worker` expulsa los pods con cuidado: 114 peticiones del bucle, **0 errores**. Con `minAvailable: 4` (igual al número de copias) el `drain` se queda reintentando: `Cannot evict pod as it would violate the pod's disruption budget.` Ningún nodo con copias se puede vaciar nunca.

## Paso 10: repartir

Antes (sin restricción) el planificador ya repartía los pods entre los dos nodos de trabajo; eso es una preferencia, no una garantía. Con `topologySpreadConstraints` (manifiesto `api-repartida.yaml`, que la prueba aplica) (`maxSkew: 1`, `topologyKey: kubernetes.io/hostname`, `DoNotSchedule`) y 6 copias quedan **3 y 3**. `ScheduleAnyway` solo lo prefiere; `DoNotSchedule` lo exige y deja pods en `Pending` si un nodo falta.

**Hallazgo:** con `DoNotSchedule` y la `topologyKey` por nodo, el plano de control (con su taint) cuenta como un dominio sin pods, el mínimo es 0 y el planificador solo deja 1 pod por nodo: la actualización gradual se quedaba con un pod `Pending` (`2 node(s) didn't match pod topology spread constraints`). Se resuelve con `nodeTaintsPolicy: Honor`, que ignora los nodos con taints que el pod no tolera; con eso quedan 3 y 3.
