# Builder

Familia: creacional. Frecuencia en Java empresarial: muy alta.

## El problema

Un objeto necesita muchos datos para construirse, varios opcionales. Las salidas habituales son malas: un constructor con diez parámetros que nadie sabe en qué orden van, varios constructores sobrecargados (el "constructor telescópico"), o un constructor vacío seguido de setters, que permite objetos a medio armar y elimina la inmutabilidad.

## Cómo reconocer que lo necesitas

- Constructores con más de cuatro o cinco parámetros, varios del mismo tipo.
- Llamadas como `new Reserva(c, h, 2, 0, true, false, null, null)`.
- Varios constructores sobrecargados que se llaman entre sí.
- Quieres un objeto inmutable pero con muchas combinaciones de datos opcionales.
- Hay validaciones que dependen de varios campos a la vez.

## Antes

```java
Reserva reserva = new Reserva("Ana", "Hotel Duitama", 2, 0, true, false, null, "piso alto");
// ¿El 2 son adultos o noches? ¿El primer boolean es desayuno o parqueadero?
```

## Después

<!-- file: ejemplo/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        Reserva reserva = Reserva.builder("Ana", "Hotel Duitama")
                .adultos(2)
                .noches(3)
                .conDesayuno()
                .observaciones("piso alto")
                .build();
        System.out.println(reserva);

        try {
            Reserva.builder("Luis", "Hotel Paipa").adultos(0).build();
        } catch (IllegalStateException e) {
            System.out.println("Rechazada: " + e.getMessage());
        }
    }
}

final class Reserva {

    private final String huesped;
    private final String hotel;
    private final int adultos;
    private final int ninos;
    private final int noches;
    private final boolean desayuno;
    private final String observaciones;

    private Reserva(Builder builder) {
        this.huesped = builder.huesped;
        this.hotel = builder.hotel;
        this.adultos = builder.adultos;
        this.ninos = builder.ninos;
        this.noches = builder.noches;
        this.desayuno = builder.desayuno;
        this.observaciones = builder.observaciones;
    }

    /** Los datos obligatorios se piden al empezar: no se pueden olvidar. */
    static Builder builder(String huesped, String hotel) {
        return new Builder(huesped, hotel);
    }

    @Override
    public String toString() {
        return huesped + " en " + hotel + ": " + adultos + " adultos, " + ninos + " niños, "
                + noches + " noches" + (desayuno ? ", con desayuno" : "")
                + (observaciones.isEmpty() ? "" : " (" + observaciones + ")");
    }

    static final class Builder {

        private final String huesped;
        private final String hotel;
        private int adultos = 1;
        private int ninos = 0;
        private int noches = 1;
        private boolean desayuno = false;
        private String observaciones = "";

        private Builder(String huesped, String hotel) {
            this.huesped = huesped;
            this.hotel = hotel;
        }

        Builder adultos(int adultos) { this.adultos = adultos; return this; }
        Builder ninos(int ninos) { this.ninos = ninos; return this; }
        Builder noches(int noches) { this.noches = noches; return this; }
        Builder conDesayuno() { this.desayuno = true; return this; }
        Builder observaciones(String observaciones) { this.observaciones = observaciones; return this; }

        /** Las reglas que involucran varios campos se validan una vez, aquí. */
        Reserva build() {
            if (adultos < 1) {
                throw new IllegalStateException("Debe haber al menos un adulto");
            }
            if (noches < 1) {
                throw new IllegalStateException("La reserva debe ser de al menos una noche");
            }
            return new Reserva(this);
        }
    }
}
```

El objeto final es inmutable, la llamada se lee sola y no existe una `Reserva` inválida.

## Alternativas

- **Constructor normal**, si hay pocos parámetros y todos obligatorios.
- **Métodos de fábrica estáticos con nombre**, si hay pocas combinaciones (`Reserva.sencilla(…)`, `Reserva.familiar(…)`).
- **Un `record` con constructor compacto** para datos inmutables con validación, cuando no hay opcionales.
- **Agrupar parámetros en objetos** (`Huespedes`, `RangoFechas`): a menudo el exceso de parámetros indica conceptos sin nombre.

## En Spring

`ResponseEntity.ok().header(…).body(…)`, `UriComponentsBuilder`, `WebClient.builder()`, `RestClient.builder()`, `MockMvcRequestBuilders`, y la configuración de `HttpSecurity`. En el JDK: `StringBuilder`, `HttpRequest.newBuilder()`, `Stream.builder()`. Lombok genera el patrón con `@Builder`.

## Ventajas, desventajas y trade-offs

| A favor | En contra |
|---|---|
| Llamadas legibles | Mucho código repetitivo si se escribe a mano |
| Objeto inmutable con opcionales | Los campos se duplican en el builder |
| Validación centralizada en `build()` | Un obligatorio olvidado se detecta en ejecución, salvo que se pida al inicio |
| Valores por defecto explícitos | Un builder mutable compartido entre hilos da problemas |

## Errores comunes

- `@Builder` de Lombok en clases con reglas: genera un builder sin validación, que permite construir objetos inválidos. Hay que escribir `build()` o validar en el constructor.
- Builder para clases con dos o tres campos.
- Reutilizar el mismo builder para crear varios objetos sin darse cuenta de que conserva el estado.
- Exponer además setters en el objeto final, anulando la inmutabilidad.
- Usar un builder para ocultar que la clase tiene demasiadas responsabilidades.

## Cuándo no usarlo

Con pocos parámetros; cuando todos son obligatorios y de tipos distintos; cuando un `record` basta; cuando la cantidad de campos revela un problema de diseño que el builder solo maquilla.

## Taller

Modela `SolicitudCredito` con: cliente y monto (obligatorios), plazo en meses (por defecto 12), tasa (por defecto según el plazo), codeudor (opcional), seguro (opcional), y cuenta de desembolso (opcional).

Reglas: monto mayor que cero; plazo entre 6 y 84 meses; si el monto supera 50 millones el codeudor es obligatorio; si el plazo supera 60 meses el seguro es obligatorio.

1. Escribe primero la versión con constructor telescópico y una llamada de ejemplo. Guárdala para comparar.
2. Implementa el Builder a mano, con objeto inmutable.
3. ¿Dónde validaste las reglas que cruzan campos? ¿Por qué no en los métodos del builder?
4. Los obligatorios, ¿los pediste en `builder(…)` o los dejaste como métodos? Argumenta a favor y en contra de cada opción.
5. Reescribe con `@Builder` de Lombok (solo en papel) y explica qué validación se perdería y cómo la recuperas.

Entrega: rama `taller/n3-builder`.
