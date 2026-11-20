# Kubernetes 10: Kustomize y taller integrador (solución de referencia)

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Reconstruye todo desde cero con `guion/construir.sh` (clúster, metrics-server, Envoy Gateway, imágenes y producción), valida las superposiciones, comprueba los requisitos y ejecuta el simulacro de fallos con un bucle de medición. Esta sesión crea su propio clúster `referencia` (ver `guion/`) y borra el que haya con ese nombre. La defensa del diseño está en [`DEFENSA.md`](DEFENSA.md).

## Qué instalar

Docker (Docker Desktop o Docker Engine), [kind](https://kind.sigs.k8s.io), [kubectl](https://kubernetes.io/docs/tasks/tools/), `curl` y `python3`. Además: [Helm](https://helm.sh), [kubeconform](https://github.com/yannh/kubeconform) y [kube-linter](https://github.com/stackrox/kube-linter). Necesita acceso a internet.

## El clúster

`guion/construir.sh` crea el clúster con los nombres y puertos de la prueba (`referencia`, puerto 50080 del equipo; `CLUSTER=...` y `PUERTO_ENTRADA=...` los cambian). No usa `kind.yaml` ni `envoy.yaml` de esta carpeta: lleva los suyos en `guion/`.

## Ejecutarla y comprobarla

```bash
./probar.sh
```

Dura unos 30 minutos. Termina con código 0 solo si todas las comprobaciones pasan; cada paso imprime `OK` o `FALLA` con lo esperado y lo obtenido.

- `CLUSTER=otro ./probar.sh` cambia el nombre del clúster y `PUERTO_ENTRADA=...` el puerto del equipo para la entrada (50080 por defecto).
- `MANTENER=1 ./probar.sh` no baja el clúster al terminar.

## Bajarlo todo

`probar.sh` baja el clúster (`kind delete cluster`), borra la configuración propia de kubectl y las imágenes al terminar (salvo con `MANTENER=1`). Si lo interrumpes a la mitad:

```bash
./bajar.sh
```

## Más

[`ANALISIS.md`](ANALISIS.md): las órdenes, las salidas y la explicación de cada paso.
