# 01 — El registro de eventos

## Idea

El libro de un notario. Cada acto se anota al final, con su número de folio, y nunca se borra ni se corrige: si algo cambió, se anota un acto nuevo. Cualquiera puede leer el libro desde el folio que quiera, a su ritmo, y cada lector lleva su propio marcapáginas. Dos lectores no se estorban, y que uno llegue tarde no le hace perder nada.

Eso es Kafka. Y es lo que lo distingue de una cola: en una cola, el mensaje que alguien toma desaparece.

## Lo esencial

| Concepto | Qué es |
|---|---|
| **Broker** | Un servidor de Kafka. Un clúster son varios |
| **Tema** (*topic*) | Un flujo de mensajes con nombre: `pedidos`, `pagos` |
| **Partición** | Cada tema se divide en particiones. Cada una es un registro ordenado al que solo se añade por el final |
| **Posición** (*offset*) | El número de cada mensaje dentro de su partición |
| **Mensaje** | Clave, valor, cabeceras y marca de tiempo |
| **Productor** | Quien escribe |
| **Consumidor** | Quien lee |
| **Retención** | Cuánto se conservan los mensajes, por tiempo o por tamaño. No depende de si alguien los leyó |

### Tres consecuencias que hay que tener claras desde hoy

1. **El orden solo existe dentro de una partición.** Entre particiones no hay orden. Si dos mensajes tienen que procesarse en orden, tienen que caer en la misma partición.
2. **La clave decide la partición.** Mensajes con la misma clave van siempre a la misma partición. La clave es una decisión de diseño, no un adorno.
3. **Leer no borra.** Cada grupo de consumidores guarda su propia posición. Se puede volver atrás y releer.

### Cola frente a registro

| | Cola tradicional | Registro (Kafka) |
|---|---|---|
| Al leer un mensaje | Se elimina | Sigue ahí |
| Varios lectores independientes | Compiten por los mensajes | Cada grupo lee todo |
| Releer lo pasado | No | Sí, mientras dure la retención |
| Orden | Global, en general | Por partición |
| Uso típico | Repartir tareas | Publicar hechos que interesan a varios |

Desde Kafka 4.2 existen además los *share groups*, que dan un comportamiento de cola (varios consumidores sobre la misma partición, con confirmación por mensaje) a costa del orden. En estos talleres se usa el modelo clásico de grupos de consumidores, que es el que necesitas dominar primero.

### Compactación

Además de borrar por antigüedad, un tema puede **compactarse**: Kafka conserva, para cada clave, al menos el último mensaje. Sirve para guardar "el estado actual de cada cosa" (el último saldo de cada cuenta). Un mensaje con valor nulo le indica que esa clave se puede eliminar.

## Ejemplo

Necesitas el [entorno](../../entorno/) levantado.

### Antes

```bash
# Un productor que escribe sin pensar en la clave, en un tema creado con una sola partición "para no complicarse"
kafka-topics.sh --create --topic eventos --partitions 1 --replication-factor 1
echo 'cuenta-7 abono 100'  | kafka-console-producer.sh --topic eventos
echo 'cuenta-7 retiro 100' | kafka-console-producer.sh --topic eventos
```

(En todas las órdenes se omite `--bootstrap-server localhost:9092` y la ruta `/opt/kafka/bin/`.)

### Análisis

| Problema | Consecuencia |
|---|---|
| Un tema llamado `eventos` para todo | Nadie sabe qué contiene ni quién depende de él |
| Una sola partición | Un único consumidor puede leerlo: no hay forma de repartir el trabajo. Y cambiarlo después altera el reparto de claves |
| Sin clave | El día que se aumenten las particiones, el abono y el retiro de la misma cuenta pueden caer en particiones distintas y procesarse en desorden: un retiro antes que su abono |
| La cuenta va dentro del texto del mensaje | Kafka no puede usarla para nada |

### Después

```bash
kafka-topics.sh --create --topic movimientos-de-cuenta --partitions 3 --replication-factor 1
kafka-topics.sh --describe --topic movimientos-de-cuenta

# La clave va separada del valor: todo lo de cuenta-7 cae en la misma partición, en orden
kafka-console-producer.sh --topic movimientos-de-cuenta \
  --property parse.key=true --property key.separator=:
> cuenta-7:abono 100
> cuenta-9:abono 50
> cuenta-7:retiro 100

kafka-console-consumer.sh --topic movimientos-de-cuenta --from-beginning \
  --property print.key=true --property print.partition=true --property print.offset=true
```

- El nombre del tema dice qué hechos contiene.
- Tres particiones permiten hasta tres consumidores en paralelo.
- La clave es la cuenta: los movimientos de una misma cuenta se leen en el orden en que se escribieron.

Las órdenes son ilustrativas: no se ejecutaron al preparar el curso.

## Errores comunes

- Tratar Kafka como una cola y sorprenderse de que los mensajes "siguen ahí".
- Esperar orden global en un tema con varias particiones.
- Elegir el número de particiones al azar. Subirlo después cambia a qué partición va cada clave.
- No pensar la clave.
- Un tema gigante para todos los tipos de evento, o un tema por cada cosa minúscula.
- Dejar activada la creación automática de temas: un nombre mal escrito crea un tema nuevo en silencio.
- Confundir retención con respaldo.

## Taller

**Objetivo:** ver con tus propios ojos particiones, posiciones, claves y retención.

No hay archivos de inicio. Anota cada orden y su salida.

1. Levanta el entorno. Crea el tema `pedidos` con tres particiones y descríbelo. Explica cada columna de la descripción.
2. Produce diez mensajes **sin clave** y léelos mostrando partición y posición. ¿Cómo se repartieron?
3. Produce diez mensajes con clave, usando solo tres claves distintas (`cliente-1`, `cliente-2`, `cliente-3`). Demuestra que cada clave cae siempre en la misma partición.
4. Abre dos consumidores **sin grupo explícito** leyendo desde el principio. ¿Ambos reciben todo? Explica por qué.
5. Lee solo la partición 1 desde la posición 2. ¿Para qué te serviría esto en un incidente?
6. Con un consumidor leyendo, produce mensajes de dos claves alternadas. Mira el orden en que llegan. ¿Qué orden está garantizado y cuál no?
7. Aumenta el tema a seis particiones. Produce de nuevo con las mismas tres claves. ¿Siguen cayendo donde antes? Explica qué le pasaría a un sistema que dependiera del orden por clave.
8. Crea un tema con retención de un minuto. Produce, espera y vuelve a leer desde el principio. Averigua por qué quizá no se borró exactamente al minuto.
9. Crea un tema compactado. Produce cinco valores para la misma clave y luego un valor nulo. Describe lo que esperas ver tras la compactación y lo que ves de verdad.
10. Detén el entorno con `down` y vuelve a levantarlo. ¿Qué pasó con los temas? Averigua en la documentación de la imagen cómo conservar los datos y hazlo.

**Criterios de aceptación**

- Cada afirmación viene con la orden y la salida que la respalda.
- El paso 7 explica el riesgo con tus palabras, no con una cita.
- El paso 10 deja un `compose.yaml` propio con datos persistentes en `taller/solucion/`.

**Entrega:** rama `taller/kafka-01`, con `taller/solucion/ANALISIS.md`.

## En el trabajo real

El número de particiones y la clave de cada tema son de las pocas decisiones de Kafka difíciles de cambiar después. En un equipo se discuten en la revisión de diseño, junto con el nombre del tema, la retención y quién es su dueño.

## Preguntas senior

1. ¿Qué diferencia de fondo hay entre un registro y una cola, y qué permite cada uno?
2. ¿Por qué el orden solo se garantiza dentro de una partición?
3. ¿Cómo eliges la clave de un tema? Da un ejemplo en el que la elección obvia sea mala.
4. ¿Cómo decides el número de particiones y qué pasa si te equivocas?
5. ¿Para qué sirve un tema compactado?
6. ¿Cuándo no usarías Kafka?
