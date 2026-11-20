# 01 — Clúster local y primer pod

## Idea

Un termostato. No le dices "enciende la calefacción doce minutos": le dices "quiero 21 grados" y él mide, compara y actúa las veces que haga falta. Si alguien abre una ventana, vuelve a corregir. Kubernetes funciona así: declaras el estado que quieres y unos procesos comparan sin descanso lo que hay con lo que pediste.

## Lo esencial

### De qué está hecho un clúster

| Pieza | Dónde | Qué hace |
|---|---|---|
| Servidor de la API | Plano de control | La única puerta de entrada. Todo, incluido `kubectl`, habla con él |
| etcd | Plano de control | Guarda el estado deseado y el real |
| Planificador | Plano de control | Decide en qué nodo corre cada pod |
| Controladores | Plano de control | Los "termostatos": cada uno vigila un tipo de objeto y corrige diferencias |
| kubelet | Cada nodo | Arranca y vigila los contenedores que le asignaron |
| kube-proxy / red | Cada nodo | Hace que los pods y los servicios se alcancen |

### Pod

La unidad mínima que Kubernetes despliega: uno o más contenedores que comparten red y pueden compartir volúmenes. Casi siempre es un solo contenedor. Un pod es **desechable**: tiene una dirección IP que cambia cuando se recrea, y si lo borras, nadie lo repone a menos que algo de más alto nivel lo esté cuidando.

### Declarativo e imperativo

| Imperativo | Declarativo |
|---|---|
| `kubectl run`, `kubectl scale`, `kubectl edit` | `kubectl apply -f archivo.yaml` |
| Dices qué hacer | Dices cómo debe quedar |
| El estado vive en el clúster y en tu memoria | El estado vive en archivos versionados |

### Órdenes para empezar

| Orden | Para qué |
|---|---|
| `kubectl get <tipo>` | Listar. Con `-o wide` o `-o yaml` da más detalle |
| `kubectl describe <tipo> <nombre>` | Detalle y, al final, los eventos. Lo primero que se mira cuando algo falla |
| `kubectl logs <pod>` | Salida del contenedor. `-f` para seguirla, `--previous` para la del intento anterior |
| `kubectl exec -it <pod> -- sh` | Terminal dentro del contenedor |
| `kubectl port-forward <pod> 8080:80` | Túnel desde tu equipo a un pod. Solo para probar |
| `kubectl apply -f` / `kubectl delete -f` | Aplicar / eliminar lo que describe un archivo |
| `kubectl explain pod.spec.containers` | La documentación de cada campo, sin salir de la terminal |

## Preparación

Necesitas Docker funcionando, `kubectl` y una herramienta para crear un clúster local. El curso usa [kind](https://kind.sigs.k8s.io/), que levanta cada nodo como un contenedor. Sirven igual minikube, k3d o el Kubernetes que trae Docker Desktop; si eliges otro, algunas órdenes cambian (por ejemplo, cómo cargar una imagen local).

<!-- file: kind/cluster.yaml -->
```yaml
# Un plano de control y dos nodos de trabajo: suficiente para ver planificación y fallos de nodo.
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: curso
nodes:
  - role: control-plane
  - role: worker
  - role: worker
```

```bash
kind create cluster --config kind/cluster.yaml
kubectl get nodes
```

## Ejemplo

### Antes

```bash
kubectl run saludo --image=nginx --port=80
kubectl edit pod saludo          # se cambia algo "en caliente"
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Nada queda escrito | Nadie puede revisar, repetir ni deshacer lo que se hizo |
| `nginx` sin versión | Se descarga `latest`: mañana puede ser otra imagen |
| Cambios con `edit` | El clúster ya no coincide con ningún archivo. Si hay que reconstruirlo, se pierde |
| Un pod suelto | Si muere, o muere su nodo, no vuelve |

### Después

<!-- file: ejemplo/pod.yaml -->
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: saludo
  labels:
    app: saludo
spec:
  containers:
    - name: web
      image: nginx:1.28-alpine
      ports:
        - containerPort: 80
```

```bash
kubectl apply -f ejemplo/pod.yaml
kubectl get pod saludo -o wide            # en qué nodo quedó y con qué IP
kubectl describe pod saludo               # lee los eventos del final
kubectl port-forward pod/saludo 8080:80   # y en otra terminal: curl localhost:8080
kubectl delete -f ejemplo/pod.yaml
```

El archivo va al repositorio. El pod suelto sigue siendo frágil: eso lo resuelve la sesión 02.

## Errores comunes

- Tratar los pods como servidores con nombre propio y entrar a arreglarlos a mano.
- Mezclar `apply` con `edit` y no saber después cuál es la verdad.
- Olvidar en qué clúster y en qué espacio de nombres estás. `kubectl config current-context` antes de borrar algo.
- En un clúster local, usar una imagen que construiste y no cargaste en los nodos: el nodo intenta descargarla de internet y falla.
- Leer solo `get` y no `describe`: la causa casi siempre está en los eventos.

## Taller

**Objetivo:** levantar tu clúster, ejecutar la API de Bitácora en un pod y comprobar con tus manos qué pasa cuando las cosas se rompen.

No hay archivos de inicio. Necesitas tu imagen `bitacora-api:1.0.0` del taller de Docker.

1. Crea el clúster de tres nodos. Lista los nodos y los pods del espacio de nombres `kube-system`, y relaciona cada pod con una pieza de la tabla de arquitectura.
2. Haz que los nodos tengan tu imagen (con kind: `kind load docker-image`). Explica por qué hace falta este paso y por qué en un clúster real no existe.
3. Escribe el manifiesto de un pod `bitacora-api` con tu imagen, el puerto y una etiqueta. Aplícalo.
4. Averigua en qué nodo quedó y qué IP tiene. Llega a `/api/info` con `port-forward`.
5. Mira los registros y entra al contenedor. ¿Con qué usuario corre? ¿Qué valor tiene `host` en `/api/info` y de dónde sale?
6. Borra el pod. ¿Vuelve? Aplica de nuevo y compara la IP con la anterior.
7. Mata el proceso desde dentro del contenedor (`kill 1`). ¿Qué hace Kubernetes? Mira la columna `RESTARTS` y explica la diferencia con el paso 6.
8. Detén el nodo donde está el pod (con kind, `docker stop <nodo>`). Observa durante unos minutos el nodo y el pod. ¿Se muda el pod a otro nodo? Vuelve a arrancar el nodo.
9. Cambia la imagen del manifiesto a una etiqueta que no existe y aplica. Encuentra la causa usando solo `describe`.
10. Usa `kubectl explain` para encontrar qué hace `restartPolicy` y qué valores admite.

**Criterios de aceptación**

- Todo lo que creaste está en archivos; nada se creó con `kubectl run`.
- Cada respuesta cita la salida de una orden.
- Explicas con claridad la diferencia entre reiniciar un contenedor, recrear un pod y perder un nodo.

**Entrega:** rama `taller/k8s-01`, con tus manifiestos y `taller/solucion/ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Nadie despliega pods sueltos. Pero `get`, `describe`, `logs` y `exec` sobre pods son el ochenta por ciento del diagnóstico diario, aunque el despliegue lo haga un flujo automático que nunca tocas.

## Preguntas senior

1. ¿Qué significa que Kubernetes sea declarativo y qué papel juegan los controladores?
2. ¿Por qué todo pasa por el servidor de la API?
3. ¿Por qué la unidad mínima es el pod y no el contenedor?
4. Diferencia entre que se reinicie un contenedor y que se recree un pod.
5. ¿Qué le pasa a un pod suelto cuando su nodo desaparece?
6. ¿Qué riesgos tiene administrar un clúster con órdenes imperativas?
