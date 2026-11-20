# Kubernetes 05: análisis

## Controlador elegido: Envoy Gateway v1.9.3

Instalado con `helm install eg oci://docker.io/envoyproxy/gateway-helm --version v1.9.3 -n envoy-gateway-system --create-namespace --wait`. Crea el controlador `gateway.envoyproxy.io/gatewayclass-controller`; la `GatewayClass` `eg` (`envoy.yaml`, en esta carpeta) lo referencia.

Por qué este: implementa Gateway API completa (pesos, cabeceras, redirecciones) sin anotaciones, se instala con un solo chart y su proxy es Envoy. Alternativas: NGINX Gateway Fabric (más cercano a quien viene de nginx, menos funciones de enrutamiento en su versión actual) y Cilium (exige cambiar el complemento de red del clúster, que es más invasivo). Costo: Envoy consume más memoria que un nginx.

## Cómo llega el navegador (paso 2)

kind publica `127.0.0.1:50080 -> nodo:30080` (`extraPortMappings`); el proxy de Envoy se expone como `NodePort` 30080 (parámetro `EnvoyProxy`). **Causa de una petición que no respondía:** Envoy Gateway crea el `Service` del proxy con `externalTrafficPolicy: Local`, y con `Local` un `NodePort` solo responde en los nodos que alojan una copia del proxy (comprobado: con `Local` el puerto del equipo da 000 si el proxy está en otro nodo; con `Cluster` da 200 desde cualquier nodo). No es una limitación de kind ni de Docker Desktop. Una versión anterior de esta solución diagnosticó mal esto y fijó el proxy al nodo del mapeo; la versión actual usa `externalTrafficPolicy: Cluster` y dos copias del proxy en nodos distintos (`envoy.yaml`, en esta carpeta), así que el nodo del mapeo ya no es un punto único. Coste de `Cluster`: la IP de origen de los clientes se pierde (SNAT). Con eso, `http://bitacora.localhost:50080` funciona (curl resuelve `*.localhost` a 127.0.0.1).

## Resultados medidos

| Paso | Resultado |
|---|---|
| 3 | `/` es la página, `/api` va a la API; POST de notas 201 |
| 4 | Gateway `Accepted` y `Programmed`. Ruta rota: `Accepted=True` pero `ResolvedRefs=False (BackendNotFound)` con el mensaje «service bitacora/bitacora-api-no-existe not found»; `kubectl apply` no protesta |
| 5 | `/salud/vivo` y `/salud/listo` desde fuera: 404 (la ruta `/` va a nginx, que no tiene esas rutas). No llegan a la API porque ninguna regla las manda a la API: solo `/api/...` |
| 6 | 200 peticiones al 10 %: entre 177 y 182 a la 1.0.0 y entre 18 y 23 a la 2.0.0 según la ejecución |
| 7 | 50 de 50 peticiones con `X-Probador: si` a la 2.0.0 |
| 8 | 50/50: 19 y 21 de 40; 0/100: 40 de 40 a la 2.0.0; el Service estable `bitacora-api` pasa a seleccionar los pods de la 2.0.0 y se retira el Deployment viejo: 0 errores en el bucle interno y 0 en el bucle desde el equipo por `bitacora.localhost:50080` |
| 8 (nombre estable) | Decisión: la web resuelve `bitacora-api` al arrancar, y nginx no arranca (`host not found in upstream`) si ese Service no existe. Por eso no se borra el Service `bitacora-api`: se cambia su selector (`servicio-final.yaml`) y se borra el `bitacora-api-v2`. La prueba reinicia la web después de retirar la v1 y comprueba que arranca y responde 200 |
| 10 | `/notas` responde 302 con `location: http://bitacora.localhost:50080/`; `/api/notas` no se redirige |

## Ingress equivalente (paso 9)

`ingress-equivalente.yaml` expresa host y rutas (el paso 3). **No se puede expresar sin anotaciones de un controlador concreto:** los pesos del canario (paso 6), la regla por cabecera (paso 7) y, según el controlador, la redirección (paso 10). ingress-nginx, además, está retirado.

## Criterios

- Ningún Service de la aplicación es `LoadBalancer` o `NodePort`: todos `ClusterIP`; solo el del controlador es `NodePort`.

## Dos comprobaciones añadidas tras la revisión

- **Política de tráfico del `NodePort`** (prueba, sección «Por qué el NodePort responde en todos los nodos»): con `externalTrafficPolicy: Cluster` una petición que llega desde otro nodo al `NodePort` del plano de control (donde no hay proxy) devuelve 200; al cambiar el Service a `Local` devuelve 000, mientras que a otro nodo que sí aloja un proxy sigue devolviendo 200; al volver a `Cluster`, 200. Se mide desde otro nodo porque el tráfico originado en el propio nodo no pasa por esta política.
- **Reiniciar la web tras retirar la v1**: la web resuelve `bitacora-api` al arrancar. Con el Service estable (ver paso 8) la web se reinicia sin problema. Durante ese reinicio, la web (que no tiene `preStop`) puede devolver algún 503 por la entrada mientras el proxy actualiza sus destinos; `probar.sh` lo registra y exige que, ya estable, 20 de 20 peticiones den 200. Una web de producción llevaría el mismo `preStop` que la API.
