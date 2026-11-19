# 07 — Escalado y actualizaciones

## Idea

Un supermercado. Cuando las filas crecen abren más cajas, y las cierran cuando baja la gente; nadie abre veinte cajas todo el día por si acaso. Y cuando cambian a los cajeros de turno no cierran todas las cajas a la vez: releva uno, luego otro, y siempre queda alguien cobrando.

## Lo esencial

### Actualización gradual

Un Deployment cambia de versión reemplazando pods poco a poco. Dos números lo gobiernan:

| Campo | Significado |
|---|---|
| `maxUnavailable` | Cuántos pods pueden faltar respecto al número deseado durante el cambio |
| `maxSurge` | Cuántos pods de más se pueden crear |

Con `maxUnavailable: 0` y `maxSurge: 1`, nunca hay menos capacidad que la declarada: se crea uno nuevo, se espera a que esté listo y solo entonces se retira uno viejo. **Si el nuevo nunca llega a estar listo, la actualización se detiene sola** y los viejos siguen atendiendo. Por eso la sonda de disponibilidad es también tu red de seguridad en los despliegues.

La otra estrategia es `Recreate`: se eliminan todos y luego se crean los nuevos. Hay corte, pero nunca conviven dos versiones.

### Escalado horizontal automático

El **HorizontalPodAutoscaler** ajusta el número de copias según una métrica. Para CPU, compara el uso con las **peticiones** del pod: un objetivo del 60 % significa "el 60 % de lo que pidió". Sin `requests`, no puede calcular nada. Necesita un servidor de métricas instalado en el clúster.

- Sube rápido y baja despacio, a propósito, para no oscilar.
- No sirve de nada si la aplicación tarda más en arrancar que lo que dura el pico.
- La CPU no siempre es la señal correcta: una aplicación que espera a una base de datos lenta está saturada con la CPU ociosa.

### Interrupciones voluntarias

Cuando alguien vacía un nodo para mantenerlo, los pods se desalojan. Un **PodDisruptionBudget** le dice al clúster cuántos pods de una aplicación deben seguir disponibles mientras eso ocurre. Solo protege de interrupciones voluntarias; si el nodo se quema, no hay presupuesto que valga.

### Repartir las copias

Tres copias en el mismo nodo no son alta disponibilidad. `topologySpreadConstraints` pide repartirlas entre nodos o zonas.

## Ejemplo

### Antes

```yaml
spec:
  replicas: 2
  strategy:
    rollingUpdate:
      maxUnavailable: 50%
      maxSurge: 0
```

y un `kubectl scale --replicas=10` cada vez que hay una campaña.

### Análisis

| Problema | Consecuencia |
|---|---|
| `maxUnavailable: 50%` con dos copias | Cada despliegue deja el servicio en una sola copia, justo cuando arranca código nuevo |
| Escalado manual | Alguien tiene que acordarse, y acordarse de bajarlo |
| Sin presupuesto de interrupción | Un mantenimiento de nodos puede desalojar las dos copias a la vez |
| Sin reparto | Las dos copias pueden estar en el mismo nodo |

### Después

<!-- file: ejemplo/escalado.yaml -->
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pedidos
spec:
  # Sin "replicas": el número lo gobierna el autoescalador.
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: pedidos
  template:
    metadata:
      labels:
        app: pedidos
    spec:
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: kubernetes.io/hostname
          whenUnsatisfiable: ScheduleAnyway
          labelSelector:
            matchLabels:
              app: pedidos
      containers:
        - name: pedidos
          image: ghcr.io/ejemplo/pedidos:2.3.1
          resources:
            requests:
              cpu: 250m
              memory: 512Mi
            limits:
              memory: 512Mi
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: pedidos
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: pedidos
  minReplicas: 3
  maxReplicas: 12
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: pedidos
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: pedidos
```

- El Deployment no fija `replicas`: si lo hiciera, cada `apply` pelearía con el autoescalador.
- Nunca hay menos capacidad que la deseada durante un despliegue.
- Un mantenimiento no puede dejar menos de dos copias.

```bash
kubectl get hpa pedidos -w
kubectl rollout status deployment/pedidos
kubectl rollout undo deployment/pedidos
kubectl drain <nodo> --ignore-daemonsets --delete-emptydir-data
kubectl uncordon <nodo>
```

## Errores comunes

- Autoescalador y `replicas` en el mismo manifiesto.
- Autoescalar sin `requests`, o con peticiones que no se parecen al consumo real.
- Un máximo tan alto que un error en bucle agota el clúster o la cuenta.
- `minAvailable` igual al número de copias: ningún nodo se puede vaciar nunca.
- Escalar la aplicación y olvidar que la base de datos tiene un límite de conexiones.
- Volver atrás una versión sin pensar en los cambios de esquema que ya se aplicaron.

## Taller

**Objetivo:** hacer que Bitácora aguante un pico, un despliegue malo y el mantenimiento de un nodo sin intervención manual.

No hay archivos de inicio. Parte de tus manifiestos de la sesión 05. Para este taller usa la API en modo sin estado: ignora las notas y trabaja con `/api/info` y `/api/carga`.

1. Instala el servidor de métricas en tu clúster siguiendo su documentación. En clústeres locales suele necesitar un ajuste para aceptar los certificados de los nodos: averigua cuál y por qué. Comprueba que `kubectl top pods` responde.
2. Crea un autoescalador para la API: mínimo 2, máximo 6, objetivo de CPU razonado a partir de tus peticiones.
3. Genera carga desde dentro del clúster contra `/api/carga`. Registra en una tabla el número de copias y el uso de CPU cada treinta segundos, hasta que suba y, después de parar la carga, hasta que baje.
4. ¿Cuánto tardó en subir? ¿Y en bajar? Explica la diferencia y ajusta `behavior` para que baje más rápido. ¿Qué riesgo tiene?
5. Deja `replicas` en el Deployment y aplica varias veces mientras hay carga. Describe la pelea y corrígela.
6. **Despliegue malo.** Con `maxUnavailable: 0`, despliega una versión que nunca llega a estar lista (por ejemplo, con `DIRECTORIO_DATOS` apuntando a un sitio donde no puede escribir). Con el bucle de medición de la sesión 04 corriendo, demuestra que no hubo errores. ¿En qué estado queda la actualización? ¿Qué papel juega `progressDeadlineSeconds`? Vuelve atrás.
7. Repite el paso 6 con `maxUnavailable: 50%`. Compara.
8. Cambia la estrategia a `Recreate` y mide el corte. ¿Cuándo es la opción correcta?
9. Crea un presupuesto de interrupción y vacía un nodo con `kubectl drain`. Observa cómo espera. Luego pon `minAvailable` igual al número de copias y vuelve a intentarlo.
10. Comprueba en qué nodos están tus copias. Añade una restricción de reparto y demuestra que quedan distribuidas. Si usas `DoNotSchedule` en kind, el plano de control (con su taint) cuenta como un dominio vacío y deja pods en `Pending`: añade `nodeTaintsPolicy: Honor` a la restricción.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| La API escala sola hacia arriba y hacia abajo | La tabla del paso 3 |
| Un despliegue malo no produce errores | El bucle del paso 6 con cero fallos |
| Se puede vaciar un nodo sin corte | El bucle durante el paso 9 |
| Las copias están en nodos distintos | `kubectl get pods -o wide` |

**Entrega:** rama `taller/k8s-07`, con los manifiestos, los guiones de carga y `taller/solucion/ANALISIS.md`.

## En el trabajo real

El escalado automático por CPU es el punto de partida. En sistemas reales se escala también por métricas propias (peticiones por segundo, mensajes pendientes en una cola) con herramientas como KEDA, y los nodos del clúster se añaden y retiran solos. Volver atrás una versión es fácil para el código y difícil para los datos: por eso los cambios de esquema se hacen compatibles hacia atrás.

## Preguntas senior

1. Explica `maxUnavailable` y `maxSurge`, y qué valores elegirías para un servicio crítico con tres copias.
2. ¿Por qué un despliegue con una versión rota no tumba el servicio, y de qué depende eso?
3. ¿Contra qué se mide el porcentaje de CPU del autoescalador?
4. ¿Cuándo la CPU es una mala métrica para escalar?
5. ¿De qué protege un presupuesto de interrupción y de qué no?
6. Tu servicio escala de 3 a 30 copias y el sistema empeora. ¿Qué pudo pasar?
