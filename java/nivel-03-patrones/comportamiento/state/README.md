# State

Familia: comportamiento. Frecuencia en Java empresarial: media-alta (todo lo que tiene "estado" de negocio).

## El problema

Un objeto se comporta distinto según el estado en que está, y pasa de un estado a otro siguiendo reglas. Lo habitual es un campo `estado` y un `switch` en cada método. Con cinco estados y seis operaciones son treinta combinaciones repartidas, y las transiciones válidas no están escritas en ningún sitio: hay que deducirlas.

## Cómo reconocer que lo necesitas

- El mismo `switch (estado)` aparece en varios métodos.
- Hay errores del tipo "se pudo cancelar un pedido ya entregado".
- Nadie puede responder rápido "¿desde qué estados se puede llegar a X?".
- Agregar un estado obliga a revisar todos los métodos.

## Antes

```java
void pagar()    { if (estado.equals("CREADO")) estado = "PAGADO"; else throw new IllegalStateException(); }
void enviar()   { if (estado.equals("PAGADO")) estado = "ENVIADO"; else throw new IllegalStateException(); }
void cancelar() { if (estado.equals("CREADO") || estado.equals("PAGADO")) estado = "CANCELADO"; else throw ...; }
```

## Después

<!-- file: ejemplo/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        Pedido pedido = new Pedido();
        pedido.pagar();
        pedido.enviar();
        System.out.println("Estado: " + pedido.estado());
        try {
            pedido.cancelar();
        } catch (IllegalStateException e) {
            System.out.println("Rechazado: " + e.getMessage());
        }
    }
}

/** Cada estado decide qué se puede hacer y a qué estado se pasa. */
interface EstadoPedido {
    String nombre();

    default EstadoPedido pagar() { throw invalida("pagar"); }
    default EstadoPedido enviar() { throw invalida("enviar"); }
    default EstadoPedido cancelar() { throw invalida("cancelar"); }

    private IllegalStateException invalida(String accion) {
        return new IllegalStateException("No se puede " + accion + " un pedido " + nombre());
    }
}

class Creado implements EstadoPedido {
    @Override public String nombre() { return "creado"; }
    @Override public EstadoPedido pagar() { return new Pagado(); }
    @Override public EstadoPedido cancelar() { return new Cancelado(); }
}

class Pagado implements EstadoPedido {
    @Override public String nombre() { return "pagado"; }
    @Override public EstadoPedido enviar() { return new Enviado(); }
    @Override public EstadoPedido cancelar() { return new Cancelado(); }
}

class Enviado implements EstadoPedido {
    @Override public String nombre() { return "enviado"; }
}

class Cancelado implements EstadoPedido {
    @Override public String nombre() { return "cancelado"; }
}

/** El contexto delega en su estado actual. */
class Pedido {

    private EstadoPedido estado = new Creado();

    void pagar() { estado = estado.pagar(); }
    void enviar() { estado = estado.enviar(); }
    void cancelar() { estado = estado.cancelar(); }
    String estado() { return estado.nombre(); }
}
```

Leyendo cada clase de estado se ve qué transiciones permite. Lo que no está permitido falla por defecto.

## Alternativas

- **`enum` con tabla de transiciones**: `Map<Estado, Set<Estado>>`. Para la mayoría de casos de negocio es suficiente, más simple y fácil de persistir. Úsalo cuando lo que varía por estado son las transiciones y no mucho comportamiento.
- **`enum` con métodos por constante**, punto intermedio.
- **Un motor de máquinas de estado** (Spring Statemachine) o de flujos de trabajo, si las transiciones son muchas, configurables o de larga duración.

## En Spring

- Entidades con un campo `@Enumerated(EnumType.STRING) EstadoPedido estado` y métodos de negocio que validan la transición. Nunca `setEstado` público.
- Spring Statemachine para flujos complejos.
- Persistencia: se guarda el nombre del estado y se reconstruye el objeto de estado al cargar.

## Trade-offs

- A favor: elimina condicionales repetidos; las transiciones quedan explícitas; agregar un estado no toca los demás métodos del contexto.
- En contra: una clase por estado; si los estados comparten casi todo, hay duplicación; el flujo completo no se ve en un solo archivo; persistir el estado requiere un mapeo.

## Errores comunes

- Exponer un `setEstado` que permite saltarse las reglas.
- Estados que conocen demasiado del contexto.
- Aplicar el patrón completo cuando un `enum` con una tabla bastaba.
- Confundir State con Strategy: la estructura es la misma, pero en State los objetos se reemplazan entre sí siguiendo reglas internas; en Strategy el cliente elige.

## Cuándo no usarlo

Con dos o tres estados y poco comportamiento distinto; cuando solo cambian las transiciones permitidas.

## Taller

Modela una solicitud de vacaciones: borrador → enviada → aprobada o rechazada; una aprobada puede cancelarse antes de la fecha de inicio; una rechazada puede volver a borrador para corregirse.

1. Impleméntala con `enum` y tabla de transiciones.
2. Impleméntala con el patrón State.
3. Compara las dos: líneas, claridad, facilidad para responder "¿desde dónde se llega a aprobada?".
4. Agrega el estado "en disfrute" con una regla propia (no se puede cancelar). ¿Cuál versión absorbió mejor el cambio?
5. ¿Cómo guardarías el estado en una base de datos en cada versión?

Entrega: rama `taller/n3-state`.
