# OCP — Principio abierto/cerrado

Requisito: [SRP](../01-srp/) y las lecciones 04 y 05 del Nivel 1 (polimorfismo y abstracción).

## 1. Explicación sencilla

Una regleta eléctrica tiene seis tomas. Para conectar un aparato nuevo no abres la regleta ni soldas cables: lo enchufas. La regleta está **cerrada** (nadie la modifica) y a la vez **abierta** (admite aparatos que no existían cuando se fabricó), porque define un punto de conexión estándar.

El principio pide lo mismo para el código: que agregar un comportamiento nuevo consista en enchufar una pieza nueva, no en abrir y modificar código que ya funciona.

## 2. Definición técnica

Bertrand Meyer lo formuló en 1988: las entidades de software deben estar abiertas a la extensión y cerradas a la modificación. Robert C. Martin lo reinterpretó con polimorfismo: el comportamiento de un módulo se extiende agregando implementaciones de una abstracción, sin editar el módulo.

Tres precisiones:

**Se cierra respecto a un eje de variación concreto, no respecto a todo.** Ningún código queda cerrado ante cualquier cambio imaginable. La decisión de diseño es elegir qué tipo de cambio será barato.

**"Cerrado" no significa intocable.** Los errores se corrigen. Lo que se evita es modificar código probado cada vez que aparece un caso nuevo del mismo tipo.

**El mecanismo es una abstracción en el punto donde el código varía:** una interfaz con varias implementaciones, una lista de reglas, una estrategia que se recibe por parámetro.

## 3. Código que viola el principio

<!-- file: ejemplos/antes/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        ValidadorPedido validador = new ValidadorPedido();
        System.out.println(validador.validar(new Pedido("Ana", 3, 250_000, "Tunja")));
        System.out.println(validador.validar(new Pedido("", 0, 9_000_000, "Leticia")));
    }
}

class Pedido {
    final String cliente;
    final int cantidadItems;
    final long valor;
    final String ciudad;

    Pedido(String cliente, int cantidadItems, long valor, String ciudad) {
        this.cliente = cliente;
        this.cantidadItems = cantidadItems;
        this.valor = valor;
        this.ciudad = ciudad;
    }
}

class ValidadorPedido {

    List<String> validar(Pedido pedido) {
        List<String> errores = new ArrayList<>();

        if (pedido.cliente == null || pedido.cliente.isBlank()) {
            errores.add("El pedido no tiene cliente");
        }
        if (pedido.cantidadItems <= 0) {
            errores.add("El pedido no tiene ítems");
        }
        // Agregado en marzo por riesgo
        if (pedido.valor > 5_000_000) {
            errores.add("El valor supera el máximo permitido");
        }
        // Agregado en junio por logística
        if (pedido.ciudad.equals("Leticia") || pedido.ciudad.equals("San Andrés")) {
            errores.add("No hay cobertura de envío a " + pedido.ciudad);
        }
        return errores;
    }
}
```

## 4. Por qué es un problema

Los comentarios cuentan la historia: cada regla nueva significó **abrir** `validar`.

- **Cada regla nueva pone en riesgo las anteriores.** Un `if` mal ubicado o una variable reutilizada rompe una validación que llevaba meses funcionando. Hay que volver a probar todo.
- **Áreas distintas editan el mismo método:** producto, riesgo, logística. Es también una violación de SRP; los dos principios suelen fallar juntos.
- **El método solo puede crecer.** En un sistema real estas clases llegan a cientos de líneas de `if`.
- **No se puede activar o desactivar una regla** (por cliente, por país, por ambiente) sin más `if`.
- **Probar una regla** exige construir un pedido que pase todas las demás.

## 5. Refactorización paso a paso

**Paso 1 — Encontrar el eje de variación.** Lo que cambia es el conjunto de reglas. Lo que no cambia es el procedimiento: aplicar todas y reunir los errores.

**Paso 2 — Darle un contrato a lo que varía.**

**Paso 3 — Convertir cada `if` en una implementación.**

**Paso 4 — El validador recibe las reglas; no las conoce.**

<!-- file: ejemplos/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Main {

    public static void main(String[] args) {
        ValidadorPedido validador = new ValidadorPedido(List.of(
                new ReglaClienteObligatorio(),
                new ReglaTieneItems(),
                new ReglaValorMaximo(5_000_000),
                new ReglaCobertura(Set.of("Leticia", "San Andrés"))));

        System.out.println(validador.validar(new Pedido("Ana", 3, 250_000, "Tunja")));
        System.out.println(validador.validar(new Pedido("", 0, 9_000_000, "Leticia")));
    }
}

class Pedido {
    final String cliente;
    final int cantidadItems;
    final long valor;
    final String ciudad;

    Pedido(String cliente, int cantidadItems, long valor, String ciudad) {
        this.cliente = cliente;
        this.cantidadItems = cantidadItems;
        this.valor = valor;
        this.ciudad = ciudad;
    }
}

/** El punto de extensión: la "toma" donde se enchufan reglas nuevas. */
interface ReglaValidacion {
    Optional<String> evaluar(Pedido pedido);
}

/** Cerrado: una regla nueva no obliga a modificar esta clase. */
class ValidadorPedido {

    private final List<ReglaValidacion> reglas;

    ValidadorPedido(List<ReglaValidacion> reglas) {
        this.reglas = List.copyOf(reglas);
    }

    List<String> validar(Pedido pedido) {
        List<String> errores = new ArrayList<>();
        for (ReglaValidacion regla : reglas) {
            regla.evaluar(pedido).ifPresent(errores::add);
        }
        return errores;
    }
}

class ReglaClienteObligatorio implements ReglaValidacion {
    @Override
    public Optional<String> evaluar(Pedido pedido) {
        boolean falta = pedido.cliente == null || pedido.cliente.isBlank();
        return falta ? Optional.of("El pedido no tiene cliente") : Optional.empty();
    }
}

class ReglaTieneItems implements ReglaValidacion {
    @Override
    public Optional<String> evaluar(Pedido pedido) {
        return pedido.cantidadItems <= 0 ? Optional.of("El pedido no tiene ítems") : Optional.empty();
    }
}

class ReglaValorMaximo implements ReglaValidacion {

    private final long maximo;

    ReglaValorMaximo(long maximo) {
        this.maximo = maximo;
    }

    @Override
    public Optional<String> evaluar(Pedido pedido) {
        return pedido.valor > maximo
                ? Optional.of("El valor supera el máximo permitido") : Optional.empty();
    }
}

class ReglaCobertura implements ReglaValidacion {

    private final Set<String> ciudadesSinCobertura;

    ReglaCobertura(Set<String> ciudadesSinCobertura) {
        this.ciudadesSinCobertura = ciudadesSinCobertura;
    }

    @Override
    public Optional<String> evaluar(Pedido pedido) {
        return ciudadesSinCobertura.contains(pedido.ciudad)
                ? Optional.of("No hay cobertura de envío a " + pedido.ciudad) : Optional.empty();
    }
}
```

### Qué se ganó

- Una regla nueva es una clase nueva. `ValidadorPedido` y las reglas existentes no se tocan.
- Cada regla se prueba sola, con un pedido mínimo.
- El conjunto de reglas se decide al ensamblar: distintos validadores para distintos contextos, sin `if`.
- Cada regla tiene un dueño claro.

### Qué se pagó

- De una clase a seis tipos. Para saber qué se valida ya no basta leer un método: hay que ver cómo se ensambla el validador.
- Sigue habiendo **un** lugar que se modifica al agregar una regla: donde se arma la lista. Ese punto de ensamblaje existe siempre; el principio consiste en que sea uno, pequeño y sin lógica.
- Si aparece una regla que depende de otra, o que necesita un orden, este diseño no alcanza y habrá que revisarlo. Se cerró respecto a "agregar reglas independientes", no respecto a todo.

## 6. Ejemplo empresarial con Spring Boot

Fragmentos ilustrativos: no forman parte del código del repo. Se compilaron y probaron con Spring Boot 4.1.1 y Java 21; donde aparece `{ ... }` o `// ...` se omitió código que no aporta a la idea.

Spring convierte el punto de ensamblaje en automático: si un constructor pide una lista de una interfaz, recibe todos los beans que la implementan.

```java
public interface ReglaValidacion {
    Optional<String> evaluar(Pedido pedido);
}

@Component
@Order(1)
class ReglaClienteObligatorio implements ReglaValidacion { ... }

@Component
@Order(2)
class ReglaValorMaximo implements ReglaValidacion { ... }

@Service
public class ValidadorPedido {

    private final List<ReglaValidacion> reglas;

    public ValidadorPedido(List<ReglaValidacion> reglas) {
        this.reglas = reglas;
    }
}
```

Agregar una regla es crear una clase con `@Component`. No se edita ningún archivo existente.

Variante frecuente: elegir **una** implementación según un dato.

```java
@Service
public class ProcesadorPagos {

    private final Map<String, MedioDePago> medios; // clave: nombre del bean

    public ProcesadorPagos(Map<String, MedioDePago> medios) {
        this.medios = medios;
    }

    public Comprobante pagar(String medio, BigDecimal monto) {
        MedioDePago seleccionado = medios.get(medio);
        if (seleccionado == null) {
            throw new MedioDePagoNoSoportadoException(medio);
        }
        return seleccionado.pagar(monto);
    }
}
```

Spring mismo está construido así: `HandlerInterceptor`, `Converter`, `HandlerMethodArgumentResolver`, `AuthenticationProvider`. Extiendes el framework registrando implementaciones, sin modificar su código. Las condiciones de `@ConditionalOnProperty` y `@Profile` permiten además activar o desactivar extensiones por configuración.

## 7. Taller práctico

<!-- file: taller/inicio/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        CalculadoraPrecio calculadora = new CalculadoraPrecio();
        System.out.println(calculadora.precioFinal(100_000, "TECNOLOGIA", "BOGOTA", true, "VERANO10"));
        System.out.println(calculadora.precioFinal(100_000, "ALIMENTOS", "TUNJA", false, null));
    }
}

class CalculadoraPrecio {

    long precioFinal(long base, String categoria, String ciudad, boolean esFragil, String cupon) {
        long precio = base;

        // impuesto según categoría
        if (categoria.equals("TECNOLOGIA")) {
            precio += base * 19 / 100;
        } else if (categoria.equals("ALIMENTOS")) {
            precio += base * 5 / 100;
        }

        // recargo de envío
        if (ciudad.equals("BOGOTA")) {
            precio += 8_000;
        } else {
            precio += 15_000;
        }

        // empaque especial
        if (esFragil) {
            precio += 6_000;
        }

        // cupones
        if ("VERANO10".equals(cupon)) {
            precio -= base * 10 / 100;
        } else if ("BIENVENIDA".equals(cupon)) {
            precio -= 20_000;
        }

        return precio;
    }
}
```

1. Identifica los ejes de variación. ¿Son todos igual de probables? Ordénalos del que más cambiará al que menos y justifica con lo que sabes de un comercio real.
2. Refactoriza para que agregar un cupón nuevo y un tipo de recargo nuevo no obligue a modificar `CalculadoraPrecio`.
3. Decide conscientemente dejar **un** eje sin abstraer y explica por qué.
4. Agrega el cupón `ENVIOGRATIS` (anula el recargo de envío). ¿Tu diseño lo soportó o tuviste que cambiarlo? Describe qué supuesto de tu diseño se rompió.
5. El orden de aplicación importa (¿el cupón porcentual se calcula antes o después del impuesto?). ¿Dónde quedó definido ese orden en tu solución?

Entrega: rama `taller/02-ocp`.

## 8. Preguntas de entrevista senior

1. "Cerrado a la modificación" es imposible en términos absolutos. ¿Cómo decides respecto a qué cambios cerrar un módulo?
2. ¿Qué relación hay entre OCP y SRP? ¿Puede cumplirse uno violando el otro?
3. Un `switch` sobre un `enum` con una cláusula `default` que lanza excepción, ¿viola OCP? ¿Y un `switch` exhaustivo sobre una interfaz `sealed`?
4. Tu equipo agregó un punto de extensión hace dos años y nunca tuvo una segunda implementación. ¿Qué haces?
5. ¿Cómo aplicas OCP a una API REST pública que ya tiene consumidores?
6. ¿Qué diferencia hay entre extender por herencia y extender por composición desde el punto de vista de OCP?
7. Describe un mecanismo de plugins que conozcas y explica cómo materializa OCP.

## 9. Cuándo aplicar OCP produce complejidad innecesaria

**Cuando la variación es especulativa.** Crear una abstracción para un cambio que quizá nunca llegue cuesta hoy y puede no pagarse. Una regla práctica: tolera el primer `if`; cuando aparezca el segundo caso del mismo tipo, ya tienes evidencia del eje de variación y sabes qué forma debe tener la abstracción.

**Cuando te equivocas de eje.** Una abstracción en el lugar equivocado es peor que ninguna: el cambio real llega por otro lado y además hay que rodear la abstracción inútil.

**Cuando los casos son pocos, cerrados y estables.** Los días de la semana, los estados de un semáforo. Un `enum` con un `switch` verificado por el compilador es más claro que cinco clases.

**Cuando la extensión necesita conocer demasiado.** Si cada implementación nueva obliga a ampliar la interfaz, la abstracción no captura lo que varía.

**Cuando el código es de aplicación y no de biblioteca.** Si controlas todo el código y modificarlo es barato y seguro (hay pruebas), el beneficio de no tocarlo es menor que en una biblioteca que usan terceros.

## Comprobación antes de avanzar

- Explicar OCP sin usar las palabras "abierto" ni "cerrado".
- Señalar el eje de variación en un código dado.
- Decir qué punto del sistema sigue modificándose siempre y por qué es aceptable.
- Dar un caso en el que un `if` es mejor que una interfaz.

Siguiente: [LSP — Principio de sustitución de Liskov](../03-lsp/).
