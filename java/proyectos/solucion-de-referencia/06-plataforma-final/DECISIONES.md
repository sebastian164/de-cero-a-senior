# Decisiones de diseño: plataforma

**La tienda es la del proyecto 5, sin cambios de lógica.** El proyecto 6 no es una aplicación nueva: es lo que la rodea. La aplicación ya trae lo operativo en su configuración (sondas de salud, apagado ordenado, métricas, trazas y registros JSON); aquí se empaqueta, se despliega y se observa.

**Imagen en capas con el modo `jarmode=tools` de Spring Boot.** Las dependencias casi no cambian y pesan la mayor parte; en su propia capa se reutilizan entre construcciones y el despliegue de un cambio de código sube solo los kilobytes de la capa de la aplicación. Se usa un JRE (sin compilador) y un usuario sin privilegios: menos superficie de ataque.

**Configuración por variables de entorno, secreto por referencia.** El mismo jar corre en tu equipo y en el clúster; solo cambian las variables (`ConfigMap`). La clave de la base de datos nunca está en un archivo del repositorio: el Deployment la lee de un `Secret` que se crea aparte. Limitación: un `Secret` de Kubernetes es solo base64; en un entorno real se alimentaría desde un gestor de secretos.

**Tres sondas distintas.** `startup` da margen al arranque lento sin que `liveness` mate el pod; `liveness` solo detecta bloqueos del proceso; `readiness` saca al pod del tráfico si la aplicación no puede atender (la base caída, por ejemplo). Separarlas evita reinicios en cascada cuando falla una dependencia.

**Apagado ordenado en dos mitades.** El `preStop` de 5 segundos deja que el Service saque al pod de su lista de destinos antes de que empiece el apagado; luego Spring termina las peticiones en curso (`server.shutdown: graceful`, 20 s) y el `terminationGracePeriodSeconds` de 40 s lo cubre todo. Con `maxUnavailable: 0` y `maxSurge: 1`, un reinicio gradual nunca baja de las réplicas deseadas. Un reinicio con carga en curso no pierde peticiones.

**OpenTofu aplica los mismos manifiestos de `k8s/`.** Una sola fuente de verdad: lo que se prueba con `kubectl apply` es lo que aplica el código de infraestructura, y `tofu plan` sin cambios demuestra que el clúster coincide con el código. Se usa el proveedor de Kubernetes sobre kind; no se probó un proveedor de nube.

**Alertas por síntomas, no por causas.** `TiendaErroresAltos` (más del 2 % de respuestas 5xx durante 2 minutos) es lo que ve el usuario; `TiendaPasarelaCortada` (cortacircuitos abierto durante 1 minuto) es el síntoma específico más probable de nuestro riesgo mayor, la pasarela. Cada una trae su procedimiento (`runbook`) con el primer paso de mitigación antes que el diagnóstico. Las reglas se prueban con series sintéticas que tienen los mismos nombres y etiquetas que las métricas reales.

**El pipeline falla con una vulnerabilidad crítica, no con cualquiera.** Un umbral en `CRITICAL` mantiene el pipeline útil; si falla por todo, el equipo aprende a ignorarlo. Quedan avisos de severidad alta en dependencias que el análisis informa sin bloquear: revísalos con `trivy fs --severity HIGH,CRITICAL pom.xml`.

**Tomcat fijado a 11.0.25.** El Tomcat que trae Spring Boot 4.1.1 (11.0.24) tiene vulnerabilidades críticas que el análisis detecta; subir la versión en el `pom.xml` las corrige sin cambiar de Spring Boot.

**La prueba de carga es un cliente de Java de un solo archivo, sin herramientas externas.** Da p50, p95, p99, errores y pedidos por segundo, suficiente para comparar dos configuraciones. Para una carga sostenida o con rampas, una herramienta como k6 o Gatling sería el siguiente paso.
