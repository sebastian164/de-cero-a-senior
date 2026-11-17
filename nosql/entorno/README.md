# Entorno de los talleres de NoSQL

Un MongoDB y un Redis en contenedores, sin instalar nada más.

MongoDB arranca como **replica set de un solo nodo**. No es por alta disponibilidad (con un nodo no la hay): es porque las transacciones de varios documentos, que usan las sesiones 03 y 04, solo existen en un replica set. Contra un `mongod` suelto, la primera transacción falla: la orden llega al servidor y este la rechaza con el código 20 (`Transaction numbers are only allowed on a replica set member or mongos`); el controlador de Java y `mongosh` sustituyen ese mensaje por `This MongoDB deployment does not support retryable writes. Please add retryWrites=false to your connection string.`, que es el que verás.

<!-- file: compose.yaml -->
```yaml
# MongoDB como replica set de un nodo (sin replica set no hay transacciones) y Redis.
# Los datos son efímeros: "docker compose down -v" los borra.
services:
  mongo:
    image: mongo:9.0.8
    command: ["mongod", "--replSet", "rs0", "--bind_ip_all"]
    ports:
      - "27017:27017"
    healthcheck:
      # La primera vez inicia el replica set; las siguientes solo comprueba que hay un primario.
      # El miembro se anuncia como localhost:27017 para que tu equipo, que entra por el puerto publicado, lo alcance.
      test:
        - CMD
        - mongosh
        - --quiet
        - --eval
        - "try { rs.status().ok } catch (e) { rs.initiate({_id: 'rs0', members: [{_id: 0, host: 'localhost:27017'}]}).ok }; db.hello().isWritablePrimary || quit(1)"
      interval: 5s
      timeout: 10s
      retries: 20
      start_period: 5s

  redis:
    image: redis:8.10.3
    # Sin límite de memoria ni desalojo: la sesión 06 guarda en Redis cosas que no se pueden perder por desalojo.
    command: ["redis-server", "--appendonly", "no", "--save", ""]
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10
```

## Uso

Desde esta carpeta:

```bash
docker compose -p nosql up -d --wait     # espera a que los dos estén "healthy"
docker compose -p nosql ps
docker compose -p nosql down -v          # detiene y borra los datos
```

La primera vez, el chequeo de salud de MongoDB tarda unos segundos más: es el que inicia el replica set. El `-p nosql` da un nombre fijo al proyecto, para que los contenedores se llamen igual desde cualquier carpeta.

## Qué dirección usar

| Desde dónde te conectas | MongoDB | Redis |
|---|---|---|
| Tu equipo (los ejemplos, tu aplicación ejecutada fuera de Docker) | `mongodb://localhost:27017/?replicaSet=rs0` | `redis://localhost:6379` |
| Otro contenedor de este mismo Compose | `mongodb://mongo:27017/?directConnection=true` | `redis://redis:6379` |

La diferencia en MongoDB importa. Con `replicaSet=rs0`, el controlador pregunta al servidor quiénes son los miembros y se conecta a la dirección que **ellos anuncian**, que aquí es `localhost:27017`. Desde tu equipo eso es correcto; desde otro contenedor, `localhost` es ese contenedor, y la conexión falla con `connect ECONNREFUSED 127.0.0.1:27017` aunque `mongo:27017` responda. Con `directConnection=true` el controlador habla con el servidor que le diste y no sigue la dirección anunciada. Es el mismo problema que la "dirección anunciada" de Kafka en el [entorno de Kafka](../../kafka-y-sagas/entorno/).

## Mirar dentro

`mongosh` y `redis-cli` vienen en las imágenes:

```bash
docker compose -p nosql exec mongo mongosh
docker compose -p nosql exec redis redis-cli
```

```text
$ docker compose -p nosql exec mongo mongosh --quiet --eval 'rs.status().members.map(m => m.name + " " + m.stateStr)'
[ 'localhost:27017 PRIMARY' ]
```

```text
$ docker compose -p nosql exec redis redis-cli PING
PONG
$ docker compose -p nosql exec redis redis-cli CONFIG GET maxmemory-policy
maxmemory-policy
noeviction
```

| Para qué | MongoDB (`mongosh`) | Redis (`redis-cli`) |
|---|---|---|
| Estado del servidor | `rs.status()`, `db.serverStatus().connections` | `INFO`, `INFO memory`, `INFO stats` |
| Qué hay | `show dbs`, `use nosql01_despues`, `show collections` | `SCAN 0 MATCH banco:* COUNT 100` |
| Ver un dato | `db.cuentas.findOne()` | `GET clave`, `HGETALL clave`, `TTL clave` |
| Índices y planes | `db.movimientos.getIndexes()`, `.explain("executionStats")` | — |
| Qué está llegando ahora | `db.currentOp()` | `MONITOR` (solo en desarrollo: cuesta rendimiento) |
| Lo lento | `db.setProfilingLevel(1, {slowms: 50})` y `db.system.profile.find()` | `SLOWLOG GET 10` |

`KEYS *` recorre todas las claves de un tirón y bloquea Redis mientras lo hace. Aquí, con cuatro claves, da igual; en producción, `SCAN`.

## Estado

El archivo se levantó con `docker compose` y los dos servicios llegaron a `healthy`; la salida de `mongosh` y `redis-cli` de arriba es de esa ejecución. Las direcciones "desde otro contenedor" se comprobaron con un contenedor temporal conectado a la red del proyecto.

Las etiquetas `mongo:9.0.8` y `redis:8.10.3` eran las estables más recientes al escribir esto. Usa las vigentes y fíjalas: una etiqueta como `latest` o `8` cambia sin avisar.

La imagen de Redis 8 trae módulos que antes eran de Redis Stack (búsqueda, JSON, filtros de Bloom). Los talleres no los necesitan, pero la sesión 05 menciona los filtros de Bloom como defensa contra la penetración de caché.
