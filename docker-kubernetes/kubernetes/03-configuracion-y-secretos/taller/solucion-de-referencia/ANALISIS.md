# Kubernetes 03: análisis

Los mismos `api.yaml` y `web.yaml` sirven para `bitacora` y `bitacora-pruebas`; solo cambian `ambientes/<ns>/configuracion.yaml` y el Secret (generado con `crear-secreto.sh`, nunca en un archivo).

- **Paso 3.** `kubectl get secret -o jsonpath='{.data.CLAVE_API}' | base64 -d` devuelve la clave en claro. Un Secret está codificado, no cifrado: su seguridad depende de quién puede leerlo (RBAC), de cifrar `etcd` y de no ponerlo en el repositorio.
- **Paso 4.** Variables de entorno: 20 s después de aplicar el ConfigMap, `/api/info` sigue con el valor viejo; hizo falta `rollout restart` (1305 ms). Las variables se leen al arrancar el proceso.
- **Paso 5.** Archivo montado: el cambio se ve dentro del contenedor a los 70 s (el kubelet sincroniza cada minuto más la caché). Es más lento que reiniciar, pero no reinicia nada; la aplicación debe volver a leer el archivo.
- **Paso 6.** Clave inexistente: `Error: couldn't find key MENSAJE_QUE_NO_EXISTE in ConfigMap bitacora/bitacora-api-config`, estado `CreateContainerConfigError`.
- **Paso 8.** ConfigMap inmutable: `field is immutable when `immutable` is set`. Sirve para evitar cambios accidentales y alivia la carga del servidor de la API.
- **Paso 9 (gestión de secretos).** Dos caminos: (a) Sealed Secrets o SOPS: el valor se cifra con una clave que solo tiene el clúster o el equipo y lo cifrado sí va al repositorio; (b) External Secrets Operator: el Secret se sincroniza desde un gestor externo (Vault, AWS Secrets Manager) y el repositorio solo guarda la referencia. Rotación: cambiar en el gestor, que el operador sincronice y reiniciar de forma gradual los pods que lo lean como variable.
