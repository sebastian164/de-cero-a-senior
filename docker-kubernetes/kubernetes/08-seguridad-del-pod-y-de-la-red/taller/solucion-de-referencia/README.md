# Kubernetes 08: seguridad del pod y de la red (solución de referencia)

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Pod Security `restricted`, endurecimiento de la API y la web, `NetworkPolicy` (kindnet las hace cumplir), RBAC y cuenta de servicio.

## Qué instalar

Docker (Docker Desktop o Docker Engine), [kind](https://kind.sigs.k8s.io), [kubectl](https://kubernetes.io/docs/tasks/tools/), `curl` y `python3`. Además: [Helm](https://helm.sh) (instala Envoy Gateway). Necesita acceso a internet.

## El clúster

`probar.sh` crea un clúster kind llamado `referencia` (un plano de control y dos nodos de trabajo, según [`kind.yaml`](kind.yaml), el mismo de la sesión 01 más el puerto de entrada), construye las imágenes de Bitácora con los Dockerfile de las soluciones de las sesiones [02](../../../docker/02-tu-primera-imagen/taller/solucion-de-referencia/) y [03](../../../docker/03-imagenes-eficientes-y-seguras/taller/solucion-de-referencia/) y el código de [`docker-kubernetes/app`](../../../app/), y las carga en los nodos. El clúster se llama distinto de tu clúster `curso` para no tocarlo, y usa un archivo de configuración de kubectl propio (no modifica tu `~/.kube/config`).

Para trabajar a mano con estos archivos en tu clúster `curso`: `kind create cluster --config kind.yaml --name curso`, carga tus imágenes como en la sesión 01 y aplica los manifiestos con `kubectl apply -f`.

## Ejecutarla y comprobarla

```bash
./probar.sh
```

Dura unos 12 minutos. Termina con código 0 solo si todas las comprobaciones pasan; cada paso imprime `OK` o `FALLA` con lo esperado y lo obtenido.

- `CLUSTER=otro ./probar.sh` cambia el nombre del clúster y `PUERTO_ENTRADA=...` el puerto del equipo para la entrada (50080 por defecto).
- `MANTENER=1 ./probar.sh` no baja el clúster al terminar.

## Bajarlo todo

`probar.sh` baja el clúster (`kind delete cluster`), borra la configuración propia de kubectl y las imágenes al terminar (salvo con `MANTENER=1`). Si lo interrumpes a la mitad:

```bash
./bajar.sh
```

## Más

[`ANALISIS.md`](ANALISIS.md): las órdenes, las salidas y la explicación de cada paso.
