# 09 — Diagnóstico: arreglar un despliegue roto

## Idea

Un buen médico no receta al primer síntoma. Pregunta, mide, descarta, y solo entonces trata. El mal médico prueba medicamentos hasta que algo parece funcionar, y el paciente nunca sabe qué tenía. Diagnosticar un clúster es lo mismo: primero la evidencia, después el cambio.

## Lo esencial

### El método: de fuera hacia dentro

1. **¿Qué ve el usuario?** El síntoma exacto: no carga, error 502, lento, a veces sí y a veces no.
2. **¿Los pods existen y en qué estado están?** `kubectl get pods -o wide`
3. **¿Qué dice Kubernetes?** `kubectl describe pod` y leer los eventos del final.
4. **¿Qué dice la aplicación?** `kubectl logs`, y `--previous` si el contenedor ya se reinició.
5. **¿El Service tiene a quién enviar?** `kubectl get endpointslices`
6. **¿Funciona desde dentro?** Un pod temporal o `kubectl debug`, llamando al Service y luego al pod directamente.
7. **¿Funciona la entrada?** Las condiciones de la ruta y los registros del controlador.

Cada paso descarta una capa. Cuando un paso falla, el problema está ahí o un poco antes; no hace falta seguir.

### Qué significa cada estado

| Lo que ves | Qué suele ser | Dónde mirar |
|---|---|---|
| `Pending` | No hay nodo donde quepa o que cumpla las condiciones; o falta un volumen | Eventos del pod |
| `ImagePullBackOff` / `ErrImagePull` | La imagen no existe, la etiqueta está mal o faltan credenciales | Eventos del pod |
| `CreateContainerConfigError` | Falta un ConfigMap, un Secret o una clave | Eventos del pod |
| `CrashLoopBackOff` | El proceso arranca y muere, una y otra vez | `logs --previous` y el código de salida en `describe` |
| `Running` pero `0/1` listo | La sonda de disponibilidad falla | Eventos y registros |
| `Running` con reinicios que crecen | La sonda de vida falla o se queda sin memoria | `Last State` en `describe` |
| `OOMKilled`, código 137 | Superó el límite de memoria | `Last State` en `describe` |
| Todo `Running` y no responde | Service sin destinos, puerto equivocado, política de red | `endpointslices`, prueba desde dentro |
| `Terminating` eterno | El proceso no atiende la señal, o hay un finalizador pendiente | `describe` |

`CrashLoopBackOff` no es una causa: es Kubernetes diciendo "lo intento de nuevo, cada vez con más espera". La causa está en el registro del intento anterior.

### Herramientas

```bash
kubectl get events --sort-by=.lastTimestamp          # todo lo que pasó, en orden
kubectl logs deploy/pedidos --previous               # el intento que murió
kubectl get pod <pod> -o jsonpath='{.status.containerStatuses[0].lastState}'
kubectl run prueba --rm -it --image=curlimages/curl --restart=Never -- sh
kubectl debug -it <pod> --image=busybox:1.37 --target=<contenedor>   # un contenedor de herramientas pegado al pod
kubectl auth can-i list pods --as=system:serviceaccount:tienda:pedidos
```

## Ejemplo

Síntoma: tras un despliegue, `pedidos` no responde.

### Antes

```bash
kubectl delete pod --all                 # "a ver si reiniciando…"
kubectl rollout restart deploy/pedidos   # "otra vez"
kubectl edit deploy/pedidos              # se sube la memoria, por si acaso
```

### Análisis

Ninguna orden mira nada. Se cambian tres cosas sin saber cuál es el problema, se destruye la evidencia (los registros del intento anterior) y, si por casualidad vuelve a funcionar, nadie sabe por qué ni cuándo se repetirá.

### Después

```bash
kubectl get pods -l app=pedidos
#   pedidos-7c9f…   0/1   ImagePullBackOff   0   4m

kubectl describe pod -l app=pedidos | tail -n 8
#   Failed to pull image "registro.interno/pedidos:2.4.0": … 401 Unauthorized
```

Dos órdenes. La imagen existe, pero el nodo no tiene credenciales para ese registro. El arreglo es un `imagePullSecrets` (o la credencial del nodo), no reiniciar. Y queda una pregunta mejor que "¿por qué falla?": ¿por qué la versión anterior sí se descargaba? Quizá venía de otro registro, o la credencial venció. Esa respuesta es la que evita la repetición.

## Errores comunes

- Reiniciar antes de mirar.
- Leer `logs` cuando el contenedor nunca llegó a arrancar: no hay registros; la respuesta está en los eventos.
- Olvidar `--previous` y leer el registro, vacío, del intento que acaba de empezar.
- Cambiar varias cosas a la vez.
- Arreglar con `kubectl edit` y no llevar el cambio al repositorio: el siguiente despliegue lo deshace.
- Dar por resuelto algo que "ya funciona" sin haber entendido la causa.

## Taller

**Objetivo:** diagnosticar y arreglar ocho despliegues rotos, con evidencia.

Archivos de inicio: [`taller/inicio/averiado/`](taller/inicio/averiado/). Son un espacio de nombres y ocho casos. Cada caso tiene **al menos un fallo**; alguno tiene dos. Todos usan tu imagen `bitacora-api:1.0.0`, que debe estar cargada en el clúster.

```bash
kubectl apply -f taller/inicio/averiado/
```

Para cada caso:

1. Anota el síntoma: qué estado muestran los pods y qué pasa al llamar al Service desde dentro del clúster.
2. Sigue el método. Anota cada orden que te acercó a la causa y la línea exacta que la delata.
3. Escribe la causa en una frase.
4. Corrige el manifiesto con el cambio más pequeño posible y demuestra que funciona: los pods están listos y `/api/info` responde 200 por el Service.
5. Di cómo se habría evitado: una validación, una prueba, una convención.

Reglas:

- No se vale leer el manifiesto antes de mirar el clúster. En producción casi nunca sabrás qué línea cambió. Diagnostica primero con `kubectl` y abre el archivo solo para corregir.
- No se vale borrar y volver a crear con un manifiesto tuyo.
- Un caso no está terminado hasta que el Service responde.

Al final, responde: ¿qué casos habría detectado una validación antes de aplicar, y cuáles solo se ven en ejecución?

**Criterios de aceptación**

- Los ocho casos responden 200 por su Service.
- Cada caso tiene síntoma, evidencia, causa, arreglo y prevención.
- Los arreglos están en los archivos, no hechos con `kubectl edit`.
- En los casos con dos fallos encontraste los dos.

**Entrega:** rama `taller/k8s-09`, con los manifiestos corregidos en `taller/solucion/averiado/` y `taller/solucion/ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

Los ocho casos se ejecutaron en un clúster kind (Kubernetes v1.37.0): todos fallan de la forma descrita y todos terminan respondiendo 200 una vez corregidos. Si alguno no falla o falla de una forma que no tiene sentido en tu clúster, cuéntalo en tu entrega: eso también es un hallazgo.

## En el trabajo real

Esta es la habilidad que más se nota. Cuando producción falla, quien sigue un método y deja escrito qué vio resuelve más rápido y, sobre todo, deja al equipo sabiendo qué pasó. Los informes posteriores a un incidente se escriben con la misma estructura de este taller: síntoma, evidencia, causa, arreglo, prevención.

## Preguntas senior

1. Un pod está en `CrashLoopBackOff`. Describe paso a paso lo que haces.
2. ¿Por qué `kubectl logs` a veces no muestra nada útil y qué miras entonces?
3. Todos los pods están `Running` y listos, pero el servicio no responde. ¿Qué capas quedan por revisar?
4. ¿Cómo diagnosticas un contenedor que no trae intérprete de órdenes?
5. Un fallo ocurre una vez cada mil peticiones. ¿Cómo lo abordas?
6. ¿Qué diferencia hay entre arreglar un incidente y resolver su causa?
