# Entorno de los talleres

Kafka en tu equipo con Docker, sin instalar nada más. Desde la versión 4, Kafka ya no usa ZooKeeper: el propio clúster se coordina con un protocolo interno llamado KRaft, y por eso basta un contenedor.

| Archivo | Qué levanta | Para qué talleres |
|---|---|---|
| [`compose.yaml`](compose.yaml) | Un broker | Casi todos |
| [`compose.cluster.yaml`](compose.cluster.yaml) | Tres brokers | Replicación y caída de un broker (Kafka 02) |

<!-- file: compose.yaml -->
```yaml
# Un solo broker en modo KRaft. Los datos son efímeros: "docker compose down" los borra.
services:
  kafka:
    image: apache/kafka:4.3.1
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      # Dos puertas: PLAINTEXT para otros contenedores (kafka:19092) y PLAINTEXT_HOST para tu equipo (localhost:9092)
      KAFKA_LISTENERS: PLAINTEXT://:19092,PLAINTEXT_HOST://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:19092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      # Con un solo broker, los temas internos no pueden tener tres réplicas
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      # Los temas se crean a propósito, no por accidente al escribir mal un nombre
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
    healthcheck:
      test: ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1"]
      interval: 10s
      timeout: 10s
      retries: 10
```

<!-- file: compose.cluster.yaml -->
```yaml
# Tres brokers, cada uno también controlador. Desde tu equipo: localhost:29092, localhost:39092 y localhost:49092.
x-entorno-comun: &entorno-comun
  KAFKA_PROCESS_ROLES: broker,controller
  KAFKA_LISTENERS: PLAINTEXT://:19092,PLAINTEXT_HOST://:9092,CONTROLLER://:9093
  KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
  KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
  KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
  KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093
  KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
  KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"

services:
  kafka-1:
    image: apache/kafka:4.3.1
    ports:
      - "29092:9092"
    environment:
      <<: *entorno-comun
      KAFKA_NODE_ID: 1
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:19092,PLAINTEXT_HOST://localhost:29092

  kafka-2:
    image: apache/kafka:4.3.1
    ports:
      - "39092:9092"
    environment:
      <<: *entorno-comun
      KAFKA_NODE_ID: 2
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-2:19092,PLAINTEXT_HOST://localhost:39092

  kafka-3:
    image: apache/kafka:4.3.1
    ports:
      - "49092:9092"
    environment:
      <<: *entorno-comun
      KAFKA_NODE_ID: 3
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-3:19092,PLAINTEXT_HOST://localhost:49092
```

## Uso

```bash
docker compose up -d                 # un broker
docker compose ps                    # espera a que esté "healthy"
docker compose down                  # detiene y borra los datos

docker compose -f compose.cluster.yaml up -d      # tres brokers
```

Las herramientas de línea de órdenes vienen dentro de la imagen, en `/opt/kafka/bin/`. Para no escribir la ruta cada vez:

```bash
alias kafka='docker compose exec kafka'
kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

| Herramienta | Para qué |
|---|---|
| `kafka-topics.sh` | Crear, listar, describir y modificar temas |
| `kafka-console-producer.sh` | Escribir mensajes desde la terminal |
| `kafka-console-consumer.sh` | Leer mensajes desde la terminal |
| `kafka-consumer-groups.sh` | Ver grupos, posiciones y retraso; mover posiciones |
| `kafka-configs.sh` | Ver y cambiar configuración de temas y brokers |

## Qué dirección usar

| Desde dónde te conectas | Dirección |
|---|---|
| Tu equipo (una aplicación que ejecutas fuera de Docker) | `localhost:9092` |
| Otro contenedor en la misma red de Compose | `kafka:19092` |
| Dentro del propio contenedor de Kafka | `localhost:9092` |
| Con los tres brokers, desde tu equipo | `localhost:29092`, `localhost:39092` o `localhost:49092` |
| Con los tres brokers, dentro de un contenedor | `kafka-1:19092` (o `kafka-2:19092`, `kafka-3:19092`). Aquí `localhost:9092` no sirve: ese broker anuncia `localhost:29092`, que dentro del contenedor no existe |

Con los tres brokers el servicio no se llama `kafka`, así que el alias cambia: `alias kafka='docker compose -f compose.cluster.yaml exec kafka-1'`.

Si una aplicación "conecta pero luego se queda colgada", casi siempre es esto: el broker le responde con la dirección anunciada, y esa dirección no es alcanzable desde donde está la aplicación.

## Estado

Los dos archivos se validaron con `docker compose config`. **No se levantaron** al preparar el curso: no había motor de Docker disponible. La configuración sigue el ejemplo de la documentación de la imagen oficial; si algo no arranca, compárala con la versión vigente de esa documentación.

La etiqueta `4.3.1` era la última publicada al escribir esto. Usa la vigente y fíjala. Algunas opciones de las herramientas de consola se renombraron en la serie 4: si una orden de los talleres avisa de una opción obsoleta, `--help` dice cuál es la nueva.
