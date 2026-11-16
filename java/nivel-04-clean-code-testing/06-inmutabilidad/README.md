# 06 — Inmutabilidad

## Idea sencilla

Un billete de 50.000 no cambia de valor mientras está en tu bolsillo. Si quieres 100.000, no modificas el billete: recibes otro. Como no cambia, puedes prestarlo, contarlo dos veces o dejarlo sobre la mesa sin miedo a que alguien lo "edite".

Un objeto inmutable funciona igual: una vez creado, no cambia. Para tener un valor distinto se crea otro objeto.

## Explicación técnica

Un objeto es inmutable si su estado observable no puede cambiar después de construirlo.

### Por qué importa

- **Se puede compartir sin copias defensivas.** Nadie puede alterarlo a tus espaldas.
- **Es seguro entre hilos** sin sincronización: no hay escrituras que coordinar.
- **Es más fácil de razonar:** lo que validaste al crear sigue siendo válido siempre.
- **Sirve como clave de `Map` y elemento de `Set`:** su `hashCode` no cambia.
- **Elimina una categoría entera de errores:** los de "¿quién modificó esto?".

### Receta para una clase inmutable

1. Clase `final` (o `record`).
2. Todos los campos `private final`.
3. Sin setters ni métodos que modifiquen el estado.
4. Copia defensiva de los parámetros mutables en el constructor.
5. No devolver referencias a componentes mutables internos.
6. Los métodos que "cambian" devuelven un objeto nuevo (`conDescuento(…)`, `plus(…)`).

### `final` no es inmutable

`final List<String> items` impide reasignar la variable, no impide `items.add(…)`. La referencia es constante; el objeto no.

### Herramientas de Java

- `record`: campos finales, `equals`, `hashCode` y `toString` generados. Es inmutable solo si sus componentes lo son.
- `List.of`, `Set.of`, `Map.of`, `List.copyOf`: colecciones no modificables.
- `Collections.unmodifiableList`: una **vista** de solo lectura; si la lista original cambia, la vista también.
- `java.time`: todo inmutable. `java.util.Date` y `Calendar` no lo son y son fuente histórica de errores.
- `String`, `BigDecimal`, `Integer`: inmutables.

### Objetos de valor

Conceptos que se definen por su valor y no por una identidad: `Dinero`, `Email`, `RangoFechas`, `Coordenada`. Son los candidatos naturales a la inmutabilidad y resuelven la obsesión por primitivos.

## Código con problemas

<!-- file: ejemplos/antes/Main.java -->
```java
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<String> invitados = new ArrayList<>(List.of("Ana", "Luis"));
        Date fecha = new Date(0);
        Evento evento = new Evento("Lanzamiento", fecha, invitados);

        // Nadie llamó a un método de Evento, y el evento cambió.
        invitados.add("Colado");
        fecha.setTime(86_400_000L * 365);
        evento.getInvitados().clear();

        System.out.println(evento.getFecha() + " " + evento.getInvitados());
    }
}

class Evento {

    private final String nombre;
    private final Date fecha;
    private final List<String> invitados;

    Evento(String nombre, Date fecha, List<String> invitados) {
        this.nombre = nombre;
        this.fecha = fecha;
        this.invitados = invitados;
    }

    String getNombre() { return nombre; }
    Date getFecha() { return fecha; }
    List<String> getInvitados() { return invitados; }
}
```

## Análisis

Todos los campos son `private final` y no hay setters. Aun así, el evento cambió de fecha y de invitados tres veces desde afuera:

- El constructor guarda las referencias recibidas; quien las creó las sigue teniendo.
- Los getters entregan las referencias internas.
- `Date` es mutable.

Si ese evento se usa desde dos hilos, o se guardó como clave en un mapa, el resultado es impredecible.

## Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<String> invitados = new ArrayList<>(List.of("Ana", "Luis"));
        Evento evento = new Evento("Lanzamiento", LocalDate.of(2026, 3, 1), invitados);

        invitados.add("Colado");                 // ya no afecta al evento
        try {
            evento.invitados().add("Otro");      // falla: lista no modificable
        } catch (UnsupportedOperationException e) {
            System.out.println("No se puede modificar la lista de invitados");
        }

        Evento aplazado = evento.aplazadoA(LocalDate.of(2026, 4, 15));
        Evento conMarta = aplazado.conInvitado("Marta");

        System.out.println(evento);
        System.out.println(conMarta);

        Dinero precio = Dinero.pesos(120_000);
        System.out.println(precio.mas(Dinero.pesos(30_000)).conDescuento(10));
    }
}

record Evento(String nombre, LocalDate fecha, List<String> invitados) {

    /** Constructor compacto: valida y hace la copia defensiva. */
    Evento {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El evento necesita nombre");
        }
        invitados = List.copyOf(invitados);
    }

    Evento aplazadoA(LocalDate nuevaFecha) {
        return new Evento(nombre, nuevaFecha, invitados);
    }

    Evento conInvitado(String invitado) {
        List<String> nuevos = new ArrayList<>(invitados);
        nuevos.add(invitado);
        return new Evento(nombre, fecha, nuevos);
    }
}

/** Objeto de valor: se define por lo que vale, no por una identidad. */
record Dinero(long pesos) {

    Dinero {
        if (pesos < 0) {
            throw new IllegalArgumentException("El valor no puede ser negativo");
        }
    }

    static Dinero pesos(long valor) {
        return new Dinero(valor);
    }

    Dinero mas(Dinero otro) {
        return new Dinero(pesos + otro.pesos);
    }

    Dinero conDescuento(int porcentaje) {
        return new Dinero(pesos - pesos * porcentaje / 100);
    }
}
```

## Taller

1. Diseña el objeto de valor `Periodo` (fecha de inicio y fin): inmutable, con validación (fin no anterior a inicio), y con `contiene(fecha)`, `seSolapaCon(otro)` y `extendidoHasta(fecha)`.
2. Diseña `Email` y `Cedula` como objetos de valor con validación. Muestra una firma de método antes (`String, String`) y después, y explica qué error de orden de parámetros deja de ser posible.
3. Toma una clase mutable de un proyecto tuyo y vuélvela inmutable. Lista qué código cliente tuviste que cambiar.
4. Escribe un programa con dos hilos que modifiquen un objeto mutable compartido y muestre un resultado incorrecto. Corrígelo con inmutabilidad, sin `synchronized`.
5. Demuestra el error de usar un objeto mutable como clave de un `HashMap`.

Se revisará: copias defensivas, ausencia de fugas de referencias y `equals`/`hashCode` coherentes. Rama `taller/n4-06-inmutabilidad`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Aplicación profesional

- **DTOs como `record`:** peticiones y respuestas de la API, eventos, mensajes y objetos de configuración (`@ConfigurationProperties` admite `record`).
- **Entidades JPA:** son mutables por naturaleza (Hibernate detecta cambios sobre ellas). La inmutabilidad se aplica a sus componentes: objetos de valor con `@Embeddable`.
- **Beans singleton:** deben ser inmutables o sin estado; dependencias `final` inyectadas por constructor.
- **Eventos de dominio:** siempre inmutables. Un hecho pasado no cambia.
- **Costo:** crear objetos nuevos presiona al recolector de basura. En la gran mayoría de aplicaciones empresariales es irrelevante; mídelo antes de preocuparte.

## Preguntas senior

1. ¿Un `record` con un campo `List` es inmutable? ¿Qué hay que agregar?
2. ¿Por qué los objetos inmutables son seguros entre hilos y qué garantía adicional da `final` en el modelo de memoria de Java?
3. ¿Cómo concilias entidades JPA mutables con un dominio que quieres inmutable?
4. ¿Cuándo la inmutabilidad es un problema real de rendimiento y cómo lo sabrías?
5. Diferencia entre `List.copyOf`, `Collections.unmodifiableList` y `new ArrayList<>(lista)`.

## Cuándo no aplicarla

Objetos con identidad y ciclo de vida largo (entidades), acumuladores locales dentro de un método (`StringBuilder`), o estructuras muy grandes modificadas en bucles críticos donde copiar es medible.

Siguiente: [07 — Manejo de excepciones](../07-excepciones/).
