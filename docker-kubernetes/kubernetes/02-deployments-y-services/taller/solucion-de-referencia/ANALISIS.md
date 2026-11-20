# Kubernetes 02: análisis

Las mediciones salen de `probar.sh`; las cifras varían un poco de una ejecución a otra.

- Balanceo: 20 llamadas por el Service desde un pod llegan a las 3 copias; por `port-forward`, siempre al mismo pod (el túnel se fija a un pod elegido al abrirse). Eso no es el Service.
- Autorreparación: al borrar un pod, vuelve a haber tres listos en 1171 ms; al borrar dos, 1174 ms. Los pods tienen como dueño un ReplicaSet y este, un Deployment.
- Selector roto (`bitacora-apii`): `kubectl apply` no da error; el Service se queda sin EndpointSlices y la web devuelve 502.
- Las listas de notas cambian entre recargas porque cada copia escribe en su propio disco (6, 3 y 6 respuestas con listas de 1, 1 y 2 notas).
- Actualización 1.0.1: dos ReplicaSet, el nuevo con 3 y el viejo con 0.
- Etiqueta inexistente: los tres pods viejos siguen atendiendo (0 errores de 236 peticiones); `rollout undo` vuelve atrás.
- `kubectl scale` a 5 y luego `kubectl apply` del archivo: manda el archivo (vuelve a 3).
- Otro espacio de nombres: `bitacora-api` no se resuelve; sí `bitacora-api.bitacora` y el nombre completo.
