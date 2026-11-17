# Kafka 01 - El registro de eventos (taller de consola)

Todo se ejecuta con `ejecutar.sh` (diez pasos, cada uno con su comprobación). La salida cruda de cada paso queda en `salida/`. Pasos 1 a 9 contra el entorno del curso (`kafka-y-sagas/entorno/compose.yaml`, imagen `apache/kafka:4.3.1`, sin puerto publicado); el paso 8 largo y el 10, con `compose.yaml` de esta carpeta.

## Paso 1 - Tema `pedidos` con tres particiones (`salida/01-describir.txt`)

```
Topic: pedidos  PartitionCount: 3  ReplicationFactor: 1  Configs: min.insync.replicas=1
  Topic: pedidos  Partition: 0  Leader: 1  Replicas: 1  Isr: 1  Elr:  LastKnownElr:
  (idénticas para la 1 y la 2)
```

| Columna | Qué significa |
|---|---|
| `PartitionCount` | cuántos registros independientes (cada uno con su orden y sus posiciones) tiene el tema; es el techo de paralelismo de un grupo |
| `ReplicationFactor` | cuántas copias de cada partición hay; 1 porque el entorno tiene un solo broker, sin tolerancia a fallos |
| `Configs` | las configuraciones del tema que difieren del defecto; aquí `min.insync.replicas=1`, lo que `acks=all` exige como mínimo de copias al día |
| `Partition` | el número de la partición dentro del tema |
| `Leader` | el broker que atiende lecturas y escrituras de esa partición (el 1, el único) |
| `Replicas` | los brokers que guardan una copia, incluido el líder |
| `Isr` | in-sync replicas: las réplicas que van al día con el líder; solo ellas pueden heredar el liderazgo sin perder datos |
| `Elr` / `LastKnownElr` | réplicas elegibles para liderar (Kafka 4): vacío mientras no ha habido caídas |

## Paso 2 - Diez mensajes sin clave (`salida/02-sin-clave.txt`)

Los diez cayeron en la partición 0, posiciones 0 a 9. Sin clave el productor no reparte mensaje a mensaje: llena un lote contra una partición y cambia al siguiente lote. Con una ráfaga corta entra todo en un lote, así que "sin clave" no significa "repartido a partes iguales" sino "sin garantía alguna de dónde".

## Paso 3 - Tres claves (`salida/03-con-clave.txt`, `03-mapa-3-particiones.txt`)

`cliente-1` y `cliente-2` cayeron siempre en la partición 1; `cliente-3`, siempre en la 0. Se comprobó con `cut | sort -u` que cada clave tiene exactamente una partición. La partición es `hash(clave) mod particiones`: determinista, y dos claves distintas pueden compartirla.

## Paso 4 - Dos consumidores sin grupo explícito (`salida/04-*.txt`)

Cada uno recibió los 20 mensajes. La consola crea un grupo nuevo (`console-consumer-NNNNN`) en cada ejecución; `--list` mostró dos grupos. Cada grupo lleva su propia posición, y un mensaje leído no se consume: el registro sigue ahí. Para repartir el trabajo entre dos consumidores hay que ponerlos en el MISMO grupo.

## Paso 5 - Solo la partición 1 desde la posición 2 (`salida/05-particion-1.txt`)

Empieza en `Partition:1 Offset:2` y no aparece ninguna otra partición. En un incidente sirve para ir directo al mensaje sospechoso (la posición que dice el log de errores) sin leer el resto, y para reproducir, sin grupo ni efectos, qué vio exactamente un consumidor que falló.

## Paso 6 - Dos claves alternadas (`salida/06-alternadas.txt`)

Producidas como x1 y1 x2 y2 ... Llegaron en ese mismo orden. Lo que garantiza Kafka: el orden DENTRO de una partición, y por tanto de todos los mensajes de una clave (`x1..x5` en orden, `y1..y5` en orden; las dos pruebas del script lo exigen). Lo que no garantiza: el orden entre claves en particiones distintas. Aquí cayeron en la misma partición, así que además se ve el intercalado; con otras claves ese intercalado no está prometido.

## Paso 7 - De tres a seis particiones (`salida/07-mapa-6-particiones.txt`, `07-cambiaron.txt`)

| Clave | Con 3 particiones | Con 6 |
|---|---|---|
| cliente-1 | 1 | 1 |
| cliente-2 | 1 | 4 |
| cliente-3 | 0 | 0 |

Una de tres claves cambió de partición (`cliente-2`: de la 1 a la 4). Con mis palabras, el riesgo (paso 7 de los criterios): el orden por clave solo vale mientras la clave vive en UNA partición. Si un sistema asume "todo lo de cliente-2 está en orden en la partición 1", al ampliar, los mensajes viejos se quedan en la 1 y los nuevos van a la 4; un consumidor que lea la 4 puede procesar el mensaje nuevo antes de haber terminado de procesar los viejos de la 1. El orden entre "antes" y "después" se pierde justo en el momento de la ampliación. Por eso el número de particiones se piensa para el futuro y no se cambia con el tráfico en vuelo (o se hace parando productores y drenando antes).

## Paso 8 - Retención de un minuto (`salida/08-tras-75s.txt`, `08b-*.txt`)

Con `retention.ms=60000`, a los 75 s los cinco mensajes SEGUÍAN ahí. Dos razones, comprobadas:

1. La retención borra segmentos, no mensajes. El segmento activo (donde se escribe) no se borra nunca; solo los cerrados. Con el tamaño por defecto (1 GiB) el segmento no se cierra.
2. El broker revisa la retención cada `log.retention.check.interval.ms` (5 minutos por defecto).

Se comprobó la explicación con el compose propio: `segment.ms=10000` (el segmento se cierra a los 10 s si llega un mensaje nuevo) y revisión cada 5 s. El script produjo cinco mensajes, esperó, produjo el sexto (que abre segmento nuevo) y el registro del broker (`08b-registro-broker.txt`) dice: `Deleting segment ... due to log retention time 60000ms breach based on the largest record timestamp in the segment`. Quedó solo el mensaje del segmento activo.

## Paso 9 - Tema compactado (`salida/09-antes.txt`, `09-despues.txt`)

Lo que esperaba: tras la compactación, de `cuenta-7` solo el último valor (nulo, una lápida), y `cuenta-9` con 50. Lo que vi:

- Antes: posiciones 0 a 6, con los cinco valores de `cuenta-7`, `cuenta-9=50` y `cuenta-7=null`.
- Después (esperando a que el limpiador pasara): `Offset:5 cuenta-9 50`, `Offset:6 cuenta-7 null`, `Offset:7 cuenta-1 1`. Los cinco valores viejos desaparecieron; las POSICIONES no se renumeran (hay un hueco del 0 al 4); la lápida sigue ahí un tiempo (`delete.retention.ms`) para que los consumidores que aún no la vieron se enteren del borrado.

Diferencia con lo que esperaba: la compactación no es inmediata ni sobre el segmento activo. Hubo que cerrar el segmento (`segment.ms=5000` más un mensaje nuevo) y bajar `min.cleanable.dirty.ratio`; y la lápida no desaparece al compactar. La versión de cinco minutos que sugiere el paso (esperar a la configuración por defecto) no se ejecutó; se usó la configuración acelerada (`segment.ms=5000` y `min.cleanable.dirty.ratio` bajo), que muestra lo mismo en segundos.

## Paso 10 - `down` y `up` (`salida/10-*.txt`, `compose.yaml`)

Con el compose del curso, tras `down` y `up` el tema `persistente` desapareció: la imagen configurada solo con variables de entorno guarda los registros en `/tmp/kafka-logs` dentro del contenedor, y `down` borra el contenedor. El `compose.yaml` propio fija `KAFKA_LOG_DIRS=/var/lib/kafka/data` y monta un volumen con nombre `datos-kafka`; tras `down` (sin `-v`) y `up`, el tema y el mensaje `dato-que-sobrevive` siguen. Con `down -v` se borran los datos: es lo que se quiere al terminar un taller.

## Qué clave usaría para mi servicio de pedidos (para la sesión 08)

La clave es el identificador del pedido (o del cliente, si lo que debe quedar en orden es todo lo de un cliente). Debe quedar en orden: el ciclo de vida de UN pedido (creado, aprobado, confirmado o cancelado); entre pedidos distintos no hace falta orden. Con la clave `pedidoId` todos los eventos de un pedido caen en la misma partición.
