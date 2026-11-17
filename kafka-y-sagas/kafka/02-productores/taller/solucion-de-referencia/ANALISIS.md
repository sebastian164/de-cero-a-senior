# Kafka 02 - Productores

Código en `src/main/java/productores/` (`ProductorPedidos`, `Lote`); pruebas en `ProductoresTest` (7), ejecutadas con el broker único (puerto 47092) y el clúster de tres brokers del curso (puertos 47291 a 47293). Cada medida sale de `salida/evidencia.txt`, que las pruebas escriben al ejecutarse. Todas las pruebas con infraestructura tienen límite de tiempo (15 minutos por defecto, 12 la Parte A) y las esperas que podrían colgarse están acotadas.

## Resumen de pasos

| Paso | Qué se hizo | Medida |
|---|---|---|
| 1 | tema de 3 particiones y factor 3 | tres particiones con ISR 3 (líder y réplicas por partición en la evidencia) |
| 2 | broker 3 detenido | el ISR de cada partición queda en 2; 30 producidos y 30 leídos |
| 3 | `min.insync.replicas` y `acks=all` | ver abajo: dos escenarios |
| 4 | `acks=1` en la misma situación | confirma con solo el líder |
| 5 | brokers de vuelta | el ISR vuelve a 3 en unos segundos (medido en la evidencia) |
| 6 y 7 | productor con clave y registro | `OK pedido p-N partición P posición O` por envío; una clave, una partición |
| 8 | `linger.ms` 0 contra 50 | ráfaga de 1000: unas decenas de ms en ambos; 100 envíos síncronos: del orden de 100-200 ms con 0 contra unos 5200 ms con 50 |
| 9 | matar contra apagado ordenado | `Lote` acepta exactamente 2 000 envíos con `linger.ms=60000`; matar (SIGKILL) dejó 0 en el tema (perdidos 2 000, todo lo del búfer); el ordenado (SIGTERM + `close()`), 2 000 (perdidos 0). Idéntico en 10 repeticiones seguidas |
| 10 | detener el líder con `acks=all` e idempotencia | 20 000 confirmados, 0 fallidos, 20 000 distintos, orden conservado |
| 11 | sin idempotencia y `request.timeout.ms=2` | miles de duplicados; con idempotencia, 0 |
| 12 | mensaje de 2 MiB | `RecordTooLargeException` del cliente (a) y del broker (b); con `acks=0` y sin callback no se ve nada (c); con callback queda registrado (d) |

## Decisiones de configuración y su razón

| Opción | Valor | Razón |
|---|---|---|
| `acks` | `all` | solo confirma cuando todas las réplicas en el ISR tienen el mensaje; con `acks=1` un fallo del líder justo después de confirmar pierde el mensaje |
| `enable.idempotence` | `true` | los reintentos no duplican (paso 11: miles de duplicados sin ella, 0 con ella) ni reordenan |
| `delivery.timeout.ms` | acotado en las pruebas, `120000` por defecto del taller | el tiempo total que `send()` puede tardar en rendirse; debe ser mayor que `linger.ms + request.timeout.ms` (el cliente rechaza 30000 con los defectos) |
| `linger.ms` | 0 por defecto; 5 en el paso 10 | espera para agrupar mensajes: baja el coste por mensaje en ráfagas, sube la latencia de cada envío síncrono |
| `retries` / reintentos | los de la idempotencia | no se desactivan salvo para demostrar el error exacto del broker |
| `max.request.size` | 1 MiB (defecto) | el límite del cliente protege antes de enviar; el del broker (`message.max.bytes`) lo rechaza si el cliente lo deja pasar |

## Paso 3 - el error exacto, en dos escenarios

Los tres brokers del curso son a la vez controladores: el quórum de metadatos es de 2 de 3. Detener DOS brokers (el escenario literal del enunciado) deja al clúster sin quórum. Eso tiene una consecuencia que el enunciado no menciona: nadie puede encoger el ISR y nadie puede asignar identificadores de productor idempotente nuevos.

- Escenario 1, determinista (un broker caído): se crea `pa-min3` con `min.insync.replicas=3`. Al detener un broker, el ISR baja a 2, menor que 3. Con `acks=all`, `enable.idempotence=false` y `retries=0` el error exacto es:

  `org.apache.kafka.common.errors.NotEnoughReplicasException: Messages are rejected since there are fewer in-sync replicas than required.`

  Con los valores por defecto (idempotente, reintentos) el mismo error es reintentable: el cliente insiste y, al vencer `delivery.timeout.ms`, el callback recibe `TimeoutException: Expiring 1 record(s) ...`. Es decir, el error "de verdad" solo se ve si se desactivan los reintentos; con los defectos se ve el vencimiento.
- Escenario 2, el literal (`min.insync.replicas=2`, dos seguidores detenidos): según si el líder alcanzó a encoger el ISR antes de perder el quórum se obtiene `NotEnoughReplicasException` o `TimeoutException`; la prueba exige que NO se confirme y registra cuál salió. Un productor idempotente que ya había confirmado un envío (ya tenía identificador) falla por vencimiento de `delivery.timeout.ms`.
- Un productor idempotente NUEVO sin quórum no consigue su identificador (`InitProducerId`). En la prueba, con `delivery.timeout.ms=15000`, el lote venció a los 15 s; un `flush()` sin límite puede esperar minutos. Por eso en la solución todo `flush()` que depende de un estado degradado es acotado (`vaciarHasta`) y los cierres usan `close(Duration)`.

## Paso 4 - el riesgo aceptado con `acks=1`

Con el líder vivo y los seguidores caídos, `acks=1` SÍ confirma (`confirmados=1`). Lo que se acepta: el mensaje existe en una sola copia. Si ese broker se pierde antes de que alguien lo replique, el mensaje se pierde habiendo sido confirmado al cliente. `acks=all` con `min.insync.replicas=2` es la protección: prefiere rechazar a confirmar sin copias suficientes.

## Paso 8 - por qué la ráfaga no nota `linger.ms` y el envío síncrono sí

En una ráfaga asíncrona de 1000 envíos el productor llena lotes mientras sigue recibiendo: con `linger.ms=0` ya se agrupa lo que llega mientras hay una petición en vuelo, y con 50 el lote sale antes por tamaño; el tiempo total es parecido (decenas de ms). En el envío síncrono (`send().get()`) cada mensaje espera su confirmación antes de enviar el siguiente: el lote nunca se llena y, con `linger.ms=50`, cada envío paga los 50 ms completos (100 x ~50 ms = ~5 s frente a ~0,1 s). La lección: `linger.ms` es un impuesto para quien espera cada confirmación y una ganancia para quien produce en paralelo.

## Paso 9 y 11

- Matar el proceso (SIGKILL) pierde lo que el productor tenía en el búfer. La medición es determinista: `Lote` usa `linger.ms=60000` (nada sale por tiempo; `batch.size` de 512 KiB no se llena con 2 000 mensajes), envía exactamente 2 000, escribe "2000 listo" y se duerme sin cerrar. El padre espera la marca "listo" y solo entonces mata (`destroyForcibly`) o apaga (`destroy`), así el kill nunca compite con un vaciado. Resultado: con SIGKILL hay 0 mensajes en el tema (se pierden los 2 000); con SIGTERM el gancho llama a `close()`, que vacía el búfer, y hay 2 000. Una alternativa peor: anotar el progreso cada 100 envíos con `linger.ms=1000`. La cifra de aceptados sería solo una cota inferior y, si el kill cae justo tras un vaciado, la resta da 0 o negativo: una medición intermitente.
- Para provocar duplicados sin idempotencia, `request.timeout.ms=2` hace que el cliente dé por vencida la petición aunque el broker la esté escribiendo, y reintente: el mensaje queda escrito dos o más veces. Con idempotencia, el número de secuencia hace que el broker descarte la repetición.
