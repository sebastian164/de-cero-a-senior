# 10 — Kustomize y taller integrador

## Idea

Un traje a medida no se cose de cero para cada cliente. El sastre parte de un patrón y anota los ajustes: dos centímetros más de manga, otro botón. El patrón es uno; los ajustes son pocos y están a la vista. Con los manifiestos pasa igual: una base común y, por ambiente, solo lo que cambia.

## Lo esencial

A estas alturas tienes una docena de manifiestos, y los ambientes se diferencian en muy poco: el número de copias, la etiqueta de la imagen, un mensaje, los recursos. Copiar la carpeta entera por ambiente garantiza que se desincronicen.

### Kustomize

Viene dentro de `kubectl`. No usa plantillas: toma manifiestos válidos y les aplica transformaciones declaradas en un archivo `kustomization.yaml`.

| Concepto | Qué es |
|---|---|
| **Base** | Los manifiestos comunes y un `kustomization.yaml` que los lista |
| **Superposición** (*overlay*) | Una carpeta por ambiente que referencia la base y declara los cambios |
| `images` | Cambia el nombre o la etiqueta de una imagen sin tocar el Deployment |
| `replicas` | Cambia el número de copias |
| `namespace` | Pone el espacio de nombres a todo |
| `configMapGenerator` | Genera el ConfigMap y le añade al nombre un resumen de su contenido |
| `patches` | Cambia cualquier otro campo |

El detalle del resumen en el nombre resuelve un problema de la sesión 03: al cambiar la configuración, cambia el nombre del ConfigMap, cambia la plantilla del pod y el Deployment hace una actualización gradual sola.

```bash
kubectl kustomize superposiciones/produccion      # muestra el resultado, sin aplicar
kubectl apply -k superposiciones/produccion
kubectl diff -k superposiciones/produccion        # qué cambiaría en el clúster
```

### ¿Y Helm?

| | Kustomize | Helm |
|---|---|---|
| Enfoque | Transformar manifiestos válidos | Plantillas con variables |
| Curva | Baja | Más alta |
| Ideal para | Tus propias aplicaciones en varios ambientes | Distribuir software a terceros con muchas opciones; instalar el de otros |
| Extras | Nada más | Versiones, vuelta atrás, dependencias entre paquetes |

No compiten del todo: lo habitual es instalar con Helm lo que publican otros (el controlador de entrada, el servidor de métricas) y usar Kustomize, o también Helm, para lo propio.

## Ejemplo

### Antes

```
manifiestos/
├── desarrollo/     deployment.yaml  service.yaml  configmap.yaml
├── pruebas/        deployment.yaml  service.yaml  configmap.yaml
└── produccion/     deployment.yaml  service.yaml  configmap.yaml
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Nueve archivos casi iguales | Un cambio hay que hacerlo tres veces, y alguna se olvida |
| Las diferencias reales están enterradas | Nadie sabe decir en qué se distingue producción de pruebas sin comparar archivo por archivo |
| Producción recibe cambios que nunca pasaron por pruebas | Porque se editó su copia directamente |

### Después

<!-- file: ejemplo/base/kustomization.yaml -->
```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - deployment.yaml
  - service.yaml
configMapGenerator:
  - name: pedidos-config
    literals:
      - NIVEL_REGISTRO=INFO
```

<!-- file: ejemplo/base/deployment.yaml -->
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pedidos
spec:
  replicas: 1
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
          image: ghcr.io/ejemplo/pedidos
          ports:
            - name: http
              containerPort: 8080
          envFrom:
            - configMapRef:
                name: pedidos-config
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              memory: 256Mi
```

<!-- file: ejemplo/base/service.yaml -->
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

<!-- file: ejemplo/superposiciones/produccion/kustomization.yaml -->
```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: pedidos-produccion
resources:
  - ../../base
  - espacio-de-nombres.yaml
images:
  - name: ghcr.io/ejemplo/pedidos
    newTag: 2.3.1
replicas:
  - name: pedidos
    count: 4
configMapGenerator:
  - name: pedidos-config
    behavior: merge
    literals:
      - NIVEL_REGISTRO=WARN
patches:
  - path: recursos.yaml
```

<!-- file: ejemplo/superposiciones/produccion/espacio-de-nombres.yaml -->
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: pedidos-produccion
```

<!-- file: ejemplo/superposiciones/produccion/recursos.yaml -->
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pedidos
spec:
  template:
    spec:
      containers:
        - name: pedidos
          resources:
            requests:
              cpu: 500m
              memory: 1Gi
            limits:
              memory: 1Gi
```

La superposición de producción se lee en diez segundos y dice exactamente en qué se diferencia: versión, copias, nivel de registro y recursos. El ejemplo se procesó con `kubectl kustomize` (Kustomize v5.8.1 integrado en `kubectl` v1.36.1) y se validó con `kubectl apply -k --dry-run=server` contra Kubernetes v1.37.0: el nombre del ConfigMap lleva el resumen, la etiqueta pasa a 2.3.1, las copias a 4 y `NIVEL_REGISTRO` a `WARN`.

## Errores comunes

- Superposiciones que parchean tanto que ya no se entiende qué sale. Si la superposición es más larga que la base, la base está mal elegida.
- Aplicar sin mirar antes el resultado con `kubectl kustomize` o `kubectl diff`.
- Secretos en claro en un generador dentro del repositorio.
- Etiquetas de imagen distintas por ambiente que no pasaron por el ambiente anterior.
- Usar Helm para tres manifiestos, o Kustomize para un producto con cincuenta opciones.

## Taller integrador

**Objetivo:** entregar Bitácora como la entregaría un equipo profesional: reproducible desde cero, en dos ambientes, y capaz de aguantar lo que le hagas.

No hay archivos de inicio: reúnes todo lo de las sesiones anteriores.

### Parte 1 — Estructura

1. Organiza tus manifiestos en una base y dos superposiciones: `desarrollo` y `produccion`.
2. Las superposiciones solo contienen diferencias. Como mínimo: espacio de nombres, etiqueta de imagen, número de copias, `MENSAJE`, recursos y nombre de host de la ruta.
3. La configuración se genera con `configMapGenerator`. Cambia un valor y demuestra que el Deployment se actualiza solo.
4. Ningún secreto está en el repositorio. Documenta cómo se crea en cada ambiente.

### Parte 2 — Requisitos de producción

La superposición de producción debe cumplir todo esto:

| Requisito | Sesión |
|---|---|
| Imágenes con versión fija, sin `latest` | Docker 06 |
| Tres copias o más de la API, repartidas entre nodos | 07 |
| Sondas de arranque, disponibilidad y vida | 04 |
| Peticiones y límites declarados | 04 |
| Apagado ordenado, sin errores al reiniciar | 04 |
| Entrada por Gateway API con nombre de host | 05 |
| Autoescalado y presupuesto de interrupción | 07 |
| Nivel `restricted` exigido en el espacio de nombres | 08 |
| Red cerrada por defecto y abierta solo donde hace falta | 08 |
| Una decisión explícita y argumentada sobre dónde vive el estado | 06 |

### Parte 3 — Reproducible

5. Escribe un guion (o un `Makefile`) que, partiendo de nada, cree el clúster, instale lo que haga falta, cargue las imágenes y despliegue un ambiente con una sola orden.
6. Destruye tu clúster y reconstrúyelo con el guion. Si algo no funciona a la primera, el guion no está terminado.
7. Añade una validación previa: una orden que revise los manifiestos generados sin necesitar un clúster (hay herramientas para validar contra los esquemas de Kubernetes y para revisar buenas prácticas). Debe poder ejecutarse en integración continua.

### Parte 4 — Simulacro

Con el bucle de medición corriendo contra el nombre de host público, ejecuta en orden y anota los errores de cada prueba:

| Prueba | Qué debe pasar |
|---|---|
| Borrar un pod de la API | Cero errores |
| Borrar todos los pods de la API a la vez | Errores durante unos segundos y recuperación sola. Mide cuánto. Con apagado ordenado, un borrado normal puede dar casi cero errores porque los pods que terminan siguen atendiendo; para simular un fallo usa `--force --grace-period=0` |
| Reiniciar el Deployment | Cero errores |
| Desplegar una versión que nunca está lista | Cero errores; la actualización se detiene |
| Volver atrás | Cero errores |
| Vaciar un nodo | Cero errores |
| Detener un nodo de golpe | Errores breves. Mide cuánto tarda Kubernetes en reaccionar y explica por qué tarda eso |
| Pico de carga | Escala hacia arriba y luego hacia abajo |

### Parte 5 — Defensa

Escribe un documento de dos páginas para alguien que va a operar esto sin conocerte:

- Un diagrama de lo desplegado y de cómo viaja una petición desde el navegador hasta el pod.
- Las tres decisiones más discutibles que tomaste, con la alternativa que descartaste y por qué.
- Qué **no** resuelve tu despliegue: qué pasaría si se pierde el clúster entero, qué pasa con los datos, qué no está monitorizado.
- Qué harías distinto en una nube real y qué costaría.

**Criterios de aceptación**

- `kubectl apply -k` sobre cada superposición deja un ambiente funcionando.
- El guion reconstruye todo desde cero sin pasos manuales.
- La tabla del simulacro está completa, con números.
- El documento de defensa nombra límites reales, no solo virtudes.

**Entrega:** rama `taller/k8s-10`, o un repositorio propio enlazado desde el pull request, con `taller/solucion/ANALISIS.md` y el documento de defensa.

Todo este taller se hace en un clúster local y no cuesta nada. Si decides repetirlo en una nube, activa antes una alerta de presupuesto y elimina los recursos al terminar: un clúster administrado y sus balanceadores se cobran por hora.

## En el trabajo real

El paso siguiente en un equipo es que nadie ejecute `kubectl apply` a mano: una herramienta de entrega continua (Argo CD o Flux) vigila el repositorio y mantiene el clúster igual a lo que dice la rama principal. Para eso, todo lo que hiciste aquí —manifiestos declarativos, diferencias por ambiente a la vista, nada fuera del repositorio— es el requisito previo.

## Preguntas senior

1. ¿Qué problema resuelven las superposiciones y cuándo se vuelven un problema ellas mismas?
2. ¿Kustomize o Helm? Da un caso para cada uno.
3. ¿Cómo garantizas que lo que llega a producción pasó antes por pruebas?
4. Explica cómo viaja una petición desde el navegador hasta tu proceso, nombrando cada pieza.
5. De todo lo que configuraste, ¿qué quitarías para un servicio interno pequeño y por qué?
6. Tu despliegue aguantó el simulacro. Nombra tres fallos reales que no probaste.
