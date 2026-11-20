# 06 — Almacenamiento y estado

## Idea

Un guardamuebles. Tú no compras la bodega ni sabes en qué pasillo queda: pides "un depósito de diez metros" y te dan un contrato y una llave. Puedes cambiar de apartamento las veces que quieras y tus cosas siguen en su depósito. Lo que no puedes es darle la misma llave a tres personas y esperar que no se estorben.

## Lo esencial

| Objeto | Qué es |
|---|---|
| **PersistentVolume** (PV) | Un trozo de almacenamiento real: un disco de la nube, una carpeta de un nodo |
| **PersistentVolumeClaim** (PVC) | La petición de un pod: "necesito 1 GiB con este modo de acceso" |
| **StorageClass** | La receta para crear volúmenes cuando alguien los pide |

El pod monta la petición, no el disco. Si el pod se recrea, vuelve a montar la misma petición.

### Modos de acceso

| Modo | Significado |
|---|---|
| `ReadWriteOnce` | Lectura y escritura desde **un solo nodo**. Es lo que ofrecen casi todos los discos |
| `ReadOnlyMany` | Solo lectura desde varios nodos |
| `ReadWriteMany` | Lectura y escritura desde varios nodos. Requiere un sistema de archivos en red |
| `ReadWriteOncePod` | Un solo pod |

### Deployment o StatefulSet

| | Deployment | StatefulSet |
|---|---|---|
| Identidad de los pods | Intercambiables, nombres al azar | Estable: `db-0`, `db-1` |
| Almacenamiento | Todos comparten la misma petición | Cada uno tiene la suya (`volumeClaimTemplates`) |
| Orden | Todos a la vez | Uno por uno, en orden |
| Para qué | Servicios sin estado | Bases de datos, colas, lo que tenga identidad |

Un StatefulSet **no** replica datos. Te da pods con identidad y disco propio; que los datos se copien de uno a otro es trabajo del propio software (la replicación de PostgreSQL, por ejemplo) o de un operador.

## Ejemplo

### Antes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: db
spec:
  replicas: 2
  selector:
    matchLabels:
      app: db
  template:
    metadata:
      labels:
        app: db
    spec:
      containers:
        - name: postgres
          image: postgres:17
          env:
            - name: POSTGRES_PASSWORD
              value: local
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Sin volumen | Cada vez que el pod se recrea, la base de datos empieza vacía |
| Dos copias de un Deployment | Son dos bases de datos independientes detrás de un mismo Service: cada consulta puede caer en una distinta |
| Contraseña en el manifiesto | Ver la sesión 03 |
| Actualización gradual | Por un momento hay copias viejas y nuevas del motor a la vez |

### Después

<!-- file: ejemplo/postgres.yaml -->
```yaml
apiVersion: v1
kind: Service
metadata:
  name: db
spec:
  clusterIP: None            # sin IP propia: da un nombre estable a cada pod (db-0.db)
  selector:
    app: db
  ports:
    - name: postgres
      port: 5432
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: db
spec:
  serviceName: db
  replicas: 1
  selector:
    matchLabels:
      app: db
  template:
    metadata:
      labels:
        app: db
    spec:
      containers:
        - name: postgres
          image: postgres:17
          ports:
            - name: postgres
              containerPort: 5432
          env:
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: db
                  key: password
            - name: PGDATA
              value: /var/lib/postgresql/data/pgdata
          volumeMounts:
            - name: datos
              mountPath: /var/lib/postgresql/data
          readinessProbe:
            exec:
              command: ["pg_isready", "-h", "127.0.0.1", "-U", "postgres"]
            periodSeconds: 5
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              memory: 256Mi
  volumeClaimTemplates:
    - metadata:
        name: datos
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 1Gi
```

- Una sola copia con disco propio. Es honesto: una base sin replicación configurada es una sola base.
- El pod se llama siempre `db-0` y siempre recibe la petición `datos-db-0`.
- `PGDATA` apunta a una subcarpeta, porque algunos volúmenes traen un `lost+found` en la raíz y PostgreSQL se niega a inicializarse ahí.
- La sonda usa `-h 127.0.0.1` a propósito: al arrancar con un disco vacío, la imagen levanta un servidor temporal que solo escucha en el socket Unix y luego lo reinicia. Sin `-h`, `pg_isready` responde «listo» durante esa inicialización y el pod se marca disponible antes de que PostgreSQL acepte conexiones por red.

El manifiesto se aplicó en un clúster kind (Kubernetes v1.37.0, StorageClass `standard` de `local-path`) con PostgreSQL 17.12: `db-0` se recrea con el mismo nombre y conserva los datos.

```bash
kubectl get statefulset,pod,pvc,pv
kubectl delete pod db-0        # se recrea con el mismo nombre y los mismos datos
```

## Errores comunes

- Creer que subir `replicas` en un StatefulSet da alta disponibilidad.
- Montar un volumen `ReadWriteOnce` en un Deployment con varias copias repartidas en varios nodos.
- Borrar la petición "para limpiar" y perder los datos: según la política de la clase, el disco se elimina con ella.
- No tener copias de seguridad porque "está en un volumen persistente". Persistente no es respaldado.
- Guardar estado en `emptyDir`, que vive lo que vive el pod.
- Poner la base de datos de producción en el clúster sin tener quien la opere.

## Taller

**Objetivo:** entender por qué el estado es la parte difícil, usando el defecto de diseño que Bitácora tiene a propósito.

No hay archivos de inicio. Parte de tus manifiestos anteriores.

1. **El problema.** Con tres copias de la API, guarda cinco notas y consulta `/api/notas` veinte veces. Cuenta cuántas listas distintas obtienes. Explica por qué, mirando dónde escribe cada pod.
2. Reinicia el Deployment. ¿Qué pasó con las notas?
3. **Primer intento.** Crea una petición `ReadWriteOnce` y móntala en `DIRECTORIO_DATOS` de las tres copias. Observa en qué nodos quedan los pods y en qué estado. Si todos arrancaron, averigua si están en el mismo nodo y qué pasaría si no. Lee los eventos.
4. Revisa qué `StorageClass` tiene tu clúster y qué modos de acceso ofrece de verdad.
5. **Decide.** Tienes al menos tres caminos: una sola copia con su volumen, un StatefulSet con un volumen por copia, o sacar el estado a una base de datos. Analiza cada uno: ¿resuelve la inconsistencia?, ¿qué pasa durante una actualización?, ¿qué pasa si cae un nodo? Implementa el que consideres correcto para Bitácora tal como está y deja escrito cuál sería el correcto si pudieras cambiar el código.
6. Si elegiste una sola copia con volumen: ¿qué estrategia de actualización necesita el Deployment y por qué la gradual no sirve? ¿Cuánto dura el corte en cada despliegue? Mídelo.
7. **Una base de datos de verdad.** Despliega PostgreSQL 17 como StatefulSet con su Service sin IP, su Secret y su volumen. Crea una tabla e inserta filas con `psql` dentro del pod.
8. Borra el pod. ¿Vuelve con el mismo nombre? ¿Están las filas? Borra el StatefulSet entero. ¿Qué pasó con la petición y con los datos?
9. Escala el StatefulSet a dos. Inserta una fila en `db-0` y búscala en `db-1`. Explica el resultado.
10. **Respaldo.** Escribe un `CronJob` que haga un volcado de la base cada cierto tiempo hacia otro volumen. Ejecútalo una vez a mano y demuestra que puedes restaurar el volcado en una base vacía.

**Criterios de aceptación**

- El paso 1 se demuestra con números, no con una descripción.
- La decisión del paso 5 está argumentada con sus costos, no solo con sus ventajas.
- El paso 10 incluye la restauración. Un respaldo que nunca se restauró no cuenta.
- Ninguna contraseña está en el repositorio.

**Entrega:** rama `taller/k8s-06`, con los manifiestos y `taller/solucion/ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

La recomendación más repetida, y con razón: si puedes, usa la base de datos administrada de tu proveedor y deja el clúster para lo que no tiene estado. Cuando sí se ejecutan bases dentro de Kubernetes, se hace con operadores que automatizan la replicación, la conmutación y los respaldos. En cualquiera de los dos casos, tu aplicación debe estar diseñada sin estado propio: sesiones, archivos subidos y cachés van a un servicio externo.

## Preguntas senior

1. ¿Qué diferencia hay entre un volumen persistente y una petición de volumen?
2. ¿Por qué un volumen `ReadWriteOnce` complica tener varias copias?
3. ¿Qué te da un StatefulSet y qué no?
4. ¿Cuándo ejecutarías una base de datos dentro del clúster y cuándo no?
5. Tu aplicación guarda archivos subidos por los usuarios en el disco local. ¿Cómo la llevas a tres copias?
6. ¿Qué significa de verdad "tener respaldo"?
