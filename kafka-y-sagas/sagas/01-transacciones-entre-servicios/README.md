# 01 — Transacciones entre servicios: el problema y la idea de saga

## Idea

Organizar un viaje por tu cuenta. Compras el vuelo en una aerolínea, reservas el hotel en otra página y alquilas el carro en una tercera. No existe un botón que haga las tres cosas "o todas o ninguna". Si el hotel resulta estar lleno, nadie deshace el vuelo por ti: tienes que llamar a la aerolínea y cancelar, y quizá te cobren una penalidad. Eso es una saga: una secuencia de pasos independientes, donde cada uno tiene su manera de echarse atrás.

## Lo esencial

### Por qué no hay transacción

Dentro de una base de datos, una transacción garantiza todo o nada. Cuando una operación de negocio cruza varios servicios, cada uno con su base, esa garantía desaparece. Existe un protocolo para coordinar varias bases (confirmación en dos fases), pero casi nunca se usa entre microservicios: exige que todos los participantes lo soporten, mantiene bloqueos mientras todos responden y, si el coordinador cae, deja a los demás esperando. Los brokers de mensajes y las API HTTP, además, no participan.

### La saga

Una **saga** es una secuencia de transacciones locales. Cada servicio confirma la suya en su propia base. Si un paso falla, se ejecutan **compensaciones** de los pasos anteriores, en orden inverso.

Tres ideas que cambian respecto a una transacción:

| Transacción | Saga |
|---|---|
| Deshacer es automático y no deja rastro | Compensar es **otra operación de negocio**, escrita por ti, que deja rastro |
| Nadie ve los estados intermedios | Los estados intermedios **se ven** desde fuera |
| Todo o nada, al instante | Todo o nada, **con el tiempo** |

### Compensar no es deshacer

Deshacer borra. Compensar corrige con una acción nueva: un cobro no se borra, se reembolsa; un correo no se desenvía, se manda otro pidiendo disculpas. Algunas acciones no tienen compensación posible (el paquete ya salió). Eso decide el orden de los pasos:

| Tipo de paso | Qué es | Dónde va |
|---|---|---|
| Compensable | Se puede echar atrás | Al principio |
| Pivote | El punto sin retorno: si pasa, la saga se termina sí o sí | En medio |
| Reintentable | No puede fallar por negocio, solo por técnica; se reintenta hasta lograrlo | Al final |

Regla práctica: **lo que no se puede compensar, déjalo para el final.**

## Ejemplo

Java puro. Ejecuta cada uno con `java Main.java`.

### Antes

<!-- file: ejemplo/antes/Main.java -->
```java
import java.util.LinkedHashSet;
import java.util.Set;

/** Una operación de tres servicios escrita como si fuera una transacción local. */
public class Main {

    // Cada conjunto representa la base de datos de un servicio distinto
    static final Set<String> vuelos = new LinkedHashSet<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static final Set<String> cobros = new LinkedHashSet<>();

    public static void main(String[] args) {
        reservar("viaje-1", 800);
        reservar("viaje-2", 5000);                 // la tarjeta no tiene cupo

        System.out.println();
        System.out.println("Vuelos reservados:  " + vuelos);
        System.out.println("Hoteles reservados: " + hoteles);
        System.out.println("Cobros:             " + cobros);
    }

    static void reservar(String viaje, int valor) {
        System.out.println("Reservando " + viaje);
        try {
            reservarVuelo(viaje);
            reservarHotel(viaje);
            cobrar(viaje, valor);
            System.out.println("  " + viaje + " confirmado");
        } catch (IllegalStateException error) {
            System.out.println("  " + viaje + " falló: " + error.getMessage());
        }
    }

    static void reservarVuelo(String viaje) {
        vuelos.add(viaje);
        System.out.println("  vuelo reservado");
    }

    static void reservarHotel(String viaje) {
        hoteles.add(viaje);
        System.out.println("  hotel reservado");
    }

    static void cobrar(String viaje, int valor) {
        if (valor > 1000) {
            throw new IllegalStateException("pago rechazado");
        }
        cobros.add(viaje);
        System.out.println("  pago cobrado");
    }
}
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Cada paso se confirma en su servicio antes de saber si el siguiente saldrá bien | `viaje-2` queda con vuelo y hotel reservados y sin pagar |
| El `catch` solo informa | Nadie libera el asiento ni la habitación. Se quedan ocupados hasta que alguien lo note |
| El código parece una transacción | Quien lo lee asume un "todo o nada" que no existe |

### Después

<!-- file: ejemplo/despues/Main.java -->
```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** La misma operación como saga: cada paso trae su compensación. */
public class Main {

    record Paso(String nombre, Runnable accion, Runnable compensacion) {}

    static final Set<String> vuelos = new LinkedHashSet<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static final Set<String> cobros = new LinkedHashSet<>();

    public static void main(String[] args) {
        reservar("viaje-1", 800);
        reservar("viaje-2", 5000);

        System.out.println();
        System.out.println("Vuelos reservados:  " + vuelos);
        System.out.println("Hoteles reservados: " + hoteles);
        System.out.println("Cobros:             " + cobros);
    }

    static void reservar(String viaje, int valor) {
        System.out.println("Reservando " + viaje);
        List<Paso> pasos = List.of(
                new Paso("vuelo", () -> reservarVuelo(viaje), () -> cancelarVuelo(viaje)),
                new Paso("hotel", () -> reservarHotel(viaje), () -> cancelarHotel(viaje)),
                new Paso("pago", () -> cobrar(viaje, valor), () -> reembolsar(viaje)));

        Deque<Paso> completados = new ArrayDeque<>();
        try {
            for (Paso paso : pasos) {
                paso.accion().run();
                completados.push(paso);
            }
            System.out.println("  " + viaje + " confirmado");
        } catch (IllegalStateException error) {
            System.out.println("  " + viaje + " falló: " + error.getMessage() + ". Compensando en orden inverso:");
            while (!completados.isEmpty()) {
                completados.pop().compensacion().run();
            }
            System.out.println("  " + viaje + " cancelado");
        }
    }

    static void reservarVuelo(String viaje) {
        vuelos.add(viaje);
        System.out.println("  vuelo reservado");
    }

    static void cancelarVuelo(String viaje) {
        vuelos.remove(viaje);
        System.out.println("  vuelo cancelado");
    }

    static void reservarHotel(String viaje) {
        hoteles.add(viaje);
        System.out.println("  hotel reservado");
    }

    static void cancelarHotel(String viaje) {
        hoteles.remove(viaje);
        System.out.println("  hotel cancelado");
    }

    static void cobrar(String viaje, int valor) {
        if (valor > 1000) {
            throw new IllegalStateException("pago rechazado");
        }
        cobros.add(viaje);
        System.out.println("  pago cobrado");
    }

    static void reembolsar(String viaje) {
        cobros.remove(viaje);
        System.out.println("  pago reembolsado");
    }
}
```

`viaje-2` termina sin vuelo ni hotel: el sistema vuelve a quedar consistente. Fíjate en lo que este ejemplo todavía **no** resuelve, porque es el resto de la ruta:

- Todo ocurre en un solo proceso y en memoria. Si ese proceso cae a mitad, la lista de compensaciones desaparece (sesión 02).
- Las llamadas son directas. En un sistema real son mensajes que pueden repetirse o llegar tarde (sesión 04).
- Una compensación también puede fallar (sesión 04).

## Errores comunes

- Programar una operación entre servicios como si fuera transaccional.
- Creer que compensar es deshacer.
- No diseñar la compensación de un paso hasta que hace falta en producción.
- Poner al principio el paso que no se puede echar atrás.
- Ignorar que, mientras la saga corre, otros ven datos a medias.
- Usar una saga dentro de un solo servicio con una sola base, donde bastaba una transacción.

## Taller

**Objetivo:** diseñar en papel la saga de compra de la tienda y construir su primera versión en memoria.

El dominio es el de la sesión 05 de Kafka: **Pedidos**, **Pagos**, **Inventario** y **Envíos**.

1. Escribe los pasos de "confirmar una compra" y decide su orden. Para cada uno: qué servicio lo hace, cuál es su compensación y si es compensable, pivote o reintentable.
2. Justifica el orden. ¿Reservas inventario antes o después de cobrar? Escribe qué pasa en cada alternativa cuando falla el paso siguiente y cuál le duele menos al negocio.
3. Encuentra un paso que no tenga compensación real. ¿Dónde lo pusiste? ¿Qué harías si falla después de él?
4. Para cada compensación, describe qué ve el cliente. ¿"Reembolsar" es instantáneo? ¿Qué pasa con el correo de confirmación que ya recibió?
5. Lista los estados intermedios que un tercero podría ver (por ejemplo, existencias reservadas de un pedido que acabará cancelado). ¿Cuál es peligroso?
6. Implementa la saga en Java puro, en memoria, con el patrón del ejemplo. Debe cubrir: compra exitosa, pago rechazado, sin existencias y fallo en el envío.
7. Añade un quinto paso (sumar puntos de fidelidad). ¿Cuántos sitios del código tocaste?
8. Haz que una compensación lance una excepción. ¿En qué estado queda el sistema? No lo arregles todavía: descríbelo.
9. Responde: ¿qué parte de esta operación podría seguir siendo una transacción local y qué ganarías?

**Criterios de aceptación**

- Hay una tabla con cada paso, su servicio, su compensación y su tipo.
- El orden está justificado con consecuencias, no con preferencias.
- Los cuatro escenarios del paso 6 dejan el sistema consistente y lo demuestras imprimiendo el estado.

**Entrega:** rama `taller/sagas-01`, con el código en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

La conversación más valiosa sobre una saga no es técnica: es con la gente del negocio. "¿Qué hacemos si ya cobramos y no hay existencias?" es una pregunta de negocio, y la respuesta (reembolsar, ofrecer espera, sustituir) define las compensaciones. Un equipo que diseña sagas sin esa conversación termina inventando reglas de negocio en el código.

## Preguntas senior

1. ¿Por qué no se usa confirmación en dos fases entre microservicios?
2. Diferencia entre deshacer y compensar. Da un ejemplo en el que compensar deja rastro visible para el cliente.
3. ¿Qué son los pasos compensables, pivote y reintentables, y cómo deciden el orden?
4. ¿Qué propiedad de las transacciones se pierde con una saga y qué problemas trae?
5. ¿Cuándo no hace falta una saga?
