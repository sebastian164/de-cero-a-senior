# 04 — Sondas, recursos y apagado ordenado

## Idea

Un restaurante con un cocinero nuevo. Que haya llegado no significa que ya pueda recibir pedidos: primero se cambia y organiza su puesto. Si está atendiendo y se quema la mano, hay que apartarlo un rato, no despedirlo. Y cuando termina su turno, no suelta la sartén a medio plato: entrega lo que tiene y se va. Kubernetes necesita que tu aplicación le diga esas tres cosas, porque desde fuera un proceso arrancado y uno que funciona se ven igual.

## Lo esencial

### Sondas

| Sonda | Pregunta | Si falla |
|---|---|---|
| De arranque (*startup*) | ¿Ya terminó de iniciar? | Las otras dos esperan. Si nunca pasa, se reinicia |
| De disponibilidad (*readiness*) | ¿Puede atender tráfico ahora? | Se saca del Service. **No** se reinicia |
| De vida (*liveness*) | ¿Está colgado sin remedio? | Se reinicia el contenedor |

Reglas que evitan los desastres habituales:

- La sonda de vida no debe depender de nada externo. Si comprueba la base de datos y la base se cae, Kubernetes reinicia todas las copias a la vez y convierte un problema en dos.
- La de disponibilidad sí puede reflejar que el proceso no está en condiciones de atender.
- Sin sonda de disponibilidad, un pod recibe tráfico en cuanto el contenedor arranca, esté listo o no.

### Recursos

| Campo | Qué significa |
|---|---|
| `requests` | Lo que el planificador reserva. Decide en qué nodo cabe el pod |
| `limits` | El techo |

- Superar el límite de **memoria** mata el contenedor (`OOMKilled`). No hay aviso ni excepción.
- Superar el límite de **CPU** no mata: frena. La aplicación se vuelve lenta sin que nada falle.
- Sin `requests`, el planificador cree que el pod no ocupa nada y amontona demasiados en un nodo.

Según lo que declares, el pod recibe una clase de calidad de servicio: `Guaranteed` (peticiones iguales a límites), `Burstable` o `BestEffort` (nada declarado). Cuando un nodo se queda sin memoria, se desaloja primero a los que menos garantías tienen.

### Apagado ordenado

Cuando un pod va a terminar ocurren dos cosas **a la vez**, no una después de la otra:

1. El contenedor recibe `SIGTERM`.
2. El pod se retira de los Service.

Como el paso 2 tarda un momento en propagarse, durante un instante siguen llegando peticiones a un proceso que ya se está cerrando. La aplicación debe seguir atendiendo unos segundos después de la señal, terminar lo que tenía en curso y entonces salir. Si no ha salido al vencer `terminationGracePeriodSeconds` (30 por defecto), recibe `SIGKILL`.

## Ejemplo

Una aplicación Spring Boot.

### Antes

```yaml
containers:
  - name: pedidos
    image: ghcr.io/ejemplo/pedidos:2.3.1
    ports:
      - containerPort: 8080
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Sin sonda de disponibilidad | En cada despliegue, los pods nuevos reciben tráfico mientras Spring aún arranca: errores durante medio minuto |
| Sin sonda de vida | Un proceso bloqueado se queda ahí para siempre |
| Sin `requests` | El planificador puede poner veinte copias en un nodo donde caben cinco |
| Sin `limits` de memoria | Una fuga de memoria tumba el nodo y a sus vecinos |
| Sin apagado ordenado | Cada despliegue corta las peticiones que estaban en curso |

### Después

<!-- file: ejemplo/deployment.yaml -->
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pedidos
spec:
  replicas: 3
  selector:
    matchLabels:
      app: pedidos
  template:
    metadata:
      labels:
        app: pedidos
    spec:
      terminationGracePeriodSeconds: 40
      containers:
        - name: pedidos
          image: ghcr.io/ejemplo/pedidos:2.3.1
          ports:
            - name: http
              containerPort: 8080
          env:
            - name: SERVER_SHUTDOWN
              value: graceful
            - name: SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE
              value: 25s
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            periodSeconds: 5
            failureThreshold: 24        # hasta dos minutos para arrancar
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: http
            periodSeconds: 5
            failureThreshold: 2
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            periodSeconds: 10
            failureThreshold: 3
          lifecycle:
            preStop:
              sleep:
                seconds: 5              # margen para que el pod salga de los Service
          resources:
            requests:
              cpu: 250m
              memory: 512Mi
            limits:
              memory: 512Mi
```

- La sonda de arranque da tiempo a una aplicación lenta sin relajar la de vida.
- Disponibilidad y vida usan rutas distintas, las que expone Spring Boot Actuator.
- `preStop` retrasa la señal unos segundos para que el tráfico deje de llegar; luego Spring termina las peticiones en curso.
- Memoria: petición igual a límite, para que el comportamiento sea predecible. Sin límite de CPU, para no frenar la aplicación sin necesidad; es una decisión discutible y depende de las reglas de cada plataforma.

La imagen del manifiesto no existe, así que solo se validó contra el servidor de la API de Kubernetes v1.37.0 (`kubectl apply --dry-run=server`), que acepta `preStop.sleep` y todos los campos.

## Errores comunes

- La misma ruta y los mismos umbrales para la sonda de vida y la de disponibilidad.
- Sonda de vida que consulta la base de datos.
- `initialDelaySeconds` enorme en lugar de una sonda de arranque.
- Límite de memoria del contenedor menor que lo que la JVM cree que puede usar.
- Límite de CPU bajo y luego culpar a la aplicación por lenta.
- Ignorar `SIGTERM` y perder peticiones en cada despliegue.

## Taller

**Objetivo:** conseguir que Bitácora se actualice y se reinicie sin que un usuario vea un solo error, y entender qué pasa cuando se queda sin recursos.

Parte de tus manifiestos de la sesión 03. Bitácora ofrece `/salud/vivo`, `/salud/listo`, `RETARDO_ARRANQUE_MS` y `ESPERA_APAGADO_MS`.

1. **Mide el problema.** Configura `RETARDO_ARRANQUE_MS=20000`. Desde un pod dentro del clúster, lanza un bucle que llame a `/api/info` por el Service cinco veces por segundo y cuente las respuestas que no son 200. Con el bucle corriendo, reinicia el Deployment (`kubectl rollout restart`). Anota cuántos errores hubo.
2. Añade una sonda de disponibilidad. Repite la medición.
3. Añade una sonda de arranque y una de vida. Justifica cada umbral con el tiempo real de arranque.
4. Provoca el error clásico: pon una sonda de vida más impaciente que el arranque y sin sonda de arranque. Describe lo que ves en `get pods` y en los eventos.
5. **Apagado.** Con las sondas bien puestas, ¿sigue habiendo algún error durante el reinicio? Si lo hay, explica de dónde sale y elimínalo. Tienes dos herramientas: `ESPERA_APAGADO_MS` y `preStop`. Ajusta `terminationGracePeriodSeconds` en consecuencia.
6. **Memoria.** Declara peticiones y límites. Baja el límite de memoria hasta que el contenedor muera. Encuentra dónde dice Kubernetes por qué murió y con qué código de salida.
7. **CPU.** Pon un límite de CPU de `100m` y mide el tiempo de respuesta de `/api/carga?ms=300` y el campo `resultado` de su respuesta (la API gasta CPU durante un tiempo fijo, así que fíjate en los dos valores). Quita el límite y vuelve a medir. Explica la diferencia.
8. Pide más CPU de la que tiene cualquier nodo. ¿En qué estado queda el pod y qué dice el evento?
9. Consulta la clase de calidad de servicio de tus pods. Consigue un pod de cada una de las tres clases y explica qué implica cada una cuando el nodo se queda sin memoria.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Cero errores durante `rollout restart` | El bucle del paso 1, con el arranque lento activo |
| Ningún reinicio inesperado tras diez minutos | Columna `RESTARTS` en cero |
| Cada umbral está justificado | `ANALISIS.md` relaciona cada número con una medición |
| Los pods son `Burstable` o `Guaranteed` | `kubectl get pod -o jsonpath='{.status.qosClass}'` |

**Entrega:** rama `taller/k8s-04`, con los manifiestos, el guion de medición y `taller/solucion/ANALISIS.md` con una tabla de errores antes y después de cada cambio.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Estas tres cosas separan un despliegue de aficionado de uno profesional. La mayoría de los "microcortes" durante los despliegues, los reinicios en cadena y los nodos que se caen de madrugada vienen de sondas mal pensadas o de recursos sin declarar, no de errores del código.

## Preguntas senior

1. ¿Por qué hay tres sondas y qué pasaría si usaras una sola para todo?
2. ¿Qué debe y qué no debe comprobar una sonda de vida?
3. ¿Por qué pueden llegar peticiones a un pod que ya recibió `SIGTERM`, y cómo lo resuelves?
4. Diferencia entre superar el límite de memoria y el de CPU.
5. ¿Pondrías límite de CPU? Argumenta a favor y en contra.
6. Una aplicación Java muere por `OOMKilled` pero su montón nunca se llena. ¿Qué está pasando?
