# 03 — Consumidores y grupos

## Idea

Un equipo leyendo un expediente de tres tomos. Se reparten: un tomo por persona, nunca dos personas el mismo tomo. Cada una deja un marcapáginas donde va. Si alguien se enferma, otro toma su tomo y sigue **desde el marcapáginas**, no desde donde de verdad iba el enfermo. Todo depende entonces de cuándo se mueve el marcapáginas: si se mueve antes de leer la página, el relevo se salta una; si se mueve después, el relevo relee una.

## Lo esencial

### Grupos de consumidores

- Un **grupo** es un conjunto de consumidores que se reparten las particiones de un tema. Cada partición la lee un solo consumidor del grupo.
- Grupos distintos son independientes: cada uno lee todo y lleva su propia posición.
- Más consumidores que particiones no ayuda: los que sobran se quedan sin trabajo. **El número de particiones es el techo del paralelismo.**
- Cuando entra o sale un consumidor, o cambian las particiones, el grupo se **reequilibra**: se reparten de nuevo.

### La posición confirmada

El consumidor lee desde una posición y, cada cierto tiempo, **confirma** hasta dónde llegó. Esa posición se guarda en Kafka, por grupo y partición. Tras un reinicio o un reequilibrio, se retoma desde ahí.

| Cuándo confirmas | Si el proceso cae a mitad | Garantía |
|---|---|---|
| Antes de procesar | El mensaje se da por leído y nunca se procesó | Como mucho una vez: puede **perder** |
| Después de procesar | El mensaje se procesó, no se confirmó y se vuelve a leer | Al menos una vez: puede **repetir** |

En casi todos los sistemas se elige "al menos una vez", y el consumidor se diseña para tolerar repeticiones (sesión 04).

### Confirmación automática

Con `enable.auto.commit=true`, que es el valor por defecto, el cliente confirma solo, cada cierto intervalo, lo que entregaron las llamadas anteriores a `poll`. Si procesas todo dentro del bucle antes de volver a llamar a `poll`, el resultado es "al menos una vez". Se vuelve peligroso cuando el procesamiento sale de ese bucle: lo pasas a otro hilo, lo dejas en una cola interna o atrapas una excepción y sigues. Entonces se confirman mensajes que nadie terminó de procesar.

### Retraso

El **retraso** (*lag*) es la distancia entre el último mensaje de la partición y la posición confirmada del grupo. Es la métrica más importante de un consumidor: si crece sin parar, el consumidor no da abasto o está detenido.

### Lo que saca a un consumidor del grupo

| Configuración | Qué vigila |
|---|---|
| `session.timeout.ms` / latidos | Que el proceso siga vivo |
| `max.poll.interval.ms` | Que no pase demasiado tiempo entre dos `poll`. Si procesar un lote tarda más, el grupo lo da por muerto, reparte sus particiones y otro empieza a procesar lo mismo |

`auto.offset.reset` decide dónde empieza un grupo que **no tiene** posición guardada: `earliest` (desde el principio) o `latest` (solo lo nuevo). No afecta a un grupo que ya tiene posición.

## Ejemplo

Un modelo en Java puro de una partición, una posición confirmada y un consumidor que se cae. No usa Kafka: aísla la única decisión que importa aquí. Ejecuta cada uno con `java Main.java`.

### Antes

<!-- file: ejemplo/antes/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

/** Un consumidor que confirma la posición ANTES de procesar el mensaje. */
public class Main {

    static final List<String> PARTICION = List.of("pago-1", "pago-2", "pago-3", "pago-4", "pago-5");
    static int posicionConfirmada = 0;                       // lo que el grupo tiene guardado en Kafka
    static final List<String> procesados = new ArrayList<>();

    public static void main(String[] args) {
        try {
            consumir(true);                                  // primera ejecución: se cae en pago-3
        } catch (IllegalStateException caida) {
            System.out.println("  >> el proceso se cayó: " + caida.getMessage());
        }
        System.out.println("  >> reinicio: retomo desde la posición confirmada " + posicionConfirmada);
        consumir(false);

        List<String> perdidos = new ArrayList<>(PARTICION);
        perdidos.removeAll(procesados);
        System.out.println();
        System.out.println("En la partición: " + PARTICION);
        System.out.println("Procesados:      " + procesados);
        System.out.println("Perdidos:        " + perdidos);
    }

    static void consumir(boolean fallarEnElTercero) {
        while (posicionConfirmada < PARTICION.size()) {
            String mensaje = PARTICION.get(posicionConfirmada);
            posicionConfirmada++;                            // 1. confirma: "ya lo tengo"
            System.out.println("confirmada la posición " + posicionConfirmada + "; procesando " + mensaje);
            if (fallarEnElTercero && mensaje.equals("pago-3")) {
                throw new IllegalStateException("error al procesar " + mensaje);
            }
            procesados.add(mensaje);                         // 2. procesa
        }
    }
}
```

### Análisis

| Problema | Consecuencia |
|---|---|
| La posición se confirma antes de procesar | `pago-3` queda marcado como leído y nunca se procesó |
| Nada lo delata | No hay error en Kafka, el retraso es cero y las métricas están en verde. El pago, simplemente, falta |
| El fallo depende del momento exacto de la caída | No aparece en las pruebas; aparece en producción, de vez en cuando |

### Después

<!-- file: ejemplo/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Un consumidor que confirma la posición DESPUÉS de procesar el mensaje. */
public class Main {

    static final List<String> PARTICION = List.of("pago-1", "pago-2", "pago-3", "pago-4", "pago-5");
    static int posicionConfirmada = 0;
    static final List<String> procesados = new ArrayList<>();

    public static void main(String[] args) {
        try {
            consumir(true);                                  // se cae justo después de procesar pago-3
        } catch (IllegalStateException caida) {
            System.out.println("  >> el proceso se cayó: " + caida.getMessage());
        }
        System.out.println("  >> reinicio: retomo desde la posición confirmada " + posicionConfirmada);
        consumir(false);

        List<String> perdidos = new ArrayList<>(PARTICION);
        perdidos.removeAll(procesados);
        Set<String> vistos = new LinkedHashSet<>();
        Set<String> repetidos = new LinkedHashSet<>();
        for (String mensaje : procesados) {
            if (!vistos.add(mensaje)) {
                repetidos.add(mensaje);
            }
        }
        System.out.println();
        System.out.println("En la partición: " + PARTICION);
        System.out.println("Procesados:      " + procesados);
        System.out.println("Perdidos:        " + perdidos);
        System.out.println("Repetidos:       " + repetidos);
    }

    static void consumir(boolean caerseTrasElTercero) {
        int posicion = posicionConfirmada;
        while (posicion < PARTICION.size()) {
            String mensaje = PARTICION.get(posicion);
            procesados.add(mensaje);                         // 1. procesa
            System.out.println("procesado " + mensaje);
            if (caerseTrasElTercero && mensaje.equals("pago-3")) {
                throw new IllegalStateException("caída después de procesar " + mensaje + " y antes de confirmar");
            }
            posicion++;
            posicionConfirmada = posicion;                   // 2. confirma
            System.out.println("confirmada la posición " + posicionConfirmada);
        }
    }
}
```

Ya no se pierde nada. A cambio, `pago-3` se procesa dos veces. Ese es el trato de "al menos una vez", y por eso el consumidor tiene que ser idempotente: es el tema de la sesión siguiente.

Con el cliente real, el mismo orden se escribe así (compilado y ejecutado con `kafka-clients` 4.3.1):

```java
config.put("group.id", "conciliacion");
config.put("enable.auto.commit", "false");
config.put("auto.offset.reset", "earliest");

try (KafkaConsumer<String, String> consumidor = new KafkaConsumer<>(config)) {
    consumidor.subscribe(List.of("pagos"));
    while (activo) {
        ConsumerRecords<String, String> lote = consumidor.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, String> registro : lote) {
            procesar(registro);                              // 1. procesa
        }
        consumidor.commitSync();                             // 2. confirma el lote
    }
}
```

## Errores comunes

- Confirmar antes de procesar, o confirmar mientras otro hilo todavía procesa.
- Atrapar la excepción de un mensaje, registrarla y seguir: el mensaje se pierde igual que en el "antes".
- Más consumidores que particiones, esperando más velocidad.
- Procesamiento lento dentro del bucle y `max.poll.interval.ms` sin ajustar: reequilibrios en cadena.
- Un mismo `group.id` para dos aplicaciones distintas: se roban los mensajes entre ellas.
- Cambiar el `group.id` "para probar" y volver a procesar toda la historia en producción.
- No vigilar el retraso.
- Creer que `auto.offset.reset` permite releer.

## Taller

**Objetivo:** ver el reparto de particiones, provocar una pérdida y una repetición con Kafka real, y medir el retraso.

**Sobre tu servicio hexagonal:** si hiciste el [módulo de arquitectura hexagonal](../../../hexagonal/), el consumidor es un **adaptador de entrada**: recibe el mensaje, llama a un puerto de entrada y confirma la posición cuando el puerto retorna. Escríbelo con esa forma desde el principio, aunque el caso de uso de este taller sea trivial.

Necesitas el [entorno](../../entorno/) y tu productor de la sesión 02.

1. Crea un tema con tres particiones y prodúcele de forma continua, un mensaje por segundo con claves variadas.
2. Arranca un consumidor del grupo `taller`. Con `kafka-consumer-groups.sh --describe`, anota qué particiones tiene. Arranca un segundo y un tercero, y vuelve a mirar tras cada uno. Arranca un cuarto: ¿qué le toca?
3. Mata un consumidor sin apagado ordenado. Mide cuánto tarda el grupo en repartir sus particiones. Después apágalo de forma ordenada y compara. Explica la diferencia.
4. Arranca un consumidor de **otro** grupo. ¿Qué mensajes recibe? Repite con `auto.offset.reset` en `earliest` y en `latest`.
5. Escribe un consumidor en código que confirme a mano **después** de procesar. Añádele un fallo deliberado: tras procesar un mensaje concreto, el proceso termina de golpe antes de confirmar. Reinicia y demuestra la repetición.
6. Cambia el orden: confirma antes de procesar. Repite el fallo y demuestra la pérdida. Mira el retraso del grupo: ¿algo delata el problema?
7. Con confirmación automática, haz el procesamiento asíncrono (envía cada mensaje a un grupo de hilos y vuelve a llamar a `poll` de inmediato). Mata el proceso con trabajo pendiente. ¿Qué se perdió?
8. Haz que procesar un mensaje tarde más que `max.poll.interval.ms`. Describe qué pasa con el grupo y con ese mensaje. Arréglalo de dos maneras distintas y compara.
9. Detén los consumidores cinco minutos con el productor activo. Mide el retraso. Arráncalos y registra cuánto tardan en ponerse al día con uno, dos y tres consumidores.
10. Mueve la posición del grupo al principio del tema con la herramienta de consola. ¿Qué condición exige la herramienta para dejarte hacerlo? ¿Cuándo usarías esto en producción y qué tendría que cumplir el consumidor?

**Criterios de aceptación**

- La pérdida y la repetición están demostradas con conteos, no descritas.
- El paso 8 tiene dos soluciones, con el costo de cada una.
- `ANALISIS.md` responde: ¿qué garantía de entrega ofrece tu consumidor final y por qué?

**Entrega:** rama `taller/kafka-03`, con el código en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

La alerta que más incidentes detecta en sistemas con Kafka es la de retraso del grupo. Y el error más caro es el silencioso: un consumidor que atrapa excepciones y sigue, perdiendo mensajes con todas las métricas en verde.

## Preguntas senior

1. ¿Qué es un grupo de consumidores y cómo limita el paralelismo el número de particiones?
2. Explica "como mucho una vez" y "al menos una vez" a partir del momento de la confirmación.
3. ¿Por qué la confirmación automática puede perder mensajes?
4. ¿Qué es un reequilibrio, qué lo provoca y por qué son un problema cuando ocurren en cadena?
5. ¿Qué te dice el retraso y qué no te dice?
6. Tienes un procesamiento que tarda dos minutos por mensaje. ¿Cómo lo diseñas?
