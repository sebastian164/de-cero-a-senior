# 02 — Deployments y Services

## Idea

Una central de taxis. Tú no llamas al conductor del taxi 27: llamas a la central, que tiene un número fijo y te manda el que esté libre. Si un taxi se avería, la central pone otro y tú ni te enteras. El Deployment es quien mantiene la flota completa; el Service es el número fijo.

## Lo esencial

### Deployment

Declara cuántas copias de un pod quieres y con qué plantilla. Por debajo crea un **ReplicaSet**, que es quien cuenta pods y repone los que faltan. Cuando cambias la plantilla, el Deployment crea un ReplicaSet nuevo y va pasando las copias del viejo al nuevo: eso es una actualización gradual.

### Etiquetas y selectores

Las etiquetas son pares clave-valor que pones a los objetos. Los selectores buscan por etiqueta. **Es el único pegamento**: un Deployment encuentra sus pods por etiqueta, y un Service encuentra a quién enviar tráfico por etiqueta. No hay referencias por nombre. Si la etiqueta no coincide, no hay error: simplemente no pasa nada.

### Service

Los pods cambian de IP cada vez que se recrean. Un Service da un nombre y una IP estables, y reparte el tráfico entre los pods que cumplen su selector **y están listos**.

| Tipo | Alcance |
|---|---|
| `ClusterIP` | Solo dentro del clúster. Es el tipo por defecto |
| `NodePort` | Abre un puerto en cada nodo |
| `LoadBalancer` | Pide un balanceador al proveedor de nube |

### Nombres

Dentro del clúster, un Service se resuelve por nombre:

- `pedidos` desde el mismo espacio de nombres.
- `pedidos.tienda` desde otro.
- `pedidos.tienda.svc.cluster.local` es el nombre completo.

## Ejemplo

### Antes

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: pedidos-1
spec:
  containers:
    - name: pedidos
      image: ghcr.io/ejemplo/pedidos:2.3.1
```

y, en la configuración de otro servicio: `PEDIDOS_URL=http://10.244.1.17:8080`.

### Análisis

| Problema | Consecuencia |
|---|---|
| Un pod suelto | Si muere, no vuelve. Para tener tres hay que copiar el archivo tres veces |
| IP del pod escrita en la configuración | Deja de funcionar la primera vez que el pod se recrea |
| Sin etiquetas | Nada puede seleccionarlo |
| Actualizar es borrar y crear | Hay un corte en cada despliegue |

### Después

<!-- file: ejemplo/deployment.yaml -->
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pedidos
  labels:
    app: pedidos
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
      containers:
        - name: pedidos
          image: ghcr.io/ejemplo/pedidos:2.3.1
          ports:
            - name: http
              containerPort: 8080
```

<!-- file: ejemplo/service.yaml -->
```yaml
apiVersion: v1
kind: Service
metadata:
  name: pedidos
spec:
  selector:
    app: pedidos
  ports:
    - name: http
      port: 80
      targetPort: http
```

- Tres copias, repuestas automáticamente.
- El selector del Deployment y las etiquetas de la plantilla coinciden. El selector del Service también.
- El Service escucha en el 80 y envía al puerto llamado `http` del pod. Usar el nombre evita desajustes si el número cambia.
- Los demás servicios usan `http://pedidos`.

La imagen del ejemplo no existe, así que los pods no arrancarían tal cual. Los dos manifiestos se validaron contra el servidor de la API (Kubernetes v1.37.0) y se aplicaron en un clúster kind cambiando solo la imagen por una que sí existe (la API de Bitácora): el Deployment queda con 3 copias listas y el Service con 3 destinos, y `http://pedidos` responde desde dentro del clúster.

```bash
kubectl get deployment,replicaset,pod -l app=pedidos
kubectl get endpointslices -l kubernetes.io/service-name=pedidos   # a qué pods envía el Service
kubectl rollout status deployment/pedidos
kubectl rollout history deployment/pedidos
kubectl rollout undo deployment/pedidos
```

## Errores comunes

- Selector del Service que no coincide con las etiquetas de los pods: el Service existe y no tiene a quién enviar.
- `targetPort` distinto del puerto en que escucha la aplicación.
- Cambiar el selector de un Deployment ya creado (es inmutable).
- Probar el balanceo con `kubectl port-forward` hacia el Service: el túnel elige un solo pod y se queda con él.
- Escalar con `kubectl scale` y que el siguiente `apply` lo devuelva al valor del archivo.
- Guardar estado en el pod y creer que tres copias lo comparten.

## Taller

**Objetivo:** desplegar la API y la web de Bitácora con varias copias, conectarlas por nombre y ver la autorreparación y las actualizaciones.

No hay archivos de inicio. Necesitas `bitacora-api:1.0.0` y `bitacora-web:0.1.0` cargadas en el clúster. Trabaja en un espacio de nombres `bitacora`, declarado en un archivo.

1. Escribe un Deployment de la API con tres copias y un Service `ClusterIP`.
2. Demuestra el balanceo: desde un pod temporal dentro del clúster, llama veinte veces a `/api/info` por el nombre del Service y cuenta cuántos valores distintos de `host` aparecen.
3. Repite la prueba con `kubectl port-forward service/…`. Explica la diferencia.
4. Borra un pod y luego dos a la vez. Mide cuánto tarda en volver a haber tres. ¿Quién los repuso, el Deployment o el ReplicaSet?
5. Despliega la web con dos copias y su Service. Configura `DESTINO_API` con el nombre del Service de la API. Llega a la página con `port-forward` y comprueba que funciona completa.
6. Rompe el selector del Service de la API a propósito. ¿Qué error muestra `kubectl apply`? ¿Qué ve el usuario? Encuentra la orden que delata el problema.
7. Guarda tres notas desde la página y recarga varias veces. ¿Por qué la lista cambia? No lo arregles todavía; explica la causa.
8. Publica una versión nueva: vuelve a etiquetar tu imagen como `1.0.1`, cárgala y cambia el manifiesto. Sigue la actualización con `kubectl rollout status` y con `kubectl get replicaset -w`. Describe lo que hacen los dos ReplicaSet.
9. Despliega una etiqueta que no existe. ¿Se cae el servicio? ¿Cuántos pods viejos siguen atendiendo? Vuelve atrás con `rollout undo`.
10. Escala a cinco con `kubectl scale` y luego aplica tu archivo. ¿Cuántas copias quedan? Saca la conclusión.
11. Desde un pod en otro espacio de nombres, llega a la API. ¿Qué nombre usaste?

**Criterios de aceptación**

- Todo está en manifiestos; el espacio de nombres también.
- La web encuentra a la API por nombre de Service.
- Las preguntas de los pasos 3, 6, 7 y 10 tienen respuesta con evidencia.
- El paso 9 demuestra, con salidas, que el servicio siguió disponible.

**Entrega:** rama `taller/k8s-02`, con los manifiestos y `taller/solucion/ANALISIS.md`.

## En el trabajo real

Deployment y Service son la pareja con la que se despliega casi cualquier servicio sin estado, incluida una aplicación Spring Boot o un frontend servido con nginx. Las etiquetas, además, son como se filtra todo: registros, métricas, costos y políticas de red.

## Preguntas senior

1. ¿Qué hace el ReplicaSet y por qué no lo creas tú directamente?
2. ¿Cómo sabe un Service a qué pods enviar tráfico y qué pasa si ninguno coincide?
3. ¿Por qué no se usan las IP de los pods?
4. Durante una actualización gradual conviven dos versiones. ¿Qué exige eso de tu aplicación y de tu base de datos?
5. ¿Qué diferencia hay entre `ClusterIP`, `NodePort` y `LoadBalancer`?
6. Un Service responde a veces sí y a veces no. ¿Qué revisas?
