# 04 — Cuando todo falla: repeticiones, desorden y compensaciones rotas

## Idea

El correo postal. Una carta puede llegar dos veces porque la reenviaron por si acaso. Dos cartas pueden llegar en orden contrario al que se enviaron: primero la que dice "olvida lo anterior" y luego "lo anterior". Y la carta en la que cancelas un pedido puede perderse la primera vez. Quien trabaja por correo no supone que nada de eso es raro: numera sus cartas, guarda las recibidas y reenvía hasta tener acuse.

## Lo esencial

Las sesiones anteriores supusieron una mensajería perfecta. La real entrega **al menos una vez**, **sin orden garantizado entre temas** y con participantes que **a veces no responden**. Una saga tiene que ser correcta con todo eso.

### Cuatro fallos y su defensa

| Fallo | Qué pasa | Defensa |
|---|---|---|
| **Mensaje repetido** | La orden "cobrar" llega dos veces | Participantes idempotentes: recuerdan el identificador del mensaje |
| **Mensaje fuera de orden** | La cancelación llega antes que la reserva que cancela | Lápida: al recibir la cancelación de algo que no existe, se anota "esta saga está cancelada" y la reserva tardía se rechaza |
| **Respuesta que no llega** | El orquestador espera para siempre | Tiempo límite, reintento de la orden (que debe ser idempotente) y, al agotarse, compensación |
| **Compensación que falla** | El sistema queda inconsistente y nadie lo arregla | Reintentar hasta lograrlo; si no se logra, escalar a una persona con toda la información |

### Las compensaciones no pueden rendirse

Una acción hacia delante puede fallar: para eso está compensar. Una compensación no tiene a quién recurrir. Por eso debe:

- Ser **idempotente** (se va a reintentar).
- **Reintentarse** con espera hasta que funcione.
- Tener un **destino final** cuando no funciona: una cola de casos para revisión humana, con alerta. "Registrar el error y seguir" no es un destino.

### Falta de aislamiento

Una transacción oculta sus pasos intermedios; una saga no. Mientras corre, otros leen y escriben los mismos datos.

| Anomalía | Ejemplo |
|---|---|
| Lectura sucia | Un informe cuenta como vendidas las unidades de un pedido que acabará cancelado |
| Actualización perdida | Dos sagas reservan la última unidad porque ambas leyeron "queda una" |
| Lectura no repetible | La saga lee un precio al principio y, al cobrar, el precio ya cambió |

| Contramedida | En qué consiste |
|---|---|
| Bloqueo semántico | Marcar el registro con un estado provisional (`PENDIENTE`, `RESERVADO`) que los demás deben respetar |
| Operaciones conmutativas | Diseñar las operaciones para que dé igual el orden: restar y sumar existencias en vez de escribir un valor |
| Releer antes de escribir | Comprobar, con un número de versión, que el dato no cambió desde que se leyó |
| Reordenar los pasos | Dejar las escrituras delicadas para después del paso sin retorno |

## Ejemplo

Java puro. Ejecuta cada uno con `java Main.java`.

### Antes

<!-- file: ejemplo/antes/Main.java -->
```java
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Participantes que suponen que cada mensaje llega una vez, en orden, y que cancelar nunca falla. */
public class Main {

    record Mensaje(String id, String tipo, String saga) {}

    static final Map<String, Integer> vecesCobrado = new LinkedHashMap<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static int llamadasAlServicioDeHoteles = 0;

    public static void main(String[] args) {
        // 1. Repetición: el broker reentrega la orden de cobro
        Mensaje cobrar = new Mensaje("m-1", "cobrar", "viaje-1");
        recibir(cobrar);
        recibir(cobrar);

        // 2. Desorden: la cancelación de viaje-2 llega antes que su reserva
        recibir(new Mensaje("m-3", "cancelar-hotel", "viaje-2"));
        recibir(new Mensaje("m-2", "reservar-hotel", "viaje-2"));

        // 3. Compensación que falla: el servicio de hoteles está inestable
        hoteles.add("viaje-3");
        try {
            cancelarEnServicioInestable("viaje-3");
        } catch (IllegalStateException error) {
            System.out.println("no se pudo compensar viaje-3: " + error.getMessage() + " (se registra y se sigue)");
        }

        System.out.println();
        System.out.println("Veces que se cobró viaje-1: " + vecesCobrado.get("viaje-1"));
        System.out.println("Hoteles reservados:         " + hoteles);
    }

    static void recibir(Mensaje mensaje) {
        switch (mensaje.tipo()) {
            case "cobrar" -> vecesCobrado.merge(mensaje.saga(), 1, Integer::sum);
            case "reservar-hotel" -> hoteles.add(mensaje.saga());
            case "cancelar-hotel" -> hoteles.remove(mensaje.saga());    // si aún no existe, no hace nada
            default -> throw new IllegalArgumentException(mensaje.tipo());
        }
        System.out.println("procesado " + mensaje.id() + " " + mensaje.tipo() + " de " + mensaje.saga());
    }

    /** Falla las dos primeras veces que se llama. */
    static void cancelarEnServicioInestable(String saga) {
        llamadasAlServicioDeHoteles++;
        if (llamadasAlServicioDeHoteles <= 2) {
            throw new IllegalStateException("el servicio de hoteles no responde");
        }
        hoteles.remove(saga);
    }
}
```

### Análisis

| Lo que queda | Por qué |
|---|---|
| `viaje-1` cobrado dos veces | El participante no reconoce un mensaje repetido |
| `viaje-2` con hotel reservado aunque la saga se canceló | La cancelación llegó primero, no encontró nada que cancelar y se dio por satisfecha. La reserva llegó después y nadie la detuvo |
| `viaje-3` con hotel reservado | La compensación falló una vez y nadie volvió a intentarla |

Ninguno de los tres produce una excepción visible ni una alerta. Son datos incorrectos que alguien descubrirá semanas después.

### Después

<!-- file: ejemplo/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Participantes idempotentes, con lápidas para el desorden y compensaciones que insisten. */
public class Main {

    record Mensaje(String id, String tipo, String saga) {}

    static final Map<String, Integer> vecesCobrado = new LinkedHashMap<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static final Set<String> mensajesProcesados = new LinkedHashSet<>();     // misma transacción que el efecto
    static final Set<String> sagasCanceladas = new LinkedHashSet<>();        // lápidas
    static final List<String> casosParaRevision = new ArrayList<>();
    static int llamadasAlServicioDeHoteles = 0;

    public static void main(String[] args) {
        Mensaje cobrar = new Mensaje("m-1", "cobrar", "viaje-1");
        recibir(cobrar);
        recibir(cobrar);

        recibir(new Mensaje("m-3", "cancelar-hotel", "viaje-2"));
        recibir(new Mensaje("m-2", "reservar-hotel", "viaje-2"));

        hoteles.add("viaje-3");
        compensarInsistiendo("viaje-3", 5);

        System.out.println();
        System.out.println("Veces que se cobró viaje-1: " + vecesCobrado.get("viaje-1"));
        System.out.println("Hoteles reservados:         " + hoteles);
        System.out.println("Casos para revisión humana: " + casosParaRevision);
    }

    static void recibir(Mensaje mensaje) {
        if (!mensajesProcesados.add(mensaje.id())) {
            System.out.println("repetido " + mensaje.id() + ": se ignora");
            return;
        }
        switch (mensaje.tipo()) {
            case "cobrar" -> vecesCobrado.merge(mensaje.saga(), 1, Integer::sum);
            case "reservar-hotel" -> {
                if (sagasCanceladas.contains(mensaje.saga())) {
                    System.out.println(mensaje.id() + ": " + mensaje.saga() + " ya fue cancelada; no se reserva");
                    return;
                }
                hoteles.add(mensaje.saga());
            }
            case "cancelar-hotel" -> {
                sagasCanceladas.add(mensaje.saga());            // queda anotado aunque no haya nada que cancelar
                hoteles.remove(mensaje.saga());
            }
            default -> throw new IllegalArgumentException(mensaje.tipo());
        }
        System.out.println("procesado " + mensaje.id() + " " + mensaje.tipo() + " de " + mensaje.saga());
    }

    static void compensarInsistiendo(String saga, int intentosMaximos) {
        for (int intento = 1; intento <= intentosMaximos; intento++) {
            try {
                cancelarEnServicioInestable(saga);              // idempotente: repetirla no hace daño
                System.out.println("compensada " + saga + " en el intento " + intento);
                return;
            } catch (IllegalStateException error) {
                System.out.println("intento " + intento + " de compensar " + saga + " falló: " + error.getMessage());
                // en un sistema real: esperar cada vez más antes del siguiente intento
            }
        }
        casosParaRevision.add(saga);                            // con alerta: alguien tiene que mirarlo
        System.out.println(saga + " no se pudo compensar: pasa a revisión humana");
    }

    static void cancelarEnServicioInestable(String saga) {
        llamadasAlServicioDeHoteles++;
        if (llamadasAlServicioDeHoteles <= 2) {
            throw new IllegalStateException("el servicio de hoteles no responde");
        }
        hoteles.remove(saga);
    }
}
```

- El cobro repetido se descarta.
- La reserva tardía de una saga ya cancelada se rechaza gracias a la lápida.
- La compensación se logra al tercer intento. Si no se lograra, quedaría en una lista que alguien revisa.

## Errores comunes

- Probar la saga solo con mensajería perfecta.
- Idempotencia en las acciones hacia delante y no en las compensaciones.
- Cancelar "lo que haya" sin dejar constancia de que la saga se canceló.
- Registrar el fallo de una compensación y continuar.
- Reintentos sin espera ni límite, que tumban al servicio que estaba intentando recuperarse.
- Tiempos límite más cortos que el tiempo normal de respuesta: compensar sagas que iban bien.
- Ignorar la falta de aislamiento hasta que dos clientes compran la última unidad.
- Lápidas y registros de mensajes que nunca se limpian.

## Taller

**Objetivo:** someter tu saga a una mensajería hostil y dejarla correcta.

**Sobre tu servicio hexagonal:** si hiciste el [módulo de arquitectura hexagonal](../../../hexagonal/), la mensajería hostil es **un adaptador más**: sustituye al de mensajería por uno que repite, desordena y pierde. Si para sobrevivir tienes que cambiar el dominio y no solo la aplicación, anota qué regla faltaba.

Sobre tu orquestador de la sesión 02 (o la coreografía de la 03), en memoria.

1. Escribe un **broker hostil**: una envoltura del bus que, de forma determinista y configurable, duplique mensajes, intercambie el orden de dos consecutivos, retrase uno varios ciclos o lo pierda. Determinista quiere decir que el mismo guion produce siempre el mismo resultado: sin azar.
2. Ejecuta los cuatro caminos de tu saga con duplicación activa. Anota cada inconsistencia.
3. Haz idempotentes a todos los participantes y al orquestador. Repite: cero inconsistencias.
4. Activa el desorden. Encuentra el caso en que una compensación llega antes que la acción que compensa. Implementa la lápida y demuéstrala.
5. Activa la pérdida de una respuesta. Implementa tiempo límite con reintento en el orquestador. ¿Qué pasa si la respuesta "perdida" en realidad solo estaba retrasada y llega después del reintento?
6. Haz que una compensación falle tres veces seguidas y luego funcione; después, que falle siempre. Implementa reintentos con espera creciente y la cola de revisión humana. ¿Qué información necesita la persona que revisa?
7. **Aislamiento.** Lanza dos sagas a la vez que quieren la última unidad de un producto. Reproduce la doble venta. Arréglala de dos formas distintas (por ejemplo, bloqueo semántico y comprobación de versión) y compara.
8. Reproduce una lectura sucia: un informe de ventas que se ejecuta a mitad de una saga que luego se cancela. Corrige el informe.
9. Escribe una prueba que ejecute cada camino de la saga con **todas** las combinaciones de un fallo en cada paso, y que compruebe una invariante al final (por ejemplo: "todo pedido confirmado está cobrado exactamente una vez y tiene inventario reservado; todo pedido cancelado no tiene ni cobro ni reserva").
10. ¿Cuánto tiempo conservas las lápidas y los identificadores procesados? Justifica.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| La invariante se cumple en todas las combinaciones de fallo | La prueba del paso 9 |
| Ninguna compensación fallida se pierde | Paso 6: o se logra o queda en revisión |
| No hay doble venta | Paso 7, ejecutado cien veces |
| El broker hostil es determinista | El mismo guion da el mismo resultado |

**Entrega:** rama `taller/sagas-04`, con el código en `taller/solucion/` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Esta sesión es la diferencia entre una saga de demostración y una que aguanta producción. Los equipos con experiencia tienen la prueba del paso 9 corriendo en integración continua, y un tablero con las sagas atascadas y los casos en revisión humana. El número de ese tablero es una métrica de salud del sistema.

## Preguntas senior

1. ¿Qué pasa cuando una compensación llega antes que la acción que compensa, y cómo lo resuelves?
2. ¿Por qué las compensaciones deben ser idempotentes y reintentables?
3. ¿Qué haces cuando una compensación no puede completarse?
4. Explica la falta de aislamiento de una saga y dos contramedidas.
5. ¿Cómo eliges un tiempo límite y qué riesgo tiene equivocarse hacia cada lado?
6. ¿Cómo pruebas que una saga es correcta ante fallos?
