# Kubernetes 09: análisis

Los ocho casos se aplicaron con `kubectl apply -f taller/inicio/averiado/`, se diagnosticaron **sin abrir los manifiestos** y se corrigieron en archivos (`averiado/`, en esta carpeta), sin `kubectl edit` ni borrar y recrear. Evidencia automatizada en `probar.sh`.

| Caso | Síntoma | Evidencia que la delata | Causa | Arreglo (mínimo) | Cómo se habría evitado |
|---|---|---|---|---|---|
| 1 | `ImagePullBackOff` | `kubectl get events`: `Failed to pull image "bitacora-api:1.0.O": ... pull access denied` | La etiqueta tiene la letra O en lugar del cero | `1.0.O` por `1.0.0` | Fijar la imagen por resumen o con una variable; una prueba de humo que despliegue en un clúster efímero |
| 2 | `CrashLoopBackOff` | `kubectl logs --previous`: `NumberFormatException: For input string: "8080 "` | `PUERTO` tiene un espacio al final | `"8080"` | Validar la configuración al arrancar con un mensaje claro; linter de YAML |
| 3 | `Running` pero `0/1` listo | `kubectl logs`: `ERROR no puedo escribir en /var/lib/bitacora: ... Read-only file system`; `/salud/listo` 503 | `readOnlyRootFilesystem: true` y `DIRECTORIO_DATOS` apunta a una ruta sin volumen | Un `emptyDir` en `/var/lib/bitacora` | Prueba con `securityContext` real en CI |
| 4 | Pods listos, Service sin destinos (`000`) | `kubectl get endpointslices`: sin endpoints; el selector del Service es `app=caso4` | **Dos fallos**: (a) selector `app: caso4` en vez de `caso-4`; (b) tras corregirlo, `targetPort: 80` en vez de `http` (8080) | `app: caso-4` y `targetPort: http` | `kubectl get endpoints` en la lista de comprobación; usar puertos con nombre |
| 5 | `Pending` | evento `FailedScheduling`: `2 node(s) didn't match Pod's node affinity/selector` | `nodeSelector: kubernetes.io/arch: s390x` | Quitar el `nodeSelector` (una etiqueta de arquitectura fija no es portable entre Apple Silicon y servidores amd64) | Revisar `nodeSelector` en revisión de código |
| 6 | Reinicios sin parar | `kubectl logs --previous`: `GET /salud/viva 404` seguido de `apagado solicitado`; salida 143; la sonda de vida apunta a `/salud/viva` | Ruta de la sonda con una letra cambiada: `viva` por `vivo` | `/salud/vivo` | Que la sonda use la misma constante que la ruta; prueba que consulte `describe` |
| 7 | `CreateContainerConfigError` | evento: `couldn't find key MENSAGE in ConfigMap averiado/caso-7` | Clave con errata en `configMapKeyRef` | `MENSAJE` | `kubeconform` no lo detecta (es valor); se detecta con una prueba en clúster o con un generador de ConfigMap con nombres compartidos |
| 8 | `CrashLoopBackOff` | `lastState.terminated`: `OOMKilled`, código 137; límite de 12Mi | Límite de memoria muy bajo para una JVM | 128Mi | Medir el consumo real; revisión de recursos |

## ¿Qué habría detectado una validación antes de aplicar?

Las dos herramientas se ejecutan sobre los ocho casos en `probar.sh` (con afirmaciones) y la prueba también comprueba el caso 4 fallo por fallo:

- `kubeconform -strict` (esquemas de Kubernetes v1.37): 18 recursos, **18 válidos**. Ninguno de los fallos es un error de esquema.
- `kube-linter lint`: solo el **caso 4** se señala por el fallo que lo rompe: `dangling-service` (el selector del Service no coincide con ningún Deployment). En el resto solo salen avisos generales (`no-read-only-root-fs`, `run-as-non-root`, `unset-cpu-requirements`, `unset-memory-requirements`, `no-anti-affinity`), ninguno relacionado con la causa. El caso 8 queda sin señal a pesar del límite de 12Mi: no existe una regla que sepa cuánta memoria necesita una JVM.
- **Solo se ven en ejecución:** 1 (etiqueta), 2 (valor), 3 (volumen), 5 (afinidad), 6 (ruta de la sonda), 7 (clave) y 8 (límite). El `targetPort: 80` del caso 4 tampoco lo detecta ninguna de las dos.

Todos los casos terminan con 2 copias listas y `/api/info` respondiendo 200 por su Service.
