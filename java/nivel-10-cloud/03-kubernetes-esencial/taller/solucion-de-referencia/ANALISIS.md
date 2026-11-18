# Taller 10-03: Kubernetes esencial (solución en local)

Todo corre en un clúster **kind** (Kubernetes 1.37, un nodo) sobre Docker Desktop, con la aplicación `pedidos` de `aplicacion/` y el `Deployment` del README de la sesión extraído tal cual (3 réplicas, las tres sondas, `MaxRAMPercentage=75`, límite de 768 Mi). La carga se genera con k6. `taller.sh` hace todo y borra el clúster al terminar.

## 1-2. Deployment, Service, ConfigMap, Secret y sondas

- La imagen `registro.example/pedidos:1.4.2` no existe en ningún registro: se construye en local y se carga en el nodo con `kind load docker-image` (el `imagePullPolicy` por defecto, `IfNotPresent`, evita descargarla).
- El **ConfigMap** (`k8s/configuracion.yaml`) llega como variables de entorno (`APP_AMBIENTE=kind`, `APP_MENSAJE`, URL y usuario de la base). El **Secret** con la clave de la base se crea con `kubectl create secret` y nunca aparece en un archivo.
- El Secret está solo **codificado en base64, no cifrado**: `kubectl get secret -o jsonpath` más `base64 -d` lo devuelve. El script lo comprueba para dejarlo a la vista.
- La JVM con `MaxRAMPercentage=75` y un límite de 768 Mi toma unos 576 MB de montón.
- PostgreSQL corre en el mismo clúster (`k8s/base-datos.yaml`), y el Service `pedidos-externo` de tipo NodePort (`k8s/servicio-nodeport.yaml`) lo publica en el anfitrión a través del mapeo de puertos de kind.

## 3. Borrar un pod y escalar a cinco

Al borrar un pod, el ReplicaSet crea otro con **nombre distinto** y el Deployment vuelve a tener 3 réplicas listas: el estado deseado manda. `kubectl scale deploy/pedidos --replicas=5` deja 5 listas; después se vuelve a 3.

## 4. Una versión que no arranca no tumba el servicio

`Dockerfile.no-arranca` produce una imagen cuyo proceso termina al iniciar. Con `kubectl set image` el despliegue progresivo crea un pod nuevo, que nunca llega a estar listo, y **no retira los viejos**: con `maxUnavailable` del 25 % de 3 réplicas (redondeado a 0), los 3 pods de la 1.4.2 siguen listos. Durante 40 s de consultas al Service, **0 peticiones fallidas**. `kubectl rollout status` reconoce que no avanza (sale con error al agotar el tiempo) y `kubectl rollout undo` vuelve a la 1.4.2.

## 5. OOMKilled

`oom.yaml` reproduce el error clásico: `JAVA_TOOL_OPTIONS=-Xmx1g` dentro de un contenedor limitado a 300 Mi. Al pedir 500 MB a `/api/memoria`, el núcleo mata el proceso. `kubectl describe pod` muestra en `Last State`: `Reason: OOMKilled` y `Exit Code: 137`, sin ninguna excepción en el registro de la aplicación.

Ajuste: se quita el `-Xmx`, se sube el límite al del README (768 Mi) y se usa `-XX:MaxRAMPercentage=60`, que deja margen fuera del montón para metaspace, hilos y memoria directa. Pedir 700 MB ahora produce un `OutOfMemoryError: Java heap space` dentro de la JVM, el servicio responde 500, **el contenedor no se reinicia** (0 reinicios) y sigue sano.

## 6. Carga durante un despliegue: tres variantes, una variable cada una

Desde Spring Boot 3.4 el apagado ordenado es el valor por defecto, así que la variante «sin» se pide a propósito. Las tres usan **la misma gracia** (`terminationGracePeriodSeconds: 45`), la misma carga (20 usuarios, peticiones de 8 s, `reinicio` del Deployment a los 8 s) y cambian una sola cosa:

| Variante | `server.shutdown` | `preStop` | Resultado típico |
|---|---|---|---|
| (a) | `immediate` | no | de 3 a 8 peticiones **cortadas en vuelo** y de 4 a 10 rechazadas al conectar |
| (b) | `graceful` | no | **0 cortadas en vuelo**; de 0 a 7 rechazadas al conectar |
| (c) | `graceful` | `sleep 10` | **0 fallos de ambos tipos** |

Cada fallo se clasifica por la duración de la petición en k6: **cortada en vuelo** (más de 200 ms: la conexión estaba aceptada y se cerró con la petición dentro) o **rechazada al conectar** (menos de 200 ms: el Service todavía enviaba tráfico a un pod que ya no escuchaba).

Lo que enseña:

- El apagado ordenado resuelve las peticiones **en vuelo**: de (a) a (b) desaparecen los cortes.
- No resuelve las **nuevas** peticiones que llegan mientras el pod sale del Service. Quitar el pod de los *endpoints* y enviarle `SIGTERM` ocurren en paralelo; el `preStop` con una pausa corta da tiempo a que la red se entere antes de que la aplicación deje de aceptar conexiones. Por eso (c) es la única con cero fallos.
- **El total de fallos no separa (a) de (b)** (en distintas ejecuciones, (a) dio entre 4 y 17 y (b) entre 0 y 7): lo que las separa es el tipo de fallo. Con peticiones de 2 s en lugar de 8 s no se observaron cortes en vuelo, así que la prueba exige peticiones largas.

Al terminar, el Deployment se deja como lo describe el README.
