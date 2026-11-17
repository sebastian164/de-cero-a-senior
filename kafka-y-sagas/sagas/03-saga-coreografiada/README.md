# 03 — Saga coreografiada

## Idea

Un grupo de baile sin director. Cada bailarín conoce su parte y sabe a qué gesto del compañero debe responder: cuando ella gira, él da el paso. Nadie da órdenes y, si entra un bailarín nuevo que solo reacciona a un gesto concreto, los demás ni se enteran. Funciona de maravilla con cinco personas. Con cuarenta, cuando algo sale mal, nadie sabe decir quién falló ni en qué compás.

## Lo esencial

En una saga **coreografiada** no hay coordinador. Cada servicio:

1. Escucha los **eventos** que le interesan.
2. Hace su transacción local.
3. Publica un evento con lo que ocurrió.

La compensación también es una reacción: Hoteles escucha `PagoRechazado` y cancela; Vuelos escucha `HotelCancelado` y cancela.

### Orquestación frente a coreografía

| | Orquestada | Coreografiada |
|---|---|---|
| Quién conoce el flujo completo | El orquestador | Nadie: está repartido |
| Mensajes | Órdenes y respuestas | Eventos |
| Acoplamiento | Los participantes no se conocen; el orquestador conoce a todos | Cada servicio conoce los **eventos** de otros |
| Añadir un paso que solo reacciona | Hay que tocar el orquestador | No se toca a nadie |
| Cambiar el orden de los pasos | Un lugar | Varios servicios |
| "¿En qué va el pedido 42?" | Una consulta | Hay que reconstruirlo siguiendo los eventos |
| Riesgo típico | Orquestador que acumula lógica | Ciclos de eventos y flujos que nadie entiende completos |
| Encaja con | Flujos largos, con ramas y tiempos límite | Flujos cortos y lineales; reacciones laterales |

No es una elección de todo o nada. Lo habitual es orquestar el flujo principal y dejar por coreografía las reacciones laterales (notificar, sumar puntos, actualizar un panel).

### Lo que la coreografía exige

- **Un identificador de correlación** en todos los eventos. Sin él, no se puede reconstruir una saga.
- **Trazabilidad:** alguna forma de ver, para una correlación, la cadena de eventos.
- **Detección de sagas atascadas:** como nadie espera una respuesta, si un servicio no reacciona, nada falla. Simplemente no pasa nada.

## Ejemplo

Java puro. Ejecuta cada uno con `java Main.java`.

### Antes

<!-- file: ejemplo/antes/Main.java -->
```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Una cadena de llamadas directas: cada servicio conoce al siguiente y deshace lo suyo si el otro falla. */
public class Main {

    static final class Pagos {
        final Set<String> cobros = new LinkedHashSet<>();
        final List<String> correos = new ArrayList<>();

        void cobrar(String viaje, int valor) {
            if (valor > 1000) {
                throw new IllegalStateException("pago rechazado");
            }
            cobros.add(viaje);
            correos.add("confirmación de " + viaje);       // "ya que estamos aquí": Pagos enviando correos
        }
    }

    static final class Hoteles {
        final Set<String> reservas = new LinkedHashSet<>();
        final Pagos pagos;

        Hoteles(Pagos pagos) {
            this.pagos = pagos;
        }

        void reservar(String viaje, int valor) {
            reservas.add(viaje);
            try {
                pagos.cobrar(viaje, valor);                // Hoteles llama a Pagos
            } catch (IllegalStateException error) {
                reservas.remove(viaje);
                throw error;
            }
        }
    }

    static final class Vuelos {
        final Set<String> reservas = new LinkedHashSet<>();
        final Hoteles hoteles;

        Vuelos(Hoteles hoteles) {
            this.hoteles = hoteles;
        }

        void reservar(String viaje, int valor) {
            reservas.add(viaje);
            try {
                hoteles.reservar(viaje, valor);            // Vuelos llama a Hoteles
            } catch (IllegalStateException error) {
                reservas.remove(viaje);
                throw error;
            }
        }
    }

    public static void main(String[] args) {
        Pagos pagos = new Pagos();
        Hoteles hoteles = new Hoteles(pagos);
        Vuelos vuelos = new Vuelos(hoteles);

        vuelos.reservar("viaje-1", 800);
        try {
            vuelos.reservar("viaje-2", 5000);
        } catch (IllegalStateException error) {
            System.out.println("viaje-2: " + error.getMessage());
        }

        System.out.println("Vuelos:  " + vuelos.reservas);
        System.out.println("Hoteles: " + hoteles.reservas);
        System.out.println("Cobros:  " + pagos.cobros);
        System.out.println("Correos: " + pagos.correos);
    }
}
```

### Análisis

El resultado es correcto. El diseño no.

| Problema | Consecuencia |
|---|---|
| Vuelos depende de Hoteles, y Hoteles de Pagos | Para desplegar o probar Vuelos hacen falta los otros dos. Si Pagos está caído, no se puede reservar un vuelo |
| Cada servicio espera a toda la cadena | La latencia se suma y un servicio lento frena a todos |
| Enviar el correo se metió en Pagos | Era el único sitio donde "se sabía" que todo terminó. Cada necesidad nueva obliga a tocar un servicio que no tiene que ver |
| La compensación depende de que la excepción suba | Si el proceso de Hoteles cae mientras espera a Pagos, el vuelo se queda reservado |
| El orden de los pasos está repartido en tres clases | Cambiarlo es reescribir las tres |

### Después

<!-- file: ejemplo/despues/Main.java -->
```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Coreografía: nadie llama a nadie. Cada servicio reacciona a eventos y publica los suyos. */
public class Main {

    record Evento(String tipo, String viaje, int valor) {}

    // El broker
    static final Map<String, List<Consumer<Evento>>> suscriptores = new LinkedHashMap<>();
    static final Deque<Evento> cola = new ArrayDeque<>();
    static final List<String> traza = new ArrayList<>();

    // La base de datos de cada servicio
    static final Set<String> vuelos = new LinkedHashSet<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static final Set<String> cobros = new LinkedHashSet<>();
    static final Map<String, String> estadoDelViaje = new LinkedHashMap<>();
    static final List<String> correos = new ArrayList<>();

    public static void main(String[] args) {
        // Vuelos
        suscribir("ViajeSolicitado", e -> {
            vuelos.add(e.viaje());
            publicar("VueloReservado", e);
        });
        suscribir("HotelCancelado", e -> {
            vuelos.remove(e.viaje());
            publicar("VueloCancelado", e);
        });

        // Hoteles
        suscribir("VueloReservado", e -> {
            hoteles.add(e.viaje());
            publicar("HotelReservado", e);
        });
        suscribir("PagoRechazado", e -> {
            hoteles.remove(e.viaje());
            publicar("HotelCancelado", e);
        });

        // Pagos
        suscribir("HotelReservado", e -> {
            if (e.valor() > 1000) {
                publicar("PagoRechazado", e);
            } else {
                cobros.add(e.viaje());
                publicar("PagoAprobado", e);
            }
        });

        // Reservas: quien inició el viaje registra cómo terminó
        suscribir("PagoAprobado", e -> estadoDelViaje.put(e.viaje(), "CONFIRMADO"));
        suscribir("VueloCancelado", e -> estadoDelViaje.put(e.viaje(), "CANCELADO"));

        // Notificaciones: un servicio nuevo, añadido sin tocar ninguno de los anteriores
        suscribir("PagoAprobado", e -> correos.add("confirmación de " + e.viaje()));
        suscribir("VueloCancelado", e -> correos.add("disculpas por " + e.viaje()));

        publicar("ViajeSolicitado", new Evento("", "viaje-1", 800));
        despachar();
        publicar("ViajeSolicitado", new Evento("", "viaje-2", 5000));
        despachar();

        System.out.println("Vuelos:  " + vuelos);
        System.out.println("Hoteles: " + hoteles);
        System.out.println("Cobros:  " + cobros);
        System.out.println("Estados: " + estadoDelViaje);
        System.out.println("Correos: " + correos);
        System.out.println();
        System.out.println("Lo único que cuenta la historia completa es la traza de eventos:");
        traza.forEach(linea -> System.out.println("  " + linea));
    }

    static void suscribir(String tipo, Consumer<Evento> reaccion) {
        suscriptores.computeIfAbsent(tipo, t -> new ArrayList<>()).add(reaccion);
    }

    static void publicar(String tipo, Evento origen) {
        cola.add(new Evento(tipo, origen.viaje(), origen.valor()));
    }

    static void despachar() {
        while (!cola.isEmpty()) {
            Evento evento = cola.poll();
            traza.add(evento.viaje() + ": " + evento.tipo());
            for (Consumer<Evento> reaccion : suscriptores.getOrDefault(evento.tipo(), List.of())) {
                reaccion.accept(evento);
            }
        }
    }
}
```

- Ningún servicio conoce a otro: solo conoce nombres de eventos.
- Notificaciones se añadió con dos suscripciones y cero cambios en los demás.
- La compensación es una cadena de reacciones: `PagoRechazado` → `HotelCancelado` → `VueloCancelado`.
- El costo está a la vista en la última parte de la salida: para saber qué le pasó a `viaje-2` hay que leer la traza. No existe un sitio que diga "el flujo es este".

## Errores comunes

- Coreografiar un flujo de ocho pasos con ramas y descubrir que nadie puede dibujarlo.
- Eventos que en realidad son órdenes para un destinatario concreto.
- Ciclos: A reacciona a B, B a C y C a A.
- Sin identificador de correlación.
- Sin detección de sagas atascadas: un servicio deja de reaccionar y no hay error en ninguna parte.
- Servicios que reaccionan a eventos internos de otro (los que nunca debieron ser públicos).
- Suponer el orden de llegada entre eventos de distintos servicios.

## Taller

**Objetivo:** reescribir la saga de la tienda como coreografía y comparar, con hechos, las dos versiones.

**Sobre tu servicio hexagonal:** si hiciste el [módulo de arquitectura hexagonal](../../../hexagonal/), cada reacción a un evento es un consumidor, es decir, un adaptador de entrada que llama a un puerto de entrada; cada evento emitido sale por el puerto de eventos y la bandeja de salida. Ningún servicio importa clases de otro.

1. A partir de tu orquestador de la sesión 02, define los eventos que publicaría cada servicio y a cuáles reaccionaría. Dibuja el grafo: servicios como nodos, eventos como flechas.
2. Impleméntalo en Java puro con un bus en memoria. Cubre los mismos cuatro caminos.
3. Compara con la versión orquestada: ¿en cuántos archivos está repartido el flujo?, ¿cuántos hay que abrir para explicar el camino "pago rechazado"?
4. Añade el servicio de puntos de fidelidad. ¿Cuántos servicios existentes tocaste? Compáralo con la sesión 02.
5. Cambia el orden de dos pasos (cobrar antes de reservar inventario). ¿Cuántos servicios tocaste? Compáralo con la sesión 02.
6. Haz que un servicio "se caiga": no reacciona a un evento. ¿Qué error aparece? Implementa la forma de detectar la saga atascada. ¿Quién es el dueño de esa detección si no hay orquestador?
7. Introduce un ciclo por accidente (un servicio que reacciona a un evento y publica otro que acaba provocando el primero). Observa el resultado y diseña una defensa.
8. Escribe una función que, dado un identificador de correlación, reconstruya el estado de la saga a partir de la traza. ¿Qué tan fiable es?
9. Diseña la versión híbrida: qué orquestarías y qué dejarías por coreografía en la tienda. Justifica cada decisión.

**Criterios de aceptación**

- Los cuatro caminos dejan el sistema consistente.
- La comparación de los pasos 3 a 5 tiene números.
- El paso 6 propone un mecanismo concreto con dueño.
- La decisión del paso 9 nombra los costos de lo que elegiste.

**Entrega:** rama `taller/sagas-03`, con el código en `taller/solucion/`, el grafo y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

La pregunta "¿orquestación o coreografía?" suele plantearse como una guerra de preferencias. La respuesta útil sale de dos datos: cuántos pasos y ramas tiene el flujo, y quién tiene que poder responder en qué va cada caso. Soporte, auditoría y negocio rara vez aceptan "hay que reconstruirlo leyendo los eventos".

## Preguntas senior

1. Compara orquestación y coreografía en acoplamiento, visibilidad y facilidad de cambio.
2. ¿Dónde vive la lógica de compensación en una coreografía?
3. ¿Cómo detectas una saga atascada cuando no hay coordinador?
4. ¿Qué es un ciclo de eventos y cómo lo previenes?
5. Da un caso en el que combinarías los dos estilos y explica la frontera.
