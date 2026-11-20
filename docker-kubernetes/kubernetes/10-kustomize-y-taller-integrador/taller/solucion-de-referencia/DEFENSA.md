# Bitácora en Kubernetes: documento de defensa

Para quien va a operar esto sin conocerme. Kubernetes v1.37.0 (kind), Envoy Gateway v1.9.3, metrics-server v0.9.1.

## 1. Qué hay desplegado y cómo viaja una petición

```
navegador ──http://bitacora.localhost:50080──► puerto 50080 del equipo
   └─► nodo de trabajo "entrada=si" ─ NodePort 30080 (en todos los nodos) ─► proxy Envoy (2 pods en nodos distintos, espacio envoy-gateway-system)
         ├─ /api/...  ──► Service bitacora-api (ClusterIP :80) ──► 3 a 6 pods api (:8080)
         └─ /...      ──► Service bitacora-web (ClusterIP :80) ──► 2 pods web (nginx :8080) ──proxy /api/──► Service bitacora-api
```

Objetos de cada ambiente (`superposiciones/<ambiente>`): `Namespace` (producción exige `restricted`), `Gateway` + `HTTPRoute`, dos `Deployment` y dos `Service`, un `ConfigMap` generado (el nombre lleva un resumen), un `Secret` creado a mano con `guion/crear-secreto.sh`, y, solo en producción, `HorizontalPodAutoscaler` (3 a 6), dos `PodDisruptionBudget` y `NetworkPolicy` (entrada cerrada; abierta solo de la web a la API y del proxy de Envoy a ambas).

Cómo operarlo: `guion/construir.sh produccion` reconstruye todo desde cero; `kubectl apply -k superposiciones/produccion` aplica cambios; `guion/validar.sh` valida sin clúster (kubeconform + kube-linter).

## 2. Las tres decisiones más discutibles

1. **El estado: se acepta que `/api/notas` es inconsistente.** La API guarda las notas en un archivo del pod (`emptyDir`) y producción exige tres copias o más. Con tres copias, cada una tiene su lista (tras guardar 5 notas, 30 lecturas por la entrada dieron tres listas distintas, una de ellas vacía: la prueba cuenta las listas distintas por su contenido, no por su tamaño, y exige al menos 2 y las cifras están en `salidas/k8s-10.txt`) y las notas se pierden al reiniciar. Alternativas descartadas: una sola copia con volumen (incumple el requisito de tres copias y de disponibilidad) y un StatefulSet con un volumen por copia (no resuelve la inconsistencia: no replica datos). **La solución correcta exige cambiar el código** para guardar en PostgreSQL, y no la he tomado porque el enunciado trabaja con Bitácora tal cual. Está declarado aquí para que nadie crea que las notas son fiables.
2. **Dos copias del proxy con `externalTrafficPolicy: Cluster`.** El puerto del equipo (50080) está mapeado a un solo nodo de trabajo (`entrada=si`), una limitación de kind: en una nube sería un balanceador. Con `Cluster` el `NodePort` responde en cualquier nodo y reenvía al proxy donde esté (Envoy Gateway usa `Local` por defecto, y con `Local` solo responden los nodos que alojan un proxy). Así el proxy no tiene que estar en el nodo del mapeo: hay dos copias en nodos distintos (`topologySpreadConstraints`; una anti-afinidad obligatoria dejaba sin sitio al proxy del Gateway de desarrollo). Resultado: vaciar cualquier nodo, también el de la entrada, da 0 errores, **siempre que se cumplan dos condiciones**:
   - **Restaurar el reparto después de cada mantenimiento.** `topologySpreadConstraints` solo actúa al programar: tras un `drain`, proxies y controladores de Envoy Gateway quedan en el mismo nodo y `uncordon` no los recoloca. La prueba expulsa una copia de cada uno y afirma el reparto antes de cada `drain` y del `docker stop`; en un clúster real lo haría un descheduler.
   - **`preStop` de la API de 15 s.** Con 5 s, los proxies seguían enviando peticiones a una API que ya se apagaba (503 con cuerpo `{"error":"el servicio no está listo"}`). Con 15 s no hubo errores. Dónde se pierde ese tiempo (controlador o proxy) no se aisló.
   - Las cifras de los experimentos están en `ANALISIS.md` y `salidas/k8s-10.txt`.
   - **Coste de `Cluster`:** un salto de red extra y se pierde la IP de origen del cliente (SNAT). Alternativa descartada: un balanceador delante (en kind no existe).
   - **Riesgo que queda:** el mapeo del puerto sigue en un solo nodo; ante una **caída** (no ante un vaciado) el puerto del equipo deja de responder. Al detener de golpe un nodo de trabajo sin el mapeo hay entre 10 y 12 errores durante unos 49 s (40 s de `node-monitor-grace-period` más `kube-proxy`) y terminan solos.
3. **Autoescalado por CPU con un objetivo del 50 % y bajada en 60 s.** Reacciona en menos de dos minutos a un pico y devuelve capacidad rápido. Alternativas descartadas: escalar por peticiones por segundo (KEDA: más piezas) y una ventana de bajada de 5 minutos (más estable, más caro). El riesgo es el aleteo ante carga a oleadas.

Otras elecciones: `ScheduleAnyway` en el reparto entre nodos (prefiere repartir, no impide arrancar), metrics-server con `--kubelet-insecure-tls` (solo válido en local).

## 3. Qué no resuelve este despliegue

- **Pérdida del clúster entero:** se pierde todo lo que no esté en el repositorio. Lo que sí se recupera con `construir.sh` en el tiempo medido en `salidas/k8s-10.txt` (reconstrucción desde cero). **No se recuperan** las notas ni el Secret (se regenera con otra clave: los clientes que usaran la anterior fallan).
- **Datos:** no hay base de datos, ni volúmenes persistentes, ni respaldo. Un reinicio borra las notas.
- **Sin monitorización ni alertas:** nadie mira las métricas, ni los registros se centralizan, ni hay trazas. Los fallos se ven con `kubectl`.
- **Controlador de Envoy Gateway:** `guion/construir.sh` lo instala con 2 réplicas repartidas por nodo y un PodDisruptionBudget (`valores-envoy-gateway.yaml`). Con 1 réplica, o con las 2 en el mismo nodo, vaciar ese nodo producía errores 503 (reconfiguración del proxy sin controlador). Con 2 en nodos distintos y el `preStop` de la API en 15 s, 0 errores en los vaciados de la prueba.
- **El reparto de la entrada no se restaura solo:** tras un `drain` los proxies y los controladores quedan juntos en un nodo y `uncordon` no los recoloca. Hay que expulsar una copia de cada uno (la prueba lo hace) o, en un clúster real, usar un descheduler o reiniciar los Deployment; si no, el siguiente mantenimiento sí produce corte.
- **Nodo caído de golpe:** Kubernetes tarda más de 30 s en reaccionar (ver tabla del simulacro: 10 a 12 errores en unos 49 s): hasta entonces `kube-proxy` sigue enviando parte del tráfico a las copias muertas. El nodo que tiene el mapeo del puerto del equipo es un punto único ante una caída (en una nube lo sustituye el balanceador).
- **Secretos:** el Secret está en `etcd` en base64, sin cifrar en reposo; el gestor externo no está implementado (ver análisis del taller 03).
- **Red:** TLS no está configurado (HTTP plano); las políticas no limitan la salida (solo la entrada; se podría añadir una política de salida de la API con la regla de DNS de la sesión 08); `kindnet` hace cumplir las políticas, otros complementos podrían no hacerlo.
- **Cadena de suministro:** las imágenes se cargan locales con `kind load`; no hay registro, ni análisis de vulnerabilidades en el flujo (ver Docker 06).

## 4. Qué haría distinto en una nube real y qué costaría

- Un `LoadBalancer` gestionado y un Gateway con varias réplicas del proxy en zonas distintas: se paga el balanceador por hora (unas decenas de dólares al mes) y desaparece el punto único.
- Base de datos administrada (PostgreSQL) con respaldos automáticos y la API sin estado: coste mensual de la instancia, a cambio de eliminar el problema 1.
- TLS con certificados automáticos (cert-manager) en el Gateway.
- Un gestor de secretos externo (External Secrets) en lugar de crear el Secret a mano.
- Autoescalado de nodos (Cluster Autoscaler o Karpenter): si el HPA pide 6 copias y no caben, hace falta añadir máquinas, y se paga por ellas.
- Entrega continua (Argo CD) para que nadie ejecute `kubectl apply` a mano; monitorización con Prometheus y alertas.
