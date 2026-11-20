# 05 — Tráfico de entrada: Gateway API

## Idea

La recepción de un edificio de oficinas. Hay una sola puerta a la calle; el recepcionista mira a qué empresa vas y te indica el piso. Las empresas se mudan de oficina sin que cambie la dirección del edificio. Y hay dos responsables distintos: la administración decide cuántas puertas hay y quién puede entrar; cada empresa decide a qué despacho va su visita.

## Lo esencial

Un Service `ClusterIP` solo se ve dentro del clúster. Para recibir tráfico de fuera hacen falta dos cosas: una **regla** que diga qué petición va a qué Service, y un **controlador** que la cumpla (un proxy que alguien instala en el clúster). Kubernetes define las reglas; el controlador lo eliges tú.

### Dos API para lo mismo

| | Ingress | Gateway API |
|---|---|---|
| Estado | Estable y soportada, pero ya no evoluciona | La sucesora recomendada |
| Qué expresa | Host y ruta | Host, ruta, cabeceras, pesos, redirecciones, reescrituras y más |
| Lo que falta | Se resuelve con anotaciones propias de cada controlador | Está en la especificación |
| Reparto de responsabilidades | Un solo objeto | Varios objetos, cada uno con su dueño |

Contexto que debes conocer: **ingress-nginx**, el controlador de Ingress más usado y mantenido por la comunidad de Kubernetes, se retiró en marzo de 2026 y ya no recibe correcciones. La API Ingress sigue existiendo y hay otros controladores que la implementan, pero para un despliegue nuevo el camino recomendado por el proyecto es Gateway API. Vas a encontrar mucho Ingress en sistemas existentes: hay que saber leerlo y saber migrarlo.

### Los objetos de Gateway API

| Objeto | Quién lo administra | Qué define |
|---|---|---|
| `GatewayClass` | Quien instala el controlador | Qué implementación atiende |
| `Gateway` | El equipo de plataforma | Los puntos de entrada: puertos, protocolos, certificados, qué espacios de nombres pueden engancharse |
| `HTTPRoute` | El equipo de la aplicación | Qué peticiones van a qué Service |

## Ejemplo

### Antes

<!-- file: ejemplo/antes/ingress.yaml -->
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: tienda
spec:
  ingressClassName: nginx
  rules:
    - host: tienda.localhost
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: pedidos
                port:
                  number: 80
          - path: /
            pathType: Prefix
            backend:
              service:
                name: web
                port:
                  number: 80
---
# Para enviar el 10 % del tráfico a la versión nueva hace falta un segundo Ingress con anotaciones.
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: tienda-canario
  annotations:
    nginx.ingress.kubernetes.io/canary: "true"
    nginx.ingress.kubernetes.io/canary-weight: "10"
spec:
  ingressClassName: nginx
  rules:
    - host: tienda.localhost
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: pedidos-v2
                port:
                  number: 80
```

### Análisis

| Problema | Consecuencia |
|---|---|
| El reparto por pesos vive en anotaciones | Solo las entiende ese controlador. Cambiar de controlador obliga a reescribirlas |
| Las anotaciones son texto libre | Un error de escritura no da error: simplemente no hace nada |
| Depende de un controlador retirado | Sin correcciones de seguridad en la puerta de entrada del sistema |
| Un solo objeto para todo | Quien puede cambiar una ruta puede cambiar también la configuración de entrada |

### Después

<!-- file: ejemplo/despues/gateway.yaml -->
```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: entrada
spec:
  gatewayClassName: mi-controlador     # la que haya instalado tu controlador: kubectl get gatewayclass
  listeners:
    - name: http
      protocol: HTTP
      port: 80
      allowedRoutes:
        namespaces:
          from: Same
```

<!-- file: ejemplo/despues/httproute.yaml -->
```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: tienda
spec:
  parentRefs:
    - name: entrada
  hostnames:
    - tienda.localhost
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: /api
      backendRefs:
        - name: pedidos
          port: 80
          weight: 90
        - name: pedidos-v2
          port: 80
          weight: 10
    - matches:
        - path:
            type: PathPrefix
            value: /
      backendRefs:
        - name: web
          port: 80
```

- Los pesos son parte de la especificación: cualquier controlador conforme los entiende.
- Un campo mal escrito lo rechaza el servidor de la API.
- El `Gateway` y la `HTTPRoute` pueden tener dueños y permisos distintos.

```bash
kubectl get gateway,httproute
kubectl describe httproute tienda     # mira las condiciones: Accepted y ResolvedRefs
```

Los manifiestos se aplicaron en un clúster kind (Kubernetes v1.37.0) con Envoy Gateway v1.9.3, cambiando solo `gatewayClassName` por el de una `GatewayClass` creada aparte para ese controlador (el chart de Envoy Gateway no instala ninguna): el `Gateway` queda `Programmed`, la `HTTPRoute` `Accepted` y `ResolvedRefs`, y el reparto 90/10 de `httproute.yaml` se cumple (en 100 peticiones, entre 7 y 10 fueron a `pedidos-v2` en las mediciones; el número varía en cada ejecución). Si llegas desde el equipo por un `NodePort` (por ejemplo con un mapeo de puertos de kind) y el `Service` del proxy usa `externalTrafficPolicy: Local` (el valor por defecto de Envoy Gateway), solo responden los nodos que alojan una copia del proxy: mapea el puerto a uno de ellos o usa `Cluster`.

## Errores comunes

- Crear las reglas sin haber instalado ningún controlador: los objetos se guardan y nada los atiende.
- No leer las condiciones de la ruta: una `HTTPRoute` rechazada no da error al aplicarla.
- Publicar cada servicio con su propio `LoadBalancer` y pagar un balanceador por servicio.
- Olvidar que el orden y la especificidad de las reglas deciden cuál gana.
- Exponer rutas internas (salud, métricas, administración) por la entrada pública.
- Copiar anotaciones de un controlador a otro.

## Taller

**Objetivo:** que Bitácora se use desde tu navegador con un nombre de host, sin `port-forward`, y hacer un despliegue canario.

No hay archivos de inicio. Parte de tus manifiestos de la sesión 04.

1. Elige un controlador que implemente Gateway API (por ejemplo Envoy Gateway, NGINX Gateway Fabric, Traefik, Istio o Cilium) e instálalo siguiendo su documentación. Anota qué versión instalaste y qué `GatewayClass` creó. Algunas distribuciones locales ya traen uno: compruébalo antes de instalar nada.
2. Resuelve cómo llega tu navegador al controlador en tu clúster local. Depende de la herramienta: mapeo de puertos al crear el clúster, un balanceador local o un `NodePort`. Documenta lo que hiciste; es la parte que más cambia entre entornos.
3. Crea un `Gateway` y una `HTTPRoute` para `bitacora.localhost`: `/api` va directo al Service de la API y todo lo demás al de la web.
4. Comprueba las condiciones de ambos objetos. Luego rompe la ruta a propósito (un Service que no existe) y encuentra dónde lo reporta Kubernetes.
5. Las rutas `/salud/…` no deben ser alcanzables desde fuera. Demuéstralo.
6. **Canario.** Despliega una segunda versión de la API (otro Deployment y otro Service, con `VERSION=2.0.0`). Envía el 10 % del tráfico a la nueva. Haz doscientas peticiones y cuenta cuántas respondió cada versión.
7. Añade una regla para que las peticiones con la cabecera `X-Probador: si` vayan siempre a la versión nueva.
8. Completa el paso a la versión nueva (50 %, 100 %) y retira la anterior, sin un solo error en el bucle de medición de la sesión 04.
9. Escribe el `Ingress` equivalente a tu `HTTPRoute` del paso 3. ¿Qué partes de los pasos 6 y 7 no puedes expresar sin anotaciones?
10. Añade una redirección: `/notas` debe responder con una redirección a `/`.

**Criterios de aceptación**

- `http://bitacora.localhost` (con el puerto que corresponda) muestra la página completa y guarda notas.
- El reparto del paso 6 se demuestra con números.
- Ningún Service es de tipo `LoadBalancer` o `NodePort`, salvo el del propio controlador.
- `ANALISIS.md` dice qué controlador elegiste y por qué.

**Entrega:** rama `taller/k8s-05`, con los manifiestos, las órdenes de instalación que usaste y `taller/solucion/ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

La entrada al clúster la suele administrar un equipo de plataforma, y a ti te toca escribir la ruta de tu servicio. Los proveedores de nube tienen sus propios controladores, que crean balanceadores reales y cuestan dinero. Y las migraciones de Ingress a Gateway API son trabajo corriente ahora mismo: saber hacer una sin corte es una habilidad que se paga.

## Preguntas senior

1. ¿Por qué hace falta un controlador además de las reglas?
2. ¿Qué problema de Ingress resuelve Gateway API con su separación en tres objetos?
3. ¿Cómo migrarías de Ingress a Gateway API sin corte?
4. Diferencia entre un despliegue canario por pesos y una actualización gradual del Deployment.
5. ¿Dónde terminarías TLS y por qué?
6. Una ruta devuelve 404 y el Service funciona desde dentro del clúster. ¿Qué revisas, en orden?
