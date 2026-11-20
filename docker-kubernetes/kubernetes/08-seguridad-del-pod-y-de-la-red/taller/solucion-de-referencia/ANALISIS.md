# Kubernetes 08: análisis

Clúster kind v1.37.0 con `kindnet`, que **sí hace cumplir** las `NetworkPolicy` (comprobado en el paso 8).

## Inventario (paso 1), antes y después

| Pod | Antes | Después |
|---|---|---|
| api | uid 10001, no escribe en `/`, credencial de la API de Kubernetes montada: **sí** | uid 10001, raíz de solo lectura, **sin** credencial |
| web | **uid 0 (root)**, escribe en `/`, credencial: **sí** | uid 101, raíz de solo lectura, **sin** credencial |
| Red | un pod ajeno llega a la API (200) | un pod ajeno no llega (000) a la API ni a la web |

## Lo que dijeron los avisos (paso 2)

`Warning: would violate PodSecurity "restricted:latest": allowPrivilegeEscalation != false ..., unrestricted capabilities ..., runAsNonRoot != true ..., seccompProfile ...`. Son la lista de tareas: `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`, `runAsNonRoot`, `seccompProfile: RuntimeDefault`.

## API con `readOnlyRootFilesystem` (paso 3)

Sin escribir `DIRECTORIO_DATOS`, `/salud/listo` devuelve 503 (la API sigue viva pero no lista; el registro dice por qué). Solución sin desactivar la opción: un `emptyDir` montado en `/datos` (y otro en `/tmp` para la JVM). Con un volumen de datos que desaparece con el pod, es un cambio solo de arranque; los datos duraderos son tema de la sesión 06.

## Web (paso 4)

nginx escribe en tres sitios, descubiertos en los registros y por el error al arrancar: `/etc/nginx/conf.d` (el arranque genera `default.conf` desde la plantilla), `/var/cache/nginx` (temporales del proxy) y `/run` (`nginx.pid`). Cada uno es un `emptyDir`; se ejecuta con el usuario 101 de la imagen.

## Enforce (pasos 5 y 6)

Con `enforce=restricted`, `kubectl apply` de un pod privilegiado se rechaza en el acto: `pods "privilegiado" is forbidden: violates PodSecurity "restricted:latest"`. Un **Deployment** que no cumple sí se aplica; el rechazo aparece en los eventos del ReplicaSet (`FailedCreate`: `Error creating: pods ... is forbidden: violates PodSecurity "restricted:latest"`), y si ya había una versión buena, sus pods siguen sirviendo.

## Red (pasos 7 a 10)

| Regla | Razón |
|---|---|
| `denegar-entrada` (todos los pods) | punto de partida: nada entra |
| `web-a-api`: de `app=bitacora-web` al 8080 de la API | la web hace de proxy de `/api/` |
| `entrada-a-api-y-web`: solo los pods `app.kubernetes.io/name=envoy` de `envoy-gateway-system` al 8080 | el controlador de entrada entrega `/` a la web y `/api` a la API; nadie más |
| `api-sin-salida` (solo `Egress`) | la API no inicia ninguna conexión |
| ...ampliada con DNS (UDP/TCP 53 a `kube-dns`) | la regla más estrecha que permite resolver nombres |

Qué deja de funcionar sin esperarlo al cerrar la salida de la API: **la resolución de nombres** (`nslookup` falla); las peticiones entrantes siguen funcionando porque las respuestas a una conexión entrante no necesitan regla de salida. Con la regla de DNS, la API resuelve pero no puede abrir conexiones a otros pods.

## Permisos (paso 11)

Cuenta `lector-de-configuracion`, `Role` con `get/list/watch` de `configmaps` y `RoleBinding`. `kubectl auth can-i --as=system:serviceaccount:bitacora-08:lector-de-configuracion`: get/list configmaps **yes**; get secrets, delete configmaps, get pods **no**; configmaps de otro espacio **no**. `automountServiceAccountToken: false` en todos los pods; la cuenta de servicio lo tiene también desactivado.
