# 02 — Productores: escribir sin perder ni duplicar

## Idea

Enviar una carta certificada. Puedes echarla al buzón y olvidarte, pedir el sello de la oficina que la recibió, o exigir el acuse firmado por el destinatario. Cada nivel cuesta más tiempo y te da más certeza. Y si no llega el acuse y vuelves a enviarla, el destinatario puede recibir dos: a menos que cada carta lleve un número que le permita reconocer la repetida.

## Lo esencial

### Réplicas

Cada partición tiene copias en varios brokers. Una es la **líder** (recibe las escrituras) y las demás son **seguidoras**. Las que están al día forman el conjunto de réplicas sincronizadas.

| Configuración | Dónde | Qué decide |
|---|---|---|
| Factor de replicación | El tema | Cuántas copias hay de cada partición |
| `min.insync.replicas` | El tema o el broker | Cuántas copias deben estar al día para aceptar una escritura con `acks=all` |
| `acks` | El productor | Qué confirmación espera antes de dar el envío por bueno |

| `acks` | El productor considera enviado el mensaje cuando… | Riesgo |
|---|---|---|
| `0` | Lo mandó | Se pierde sin que nadie se entere |
| `1` | La líder lo escribió | Se pierde si la líder cae antes de replicar |
| `all` | Lo tienen todas las réplicas sincronizadas | El más seguro; algo más lento |

La combinación habitual para datos que importan: factor de replicación 3, `min.insync.replicas=2` y `acks=all`. Aguanta la caída de un broker sin perder datos y sin dejar de aceptar escrituras.

### Reintentos e idempotencia

Si la confirmación no llega, el productor reintenta. Sin más, eso duplica: el mensaje sí se había escrito y lo que se perdió fue la confirmación. Con **idempotencia** (`enable.idempotence=true`, activada por defecto en los clientes modernos), cada mensaje lleva un número de secuencia y el broker descarta las repeticiones. También conserva el orden aunque haya reintentos.

Esa idempotencia cubre los reintentos de **un** productor durante **una** sesión. No cubre que tu aplicación llame dos veces a `send` con el mismo dato.

### Lotes

El productor no envía mensaje por mensaje: agrupa. `linger.ms` es cuánto espera para llenar un lote y `batch.size` su tamaño máximo. Más espera da más rendimiento y más latencia. `compression.type` comprime el lote entero.

### El envío es asíncrono

`send` devuelve enseguida. El resultado llega después, en una función de retorno o en el futuro que devuelve. Si no miras ese resultado, no sabes si se envió.

## Ejemplo

Código Java con el cliente oficial (`org.apache.kafka:kafka-clients`). Es ilustrativo: no se compiló al preparar el curso porque requiere descargar la biblioteca.

### Antes

```java
Properties config = new Properties();
config.put("bootstrap.servers", "localhost:9092");
config.put("key.serializer", StringSerializer.class.getName());
config.put("value.serializer", StringSerializer.class.getName());
config.put("acks", "0");

KafkaProducer<String, String> productor = new KafkaProducer<>(config);

void registrarPago(Pago pago) {
    productor.send(new ProducerRecord<>("pagos", pago.toString()));   // sin clave, sin mirar el resultado
}
// la aplicación termina sin cerrar el productor
```

### Análisis

| Problema | Consecuencia |
|---|---|
| `acks=0` | Un pago puede no escribirse y el código nunca lo sabrá |
| No se mira el resultado de `send` | Los errores (tema inexistente, mensaje demasiado grande, broker caído) pasan en silencio |
| Sin clave | Los pagos de un mismo pedido pueden leerse en desorden |
| `pago.toString()` como contenido | El formato depende de una implementación interna; cualquier cambio rompe a los consumidores |
| No se cierra el productor | Los mensajes que aún estaban en el lote se pierden al terminar el proceso |

### Después

```java
Properties config = new Properties();
config.put("bootstrap.servers", "localhost:9092");
config.put("key.serializer", StringSerializer.class.getName());
config.put("value.serializer", StringSerializer.class.getName());
config.put("acks", "all");
config.put("enable.idempotence", "true");
config.put("delivery.timeout.ms", "30000");      // tiempo total para lograrlo, reintentos incluidos
config.put("linger.ms", "10");
config.put("compression.type", "zstd");

try (KafkaProducer<String, String> productor = new KafkaProducer<>(config)) {   // cerrar vacía los lotes pendientes
    String clave = pago.pedidoId();                                             // todo lo de un pedido, en orden
    String valor = aJson(new PagoRegistrado(pago.id(), pago.pedidoId(), pago.valor(), pago.fecha()));

    productor.send(new ProducerRecord<>("pagos", clave, valor), (metadatos, error) -> {
        if (error != null) {
            registro.error("No se pudo publicar el pago {}", pago.id(), error);  // y decidir: reintentar, avisar, guardar
        } else {
            registro.info("Pago {} en partición {} posición {}", pago.id(), metadatos.partition(), metadatos.offset());
        }
    });
}
```

- `acks=all` con idempotencia: ni se pierde ni se duplica por reintentos.
- La clave es el pedido.
- El contenido es un evento con forma explícita, en JSON.
- El resultado se revisa.
- El productor se cierra.

En una aplicación real hay **un** productor para toda la aplicación, creado al arrancar y cerrado al apagar: es seguro compartirlo entre hilos y crearlo por mensaje es carísimo.

## Errores comunes

- `acks=1` o `0` para datos que no se pueden perder.
- Ignorar el resultado de `send`.
- Un productor nuevo por cada mensaje.
- Llamar a `get()` sobre el futuro en cada envío y convertir un cliente rápido en uno lentísimo, sin necesidad.
- Factor de replicación 1 en producción.
- `min.insync.replicas` igual al factor de replicación: la caída de un solo broker detiene las escrituras.
- Mensajes enormes. Kafka mueve hechos, no archivos: se publica la referencia al archivo.
- Creer que la idempotencia del productor hace idempotente a todo el sistema.

## Taller

**Objetivo:** escribir un productor fiable y demostrar, tumbando brokers, qué garantiza cada configuración.

Parte A, con las herramientas de consola y el clúster de tres brokers ([`compose.cluster.yaml`](../../entorno/compose.cluster.yaml)):

1. Crea un tema con tres particiones y factor de replicación 3. En la descripción, identifica la líder y las réplicas sincronizadas de cada partición.
2. Detén un broker. Describe el tema otra vez. ¿Qué cambió? ¿Se puede seguir produciendo y consumiendo?
3. Configura `min.insync.replicas=2` en el tema. Con un broker detenido, produce con `acks=all`. Detén un segundo broker y vuelve a producir. Copia el error exacto y explícalo.
4. Repite el paso 3 produciendo con `acks=1`. ¿Qué diferencia hay y qué riesgo estás aceptando?
5. Vuelve a levantar los brokers y observa cómo se recupera el conjunto de réplicas sincronizadas.

Parte B, con código (Java con el cliente oficial; si vienes de otra ruta, el cliente de tu lenguaje sirve igual):

6. Escribe un productor que publique eventos `PedidoCreado` en JSON al tema `pedidos`, con el identificador del cliente como clave.
7. Haz que registre, para cada envío, partición y posición, o el error.
8. Publica mil mensajes con `linger.ms=0` y luego con `linger.ms=50`. Mide el tiempo total y explica la diferencia.
9. Apaga la aplicación a mitad de un envío de diez mil mensajes, primero matando el proceso y luego con un apagado ordenado que cierre el productor. Cuenta en el tema cuántos llegaron en cada caso.
10. Detén el broker líder de una partición mientras produces con `acks=all` e idempotencia. Cuenta: ¿se perdió alguno?, ¿hay duplicados?
11. Desactiva la idempotencia, fuerza reintentos (por ejemplo, con un tiempo de espera de petición muy corto) e intenta producir duplicados. Documenta si lo lograste y cómo.
12. Publica un mensaje más grande que el límite del broker. ¿Dónde aparece el error? ¿Lo habría visto el código del "antes"?

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Con un broker caído no se pierde ningún mensaje | Conteo de mensajes producidos y leídos |
| Todo error de envío queda registrado | Paso 12 |
| El apagado ordenado no pierde mensajes | Paso 9 |
| Cada decisión de configuración tiene su razón | `ANALISIS.md` |

**Entrega:** rama `taller/kafka-02`, con el código en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

La configuración del productor suele venir de una biblioteca interna o de valores por defecto acordados por el equipo de plataforma, precisamente para que nadie publique datos importantes con `acks=1`. Tu trabajo es conocer qué garantiza cada opción para detectar cuándo esos valores no sirven para tu caso.

## Preguntas senior

1. Explica `acks`, factor de replicación y `min.insync.replicas`, y cómo se combinan.
2. ¿Qué garantiza la idempotencia del productor y qué no?
3. ¿Qué pasa con el orden cuando hay reintentos?
4. ¿Cómo equilibras latencia y rendimiento en un productor?
5. ¿Qué haces cuando `send` falla definitivamente?
6. ¿Por qué no se envían archivos grandes por Kafka?
