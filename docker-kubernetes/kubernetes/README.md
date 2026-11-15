# Kubernetes

Diez talleres, en orden. Requisito: la parte de [Docker](../docker/) completa, con las imágenes `bitacora-api:1.0.0` y `bitacora-web:0.1.0` construidas.

| # | Sesión | Taller |
|---|---|---|
| 01 | [Clúster local y primer pod](01-cluster-local-y-primer-pod/) | Levantar el clúster y romper cosas para ver qué pasa |
| 02 | [Deployments y Services](02-deployments-y-services/) | Varias copias, balanceo, actualización y vuelta atrás |
| 03 | [Configuración y secretos](03-configuracion-y-secretos/) | La misma imagen en dos ambientes |
| 04 | [Sondas, recursos y apagado ordenado](04-sondas-recursos-y-apagado/) | Cero errores durante un reinicio, medido |
| 05 | [Tráfico de entrada: Gateway API](05-trafico-de-entrada/) | Nombre de host y despliegue canario |
| 06 | [Almacenamiento y estado](06-almacenamiento-y-estado/) | El defecto de Bitácora, PostgreSQL y un respaldo que se restaura |
| 07 | [Escalado y actualizaciones](07-escalado-y-actualizaciones/) | Pico de carga, despliegue malo y nodo en mantenimiento |
| 08 | [Seguridad del pod y de la red](08-seguridad-del-pod-y-de-la-red/) | Nivel `restricted` y red cerrada por defecto |
| 09 | [Diagnóstico: arreglar un despliegue roto](09-diagnostico/) | Ocho casos averiados |
| 10 | [Kustomize y taller integrador](10-kustomize-y-taller-integrador/) | Dos ambientes, un guion, un simulacro y una defensa |

Todo corre en un clúster local. La versión de Kubernetes vigente al escribir el curso era la 1.37. El estado de verificación del código está en el [índice de la ruta](../README.md#estado-del-código).
