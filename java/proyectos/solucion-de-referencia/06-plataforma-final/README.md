# Proyecto 6: plataforma desplegada y defendida, solución de referencia

> **Intenta el proyecto antes de leer esto.** Ver la solución primero convierte el proyecto en lectura y no en práctica. Es *una* solución mínima y verificada, no *la* solución: la revisión de tu entrega mira tu diseño, tus decisiones y tu defensa.

Enunciado: [sección 6 de proyectos/README.md](../../README.md#6-proyecto-final-plataforma-desplegada-y-defendida). Lleva la tienda del [proyecto 5](../05-tienda-monolito-modular/README.md) (su código está copiado aquí para que esta carpeta sea autónoma) a un estado de producción: imagen, pipeline, clúster, infraestructura como código, observabilidad, alertas y carga.

## Qué requisitos cubre y dónde

| Requisito del enunciado | Dónde está | Cómo se comprueba |
|---|---|---|
| Imagen de contenedor optimizada | `Dockerfile`: construcción en capas (dependencias aparte, se reutilizan entre construcciones), JRE sin JDK, usuario sin privilegios, `-XX:MaxRAMPercentage=75` | `docker build` y `docker run --rm --entrypoint id tienda:p6 -u app` (no es 0) |
| Configuración externa | `ConfigMap` en `k8s/tienda.yaml`; la aplicación lee variables de entorno (`application.yaml`) | `kubectl get configmap tienda-config -o yaml` |
| Secretos fuera del repositorio | el Deployment solo referencia el secreto `tienda-secretos`, que se crea con `kubectl` | el README y `k8s/tienda.yaml`: no hay ninguna clave en el repositorio |
| Pipeline de integración y entrega continuas con pruebas, análisis y despliegue | `ci/ci.yml`: pruebas (incluye arquitectura), análisis de dependencias con Trivy, imagen, análisis de la imagen y despliegue con `kubectl set image` | `actionlint ci/ci.yml` |
| Desplegado en un clúster local con sondas, recursos y apagado ordenado | `k8s/tienda.yaml` (2 réplicas, sondas `startup`, `liveness` y `readiness`, `requests`/`limits`, `preStop`, `terminationGracePeriodSeconds`) y `server.shutdown: graceful` | `kubectl rollout status`; un reinicio gradual con carga no pierde peticiones |
| Infraestructura como código | `iac/main.tf` (OpenTofu o Terraform aplican los manifiestos al clúster) | `tofu apply` y luego `tofu plan -detailed-exitcode` sin cambios |
| Métricas | `/actuator/prometheus`: peticiones HTTP, cortacircuitos de Resilience4j; Prometheus las raspa | consulta en Prometheus (puerto 9090) |
| Registros estructurados | JSON en formato ECS con `traceId` (`LOGGING_STRUCTURED_FORMAT_CONSOLE`) | `kubectl logs -l app=tienda` |
| Trazas | OpenTelemetry por OTLP hacia Tempo | busca la traza de `POST /pedidos` en Grafana, o con el `traceId` de un registro |
| Un tablero | `grafana/tablero-tienda.json` | `grafana/importar_y_comprobar_tablero.py` lo importa y comprueba que cada panel devuelve datos |
| Dos alertas con su procedimiento | `alertas/alertas-tienda.yml` (`TiendaErroresAltos` y `TiendaPasarelaCortada`; el procedimiento va en la anotación `runbook`) | `promtool check rules` y `promtool test rules alertas/alertas_test.yml` |
| Una prueba de carga | `carga/CargaTienda.java` | ver abajo |

**Qué no incluye, a propósito:**

- **El documento de diseño con estimaciones y la defensa de quince minutos.** Son tu entrega y no se pueden dar hechos. Las decisiones de esta solución están en [DECISIONES.md](DECISIONES.md) como ejemplo de lo que defender.
- **Los resultados de la prueba de carga y el cuello de botella.** Depende de tu equipo: ejecútala, mídela y encuéntralo tú. Una pista: compara una carga contra un solo producto y otra repartida entre muchos, y piensa qué fila disputan los pedidos.
- Despliegue en la nube (se usa kind, un clúster local), análisis estático del código fuente (Trivy analiza dependencias, imagen y secretos), registros enviados a Loki (se leen de la salida del pod) y trazas con más de un tramo por petición (no hay instrumentación de JDBC).
- El último paso del pipeline (desplegar en un clúster remoto con el secreto `KUBECONFIG`) no se ejecutó en los servidores de GitHub.

## Requisitos

JDK 21, Maven, Docker en marcha, [kind](https://kind.sigs.k8s.io), `kubectl` y [OpenTofu](https://opentofu.org) (`tofu`) o Terraform. Opcionales: `trivy` y `actionlint`.

## Cómo probarla

```bash
mvn clean verify
```

Levanta PostgreSQL y Kafka propios con Testcontainers (no necesita `docker compose`) y ejecuta las 17 pruebas de la tienda. Deja `target/tienda.jar`.

## Cómo levantarla

1. Infraestructura local: PostgreSQL (5432), Kafka (9092 para tu equipo y 9094 para los pods) y la pila de observabilidad (Grafana 3000 con usuario `admin` y clave `admin`, Prometheus 9090, Tempo 3200, colector OTLP 4318):

```bash
docker compose up -d --wait
```

2. La imagen (usa el jar de `mvn verify`):

```bash
docker build -t tienda:p6 .
```

3. El clúster, con el puerto 8080 de tu equipo apuntando a la tienda:

```bash
kind create cluster --name tienda --config k8s/kind.yaml --wait 120s
kind load docker-image tienda:p6 --name tienda
kubectl create secret generic tienda-secretos --from-literal=db-clave=tienda
```

4. Despliega con infraestructura como código:

```bash
cd iac
tofu init
tofu apply -var kubeconfig=$HOME/.kube/config
cd ..
kubectl rollout status deployment/tienda --timeout=240s
```

Si prefieres no usar OpenTofu: `kubectl apply -f k8s/tienda.yaml`.

5. Úsala:

```bash
curl localhost:8080/actuator/health/readiness
curl -X POST localhost:8080/productos -H 'Content-Type: application/json' -d '{"sku":"HOLA","nombre":"Cuaderno","precio":10,"stock":50}'
curl -X POST localhost:8080/pedidos -H 'Content-Type: application/json' -d '{"cliente":"ana","sku":"HOLA","cantidad":1}'
kubectl logs -l app=tienda --tail=-1 | grep '"traceId"' | tail -3   # registros JSON con traceId
```

(Los endpoints de la tienda son los del [proyecto 5](../05-tienda-monolito-modular/README.md).)

## Observabilidad, alertas y carga

```bash
# Prueba de carga: <url> <hilos> <pedidos> <productos distintos>
java carga/CargaTienda.java http://localhost:8080 16 2000 1
java carga/CargaTienda.java http://localhost:8080 16 2000 50

# Tablero: lo importa en Grafana y comprueba cada panel (genera tráfico antes: los paneles necesitan datos recientes)
python3 grafana/importar_y_comprobar_tablero.py http://localhost:3000 grafana/tablero-tienda.json

# Alertas: sintaxis y pruebas con series sintéticas (promtool viene dentro de la imagen de observabilidad)
docker compose exec observabilidad /otel-lgtm/prometheus/promtool check rules /otel-lgtm/alertas-tienda.yml
docker compose cp alertas/. observabilidad:/tmp/alertas
docker compose exec -w /tmp/alertas observabilidad /otel-lgtm/prometheus/promtool test rules alertas_test.yml

# Pipeline
actionlint ci/ci.yml
```

Para usar el pipeline, copia `ci/ci.yml` a `.github/workflows/tienda.yml` en la raíz de tu repositorio. Las trazas están en Grafana (http://localhost:3000, Explore, Tempo) y las métricas en Prometheus (http://localhost:9090).

## Apagarlo todo

```bash
kind delete cluster --name tienda
docker compose down -v
```

## Decisiones de diseño

Están en [DECISIONES.md](DECISIONES.md).
