# Proyecto 5: tienda como monolito modular con eventos, solución de referencia

> **Intenta el proyecto antes de leer esto.** Ver la solución primero convierte el proyecto en lectura y no en práctica. Es *una* solución mínima y verificada, no *la* solución: la revisión de tu entrega mira tu diseño, tus decisiones y tu defensa.

Enunciado: [sección 5 de proyectos/README.md](../../README.md#5-tienda-como-monolito-modular-con-eventos). Spring Boot 4.1.1, Spring Modulith, ArchUnit, PostgreSQL 18.7, Kafka 4.3.1, Flyway, Resilience4j.

## Qué requisitos cubre y dónde

| Requisito del enunciado | Dónde está | Prueba que lo demuestra |
|---|---|---|
| Módulos catálogo, inventario, pedidos, pagos y notificaciones | un paquete por módulo bajo `src/main/java/tienda/`, más `eventos` (el núcleo compartido del Outbox) | `spring_modulith_verifica_la_estructura_de_modulos` |
| Cada módulo es dueño de sus tablas y expone una API pública | las tablas llevan el prefijo del módulo en `db/migration/V1__tienda.sql`; la API pública son las clases `Catalogo`, `Inventario`, `Pedidos`, `Pagos`, `Notificaciones`; lo interno está en paquetes `interno` | `ningun_modulo_toca_tablas_de_otro`, `nadie_usa_el_interior_de_otro_modulo` |
| Sin dependencias cíclicas | Spring Modulith | `spring_modulith_verifica_la_estructura_de_modulos` |
| Confirmar un pedido reserva inventario, cobra y notifica | `Pedidos.confirmar` (saga orquestada) | `flujo_feliz_reserva_cobra_confirma_y_notifica_una_vez` |
| Si el cobro falla, el inventario se libera | `Pedidos.confirmar` compensa en orden inverso | `si_el_cobro_falla_se_libera_el_inventario_tras_reintentar`, `un_fallo_en_cualquier_paso_deja_todo_consistente` (inyecta el fallo en cada paso con `Fallos`) |
| Publicación confiable (Outbox) | `Outbox` escribe el evento en la misma transacción; `RelevoOutbox` lo publica en Kafka | `flujo_feliz...` (un solo evento) |
| Consumidores idempotentes | `Notificaciones.alRecibir` (tabla `notificaciones_procesadas`) | `el_consumidor_es_idempotente_ante_entregas_duplicadas` |
| Migraciones con Flyway | `src/main/resources/db/migration` | todas las pruebas arrancan sobre ellas |
| Índices justificados con planes de ejecución | `pedidos_por_cliente` y `outbox_pendientes` | `el_indice_de_pedidos_por_cliente_cambia_el_plan` (de Seq Scan + Sort a Index Scan) |
| Resiliencia: tiempo límite, reintentos y cortacircuitos | `Pagos.cobrar` (`@Retry`, `@CircuitBreaker`, 300 ms de límite); configuración en `application.yaml` | `tiempo_limite_corta_una_pasarela_lenta`, `el_cortacircuitos_se_abre_deja_de_llamar_y_se_recupera` |
| Cobro sin duplicados con una pasarela lenta | clave de idempotencia por pedido; anulación por clave si el tiempo vence | `con_una_pasarela_lenta_el_pedido_rechazado_no_deja_cobro_neto_ni_cobros_duplicados`, `repetir_la_misma_clave_en_la_pasarela_cobra_una_sola_vez...` |
| Inventario sin vender de más | `UPDATE ... WHERE disponible >= ?` atómico | `treinta_pedidos_simultaneos_por_diez_unidades_no_venden_de_mas` |
| Qué es síncrono y qué va por eventos | [DECISIONES.md](DECISIONES.md) | no aplica |

**Qué no cubre:** la extensión (extraer un módulo a un servicio independiente y documentar todo lo que cambia). `ApplicationModules.verify()` pasa, pero casi todos los módulos exponen una sola clase pública; la regla del "interior" se aplica a `pagos.interno` y `pedidos.interno`, los únicos paquetes internos que hay.

## Cómo probarla

Requiere JDK 21, Maven y Docker en marcha (Testcontainers levanta PostgreSQL y Kafka propios; **no necesitas `docker compose` para probar**).

```bash
mvn clean verify
```

Ejecuta 17 pruebas: arquitectura y módulos, y la tienda completa contra PostgreSQL y Kafka reales (fallos inyectados, pasarela lenta, cortacircuitos, concurrencia, plan de ejecución).

## Cómo levantarla y usarla

1. Levanta PostgreSQL (5432) y Kafka (9092):

```bash
docker compose up -d --wait
```

2. Arranca la tienda (puerto 8080). Sin un colector de trazas conviene apagar el muestreo:

```bash
TRAZAS_MUESTREO=0 mvn spring-boot:run
```

Sin colector OpenTelemetry verás un WARN con traza de pila ("Failed to publish metrics to OTLP receiver ... localhost:4318"). Es esperado e inofensivo: solo indica que las métricas no tienen a dónde exportarse.

3. Úsala:

```bash
# crear un producto con 5 unidades
curl -X POST localhost:8080/productos -H 'Content-Type: application/json' -d '{"sku":"CUADERNO","nombre":"Cuaderno","precio":10,"stock":5}'

# pedir 2 unidades: reserva, cobra, confirma y publica el evento
curl -X POST localhost:8080/pedidos -H 'Content-Type: application/json' -d '{"cliente":"ana","sku":"CUADERNO","cantidad":2}'

# pedir más de lo que hay: RECHAZADO, sin cobrar
curl -X POST localhost:8080/pedidos -H 'Content-Type: application/json' -d '{"cliente":"ana","sku":"CUADERNO","cantidad":9}'

# ver un pedido, la salud y el estado del cortacircuitos de la pasarela
curl localhost:8080/pedidos/1
curl localhost:8080/actuator/health
curl localhost:8080/actuator/circuitbreakers
```

El evento `PedidoConfirmado` sale por Kafka (tema `tienda.eventos`) y el módulo de notificaciones lo registra en `notificaciones_enviadas`:

```bash
docker compose exec postgres psql -U tienda -d tienda -c 'SELECT * FROM notificaciones_enviadas'
```

4. Al terminar: `docker compose down -v`.

**Variables:** `TIENDA_DB_URL`, `TIENDA_DB_USUARIO`, `TIENDA_DB_CLAVE`, `KAFKA_SERVIDORES`, `PUERTO` (8080), `OTLP_TRAZAS` y `TRAZAS_MUESTREO`. Si un puerto está ocupado: `PG_PUERTO=5433 KAFKA_PUERTO=9093 docker compose up -d --wait` y apunta las variables a esos puertos.

## Decisiones de diseño

Están en [DECISIONES.md](DECISIONES.md), incluida la justificación de qué es síncrono y qué va por eventos.

El proyecto 6 parte de esta misma tienda: [06-plataforma-final](../06-plataforma-final/README.md).
