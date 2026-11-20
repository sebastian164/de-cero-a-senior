# Kubernetes 01: análisis

Clúster kind v0.33.0, Kubernetes v1.37.0, un plano de control y dos nodos de trabajo (aquí el clúster se llama `referencia`, el espacio de nombres `taller-k01` y el puerto del reenvío es el 50110 en lugar del 8080, para no chocar con tu clúster `curso`). Las órdenes y salidas son las de una ejecución de `probar.sh`. El paso 8 espera más de cinco minutos a que Kubernetes desaloje el pod del nodo detenido; esa espera es opcional (`ESPERA_LARGA=1 ./probar.sh`) y las salidas de este documento son de una ejecución con ella. Los manifiestos (`pod.yaml`, `pod-etiqueta-inexistente.yaml`, `espacio-de-nombres.yaml`) están en esta carpeta: nada se creó con `kubectl run`.

## 1. Nodos y `kube-system`, relacionados con la arquitectura

```bash
kubectl get nodes
kubectl -n kube-system get pods -o wide
```
Tres nodos `Ready`. En `kube-system`:

| Pod (nodo) | Pieza de la tabla del README | Qué hace |
|---|---|---|
| `kube-apiserver-...-control-plane` | Servidor de la API | Única puerta de entrada; `kubectl` habla con él |
| `etcd-...-control-plane` | etcd | Guarda el estado deseado y el real |
| `kube-scheduler-...-control-plane` | Planificador | Elige el nodo de cada pod |
| `kube-controller-manager-...-control-plane` | Controladores | Los «termostatos» (ReplicaSet, nodos, etc.) |
| `kube-proxy-*` (uno por nodo) | kube-proxy / red | Hace que los Services se alcancen |
| `kindnet-*` (uno por nodo) | red (complemento de red de kind) | Da IP a los pods y comunica los nodos; aquí también hace cumplir las NetworkPolicy |
| `coredns-*` (2) | DNS del clúster (parte de la red) | Resuelve los nombres de Service |
| `metrics-server-*` | complemento (lo instala el taller 07) | Métricas de CPU y memoria |

El kubelet no aparece como pod: corre como proceso en cada nodo (aquí dentro del contenedor del nodo de kind), por eso no está en `kube-system`.

## 2. Por qué hace falta `kind load` y por qué en un clúster real no existe

```bash
kind load docker-image bitacora-api:1.0.0 --name <clúster>
docker exec <nodo> crictl images | grep bitacora-api      # bitacora-api  1.0.0  ...
```
Cada nodo de kind es un contenedor con su **propio** almacén de imágenes (containerd); no ve las imágenes del Docker del equipo. Sin cargarla, el kubelet intenta descargar `bitacora-api:1.0.0` de Docker Hub, no la encuentra y el pod queda en `ErrImagePull`/`ImagePullBackOff` (es lo que se provoca en el paso 9). En un clúster real los nodos descargan siempre de un **registro** (ghcr.io, ECR...) con el nombre de la imagen; nadie copia imágenes a mano a cada nodo, y por eso la imagen debe estar publicada allí antes de desplegar.

## 3 y 4. El pod, su nodo, su IP y `/api/info`

```bash
kubectl apply -f pod.yaml
kubectl get pod bitacora-api -o wide
# bitacora-api   1/1   Running   0   1s   10.244.1.43   referencia-worker
kubectl port-forward pod/bitacora-api 8080:8080 &      # (probar.sh usa el 50110)
curl localhost:8080/api/info
# {"mensaje":"Hola desde la bitácora","version":"dev","host":"bitacora-api","visitas":2,"notas":0}
```
Quedó en el nodo `worker` con IP `10.244.1.43` (IP de la red de pods, solo alcanzable dentro del clúster; por eso `port-forward`).

## 5. Registros, usuario y `host`

```bash
kubectl logs bitacora-api         # escuchando en el puerto 8080 (version dev, host bitacora-api) / listo para atender; datos en /datos
kubectl exec bitacora-api -- id   # uid=10001(bitacora) gid=10001(bitacora) groups=10001(bitacora)
```
Corre con el usuario **10001**, el que fija la imagen (Docker 03): Kubernetes no lo cambia. `host` vale `bitacora-api`, el **nombre del pod**: la API lo lee de la variable `HOSTNAME`, que el kubelet fija al nombre del pod (`kubectl exec bitacora-api -- sh -c 'echo $HOSTNAME'` da `bitacora-api`). En Docker, `host` era el ID del contenedor, porque ahí `HOSTNAME` es el ID.

## 6. Borrar el pod

```bash
kubectl delete pod bitacora-api ; sleep 5 ; kubectl get pod bitacora-api    # NotFound: nadie lo repone
kubectl apply -f pod.yaml
```
No vuelve: un pod suelto no tiene ningún controlador que lo vigile. Al aplicar de nuevo se crea **otro pod** (otro `uid`) y esta vez cayó en el otro nodo: IP `10.244.1.43` → `10.244.2.46`. La IP pertenece al pod, no al nombre. (Con un Deployment sí se recrearía solo, también con otra IP.)

## 7. `kill 1`

```bash
kubectl exec bitacora-api -- kill 1
kubectl get pod bitacora-api          # Running, RESTARTS 1 (1s ago)
```
Kubernetes (el kubelet del nodo) reinicia **el contenedor** dentro del mismo pod (`restartPolicy: Always`). `RESTARTS` sube a 1; el `uid` y la IP **no cambian** (comprobado). Último estado del contenedor: `143 Error` (128 + SIGTERM: `kill` sin número envía TERM; la JVM salió ordenadamente, `kubectl logs --previous` muestra «apagado completo»). Diferencia con el paso 6: allí desapareció el **pod** (objeto, IP, uid); aquí solo se reinició un **contenedor** y el pod es el mismo.

## 8. Detener el nodo del pod

```bash
docker stop referencia-worker2       # el nodo donde estaba el pod
kubectl get node,pod -o wide -w
```
Lo que se observó, midiendo más de cinco minutos:

| Tiempo desde `docker stop` | Qué pasa |
|---|---|
| ~48 s | El nodo pasa a `NotReady` (el controlador de nodos espera `node-monitor-grace-period`, 40 s, sin latidos del kubelet) y recibe los taints `node.kubernetes.io/unreachable` (`NoSchedule` y `NoExecute`) |
| +30 s | El pod sigue figurando `Running`, en el mismo nodo: **no se muda** |
| ~349 s (≈300 s tras el `NotReady`) | Se agota la `tolerationSeconds: 300` que Kubernetes añade a todo pod para `not-ready` y `unreachable`: el pod recibe `deletionTimestamp` y queda **`Terminating`**; el kubelet del nodo muerto no puede confirmar el borrado |
| nodo aún detenido | Sigue `Terminating`; ningún pod lo sustituye, porque un pod suelto no tiene quien lo recree |
| `docker start` del nodo | El nodo vuelve a `Ready`, el kubelet confirma el borrado y el pod **desaparece del todo** (no se recrea); hay que aplicar de nuevo `pod.yaml` |

Un pod suelto **no se muda a otro nodo** nunca. Con un Deployment, el ReplicaSet crearía una copia nueva en otro nodo en cuanto el pod pierde la condición de listo/se desaloja.

## 9. Una etiqueta que no existe

```bash
kubectl apply -f pod-etiqueta-inexistente.yaml        # imagen bitacora-api:9.9.9
kubectl describe pod bitacora-api                     # solo describe
# Warning  Failed  kubelet  spec.containers{api}: Failed to pull image "bitacora-api:9.9.9": failed to pull and unpack image "docker.io/library/bitacora-api:9.9.9": failed to resolve ...
```
Estado `ErrImagePull` y luego `ImagePullBackOff`. `describe` nombra la imagen exacta y el motivo en los eventos del final.

## 10. `kubectl explain pod.spec.restartPolicy`

```
FIELD: restartPolicy <string>
  Restart policy for all containers within the pod. One of Always, OnFailure, Never.
  Default to Always.
```
`Always` (por defecto): reinicia siempre el contenedor, incluso si sale bien. `OnFailure`: solo si sale con error (propio de Jobs). `Never`: no reinicia (tareas de una sola vez). Es lo que hizo el paso 7.

## Reiniciar un contenedor, recrear un pod y perder un nodo

| Qué | Quién actúa | Qué cambia | Qué no cambia | Ejemplo |
|---|---|---|---|---|
| **Reiniciar un contenedor** | El kubelet del nodo, según `restartPolicy` | `RESTARTS` +1; el proceso arranca de nuevo, con un sistema de archivos limpio (lo que no esté en un volumen se pierde) | El pod: mismo `uid`, misma IP, mismo nodo | `kill 1` (paso 7) |
| **Recrear un pod** | Un controlador (Deployment/ReplicaSet) o tú con `apply` | Es **otro objeto**: otro `uid`, otra IP, quizá otro nodo; el nombre puede cambiar | La definición (la plantilla) | Borrar el pod (paso 6) |
| **Perder un nodo** | El controlador de nodos, tras 40 s; el desalojo, tras 300 s más | Todos los pods del nodo dejan de estar listos y a los 300 s se marcan para borrar; solo un controlador los recrea en otro nodo | Los pods sueltos no se recrean: se quedan `Terminating` hasta que el nodo vuelve | `docker stop` del nodo (paso 8) |
