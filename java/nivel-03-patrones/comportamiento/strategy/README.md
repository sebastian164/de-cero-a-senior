# Strategy

Familia: comportamiento. Frecuencia en Java empresarial: muy alta.

## El problema

Una operación puede hacerse de varias maneras (varios algoritmos, varias políticas) y la manera correcta se decide en tiempo de ejecución. Si todas las variantes viven dentro de la clase que las usa, esa clase crece con cada variante nueva y mezcla "qué hacer" con "cómo hacerlo".

## Cómo reconocer que lo necesitas

- Un `if`/`switch` que elige entre algoritmos equivalentes para el mismo fin.
- La misma estructura condicional repetida en varios métodos.
- Necesitas cambiar el comportamiento por cliente, por país o por configuración.
- Quieres probar cada variante de forma aislada.

## Antes

```java
long calcularEnvio(String modalidad, int pesoKg) {
    if (modalidad.equals("ESTANDAR")) {
        return 8_000 + pesoKg * 500L;
    } else if (modalidad.equals("EXPRESS")) {
        return 15_000 + pesoKg * 900L;
    } else if (modalidad.equals("RECOGIDA")) {
        return 0;
    }
    throw new IllegalArgumentException(modalidad);
}
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Map<String, CalculoEnvio> modalidades = Map.of(
                "ESTANDAR", new EnvioEstandar(),
                "EXPRESS", new EnvioExpress(),
                "RECOGIDA", pesoKg -> 0);   // una estrategia también puede ser una lambda

        Carrito carrito = new Carrito(4, modalidades.get("EXPRESS"));
        System.out.println("Express: " + carrito.costoEnvio());

        carrito.cambiarEnvio(modalidades.get("RECOGIDA"));
        System.out.println("Recogida: " + carrito.costoEnvio());
    }
}

/** La estrategia: una familia de algoritmos intercambiables. */
@FunctionalInterface
interface CalculoEnvio {
    long calcular(int pesoKg);
}

class EnvioEstandar implements CalculoEnvio {
    @Override public long calcular(int pesoKg) { return 8_000 + pesoKg * 500L; }
}

class EnvioExpress implements CalculoEnvio {
    @Override public long calcular(int pesoKg) { return 15_000 + pesoKg * 900L; }
}

/** El contexto: usa una estrategia sin saber cuál es. */
class Carrito {

    private final int pesoKg;
    private CalculoEnvio calculoEnvio;

    Carrito(int pesoKg, CalculoEnvio calculoEnvio) {
        this.pesoKg = pesoKg;
        this.calculoEnvio = calculoEnvio;
    }

    void cambiarEnvio(CalculoEnvio nuevo) {
        this.calculoEnvio = nuevo;
    }

    long costoEnvio() {
        return calculoEnvio.calcular(pesoKg);
    }
}
```

`Carrito` quedó cerrado: una modalidad nueva es una clase o una lambda nueva. La selección por texto se redujo a un mapa en un único lugar.

## Alternativas

- **Un `if` simple**, si hay dos variantes estables.
- **Una lambda o referencia a método** en lugar de una clase, cuando la estrategia no tiene estado. `Comparator` es una estrategia y casi siempre se escribe como lambda.
- **Un `enum` con comportamiento** (cada constante implementa el método), si el conjunto es cerrado.
- **Template Method**, si lo que varía son pasos dentro de un algoritmo fijo y la herencia es aceptable.

## En Spring

Es el patrón más usado en aplicaciones Spring, aunque rara vez se le llame por su nombre: una interfaz, varios `@Component` que la implementan, y un servicio que recibe `Map<String, CalculoEnvio>` o `List<CalculoEnvio>` por el constructor. También: `PasswordEncoder`, `AuthenticationProvider`, `ResourceLoader`, `TaskExecutor`.

## Ventajas, desventajas y trade-offs

| A favor | En contra |
|---|---|
| Elimina condicionales por variante | Más tipos que leer |
| Cada variante se prueba sola | El cliente debe saber que existen estrategias y elegir una |
| Se cambia el comportamiento en ejecución | Si las estrategias necesitan datos distintos, la interfaz se hincha |
| Cumple OCP para "nueva variante" | Indirección innecesaria si las variantes no cambian |

## Errores comunes

- Estrategias que necesitan conocer el interior del contexto: señal de que la frontera está mal puesta.
- Una interfaz con parámetros que solo usa una de las estrategias.
- Dejar el `switch` que selecciona la estrategia repartido por el código en lugar de en un único punto.
- Crear la jerarquía para dos variantes que nunca han cambiado.

## Cuándo no usarlo

Cuando hay una sola forma de hacer la operación; cuando las variantes son dos y estables; cuando el "algoritmo" es una línea y una lambda en el punto de uso basta.

## Taller

Un sistema de nómina liquida horas extra. Hay tres políticas según el país de la filial: recargo fijo del 25 %; recargo del 25 % diurno y 75 % nocturno; y sin recargo pero con compensación en días libres (una hora libre por cada hora extra).

1. Impleméntalo primero con condicionales.
2. Refactoriza a Strategy. Nota que la tercera política no devuelve dinero: decide cómo modelas un resultado que puede ser dinero o tiempo.
3. Agrega una cuarta política sin modificar la clase que liquida.
4. ¿Cuál de tus estrategias podría ser una lambda y cuál no? ¿Por qué?
5. Indica dónde quedó la decisión de qué estrategia usar y quién es responsable de ella.

Entrega: rama `taller/n3-strategy`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).
