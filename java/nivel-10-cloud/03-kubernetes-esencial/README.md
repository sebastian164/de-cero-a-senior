# 03 — Kubernetes esencial para desarrolladores

## Idea

Un director de orquesta no toca ningún instrumento: tiene la partitura y se asegura de que cada músico haga su parte. Si falta un violinista, pone a otro. Kubernetes es ese director: tú le entregas la partitura ("quiero tres copias de este servicio") y él se ocupa de mantenerla.

## Lo esencial

**Modelo declarativo:** describes el estado deseado y Kubernetes trabaja de forma continua para que el estado real coincida. Si un contenedor muere, lo reemplaza.

### Objetos que un desarrollador debe conocer

| Objeto | Qué es |
|---|---|
| **Pod** | La unidad mínima: uno o más contenedores que comparten red. Es efímero |
| **Deployment** | Declara cuántas réplicas de un pod se quieren y cómo actualizarlas |
| **Service** | Nombre y dirección estables frente a un conjunto de pods cambiantes; balancea entre ellos |
| **Ingress** / Gateway | Entrada HTTP desde fuera del clúster |
| **ConfigMap** | Configuración no sensible |
| **Secret** | Configuración sensible (codificada, no cifrada por defecto) |
| **Namespace** | Separación lógica |
| **HorizontalPodAutoscaler** | Ajusta el número de réplicas según métricas |
| **Job** / **CronJob** | Tareas que terminan / programadas |
| **StatefulSet** | Cargas con identidad y almacenamiento estables |

### Lo que la aplicación debe hacer bien

1. **Sondas:**

| Sonda | Pregunta | Si falla |
|---|---|---|
| De vida (*liveness*) | ¿El proceso está sano o colgado? | Se reinicia el contenedor |
| De disponibilidad (*readiness*) | ¿Puede atender tráfico ahora? | Se saca del balanceo, sin reiniciar |
| De arranque (*startup*) | ¿Ya terminó de iniciar? | Da tiempo a aplicaciones lentas |

2. **Recursos:** `requests` (lo reservado; se usa para planificar) y `limits` (el máximo). Superar el límite de memoria mata el contenedor; superar el de CPU lo frena.
3. **Apagado ordenado:** al recibir `SIGTERM`, dejar de aceptar peticiones, terminar las que están en curso y cerrar.
4. **Ser sin estado** y tolerar que cualquier pod desaparezca en cualquier momento.
5. **Registrar en la salida estándar.**

### Actualizaciones

El Deployment hace actualización progresiva: crea pods nuevos, espera a que estén disponibles y retira los viejos. Si los nuevos no pasan la sonda de disponibilidad, el despliegue se detiene. `kubectl rollout undo` revierte.

## Ejemplo

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pedidos
spec:
  replicas: 3
  selector:
    matchLabels: { app: pedidos }
  template:
    metadata:
      labels: { app: pedidos }
    spec:
      containers:
        - name: pedidos
          image: registro.example/pedidos:1.4.2
          ports: [{ containerPort: 8080 }]
          envFrom:
            - configMapRef: { name: pedidos-config }
            - secretRef: { name: pedidos-secretos }
          resources:
            requests: { cpu: 250m, memory: 512Mi }
            limits: { memory: 768Mi }
          startupProbe:
            httpGet: { path: /actuator/health/liveness, port: 8080 }
            failureThreshold: 30
            periodSeconds: 2
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: 8080 }
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: 8080 }
---
apiVersion: v1
kind: Service
metadata:
  name: pedidos
spec:
  selector: { app: pedidos }
  ports: [{ port: 80, targetPort: 8080 }]
```

Comandos del día a día: `kubectl get pods`, `describe pod`, `logs -f`, `exec -it`, `rollout status`, `rollout undo`, `port-forward`.

## Errores comunes

- Sin sondas, o sonda de vida que depende de la base de datos.
- Sin `requests` ni `limits`: un pod consume todo el nodo.
- Límite de memoria igual al montón de la JVM.
- Etiqueta `latest` en la imagen.
- Secretos en texto plano en el repositorio.
- Una sola réplica de un servicio crítico.
- No manejar `SIGTERM`: peticiones cortadas en cada despliegue.
- Adoptar Kubernetes para una aplicación y un equipo pequeños, cuando un servicio de contenedores gestionado bastaba.

## Taller

1. Instala un clúster local (kind o minikube) y despliega una aplicación Spring Boot con Deployment y Service.
2. Configura ConfigMap, Secret y las tres sondas.
3. Elimina un pod y observa cómo se recrea. Escala a cinco réplicas.
4. Despliega una versión que no arranca y observa cómo la actualización progresiva protege al servicio. Revierte.
5. Fija un límite de memoria bajo, provoca el *OOMKilled* y diagnostícalo con `describe`.
6. Lanza carga durante un despliegue con y sin apagado ordenado, y cuenta las peticiones fallidas.

Rama `taller/n10-03-kubernetes`.

## Preguntas senior

1. ¿Qué significa que Kubernetes sea declarativo?
2. Diferencia entre las tres sondas y un error típico de cada una.
3. `requests` frente a `limits`; qué pasa al superar cada uno.
4. ¿Cómo logras despliegues sin peticiones fallidas?
5. ¿Cuándo no usarías Kubernetes?
6. ¿Por qué un pod debe considerarse desechable?
