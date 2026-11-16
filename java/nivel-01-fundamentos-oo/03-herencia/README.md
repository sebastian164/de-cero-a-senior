# 03 — Herencia

## Paso 1 — Explicación para principiantes

En una empresa todos los empleados tienen nombre, documento y fecha de ingreso. Algunos cobran por horas y otros tienen salario fijo. En vez de describir desde cero cada tipo, se dice: "un empleado por horas **es un** empleado que además cobra según las horas trabajadas".

La herencia permite definir un tipo general y tipos más específicos que reciben todo lo del general y agregan o cambian lo suyo.

La prueba es la frase "es un". Un empleado por horas es un empleado: bien. Una pila de platos **no es** una lista con todas sus operaciones: mal, aunque se parezcan por dentro.

## Paso 2 — Explicación técnica

- `class B extends A`: `B` (subclase) hereda los miembros no privados de `A` (superclase).
- **Sobrescritura (`@Override`)**: la subclase redefine un método heredado.
- **`super`**: acceso a la versión de la superclase, incluido su constructor. El constructor de la superclase siempre se ejecuta primero.
- **`protected`**: visible para subclases. Es una grieta en el encapsulamiento: úsalo con cuidado.
- **`final`**: una clase `final` no se puede heredar; un método `final` no se puede sobrescribir.
- Java tiene **herencia simple** de clases: una sola superclase. Toda clase hereda de `Object`.

### Lo que la herencia cuesta

La herencia es la relación **más acoplada** que existe entre dos clases:

- La subclase depende de los detalles de implementación de la superclase, no solo de su interfaz pública.
- Un cambio interno en la superclase puede romper subclases que nadie tocó (**clase base frágil**).
- La subclase hereda **todo**, también lo que no le conviene.
- Se decide al compilar y no se puede cambiar al ejecutar.

### Dos usos distintos

| Uso | Ejemplo | Veredicto |
|---|---|---|
| Modelar una relación "es un" con comportamiento sustituible | `EmpleadoPorHoras` es un `Empleado` | Correcto |
| Reutilizar código de una clase que hace algo parecido | `Pila` hereda de `ArrayList` | Casi siempre incorrecto |

## Paso 3 — Código que funciona, pero está mal diseñado

<!-- file: ejemplos/antes/Main.java -->
```java
import java.util.ArrayList;

public class Main {

    public static void main(String[] args) {
        Pila pila = new Pila();
        pila.apilar("plato 1");
        pila.apilar("plato 2");
        pila.apilar("plato 3");
        System.out.println("Tope: " + pila.desapilar());

        // Todo esto es legal, y destruye la idea de pila.
        pila.add(0, "plato colado por debajo");
        pila.remove("plato 2");
        pila.clear();
        System.out.println("¿Vacía? " + pila.isEmpty());
    }
}

/** Hereda de ArrayList para "aprovechar" que ya sabe guardar elementos. */
class Pila extends ArrayList<String> {

    void apilar(String elemento) {
        add(elemento);
    }

    String desapilar() {
        return remove(size() - 1);
    }
}
```

## Paso 4 — Análisis

- `Pila` prometía una regla: el último en entrar es el primero en salir. Al heredar de `ArrayList` recibió decenas de métodos públicos que permiten violarla.
- La pila **no es una** lista. Se usó herencia para ahorrar código, no para modelar una relación.
- Si mañana conviene guardar los elementos de otra forma, no se puede: todo el que use `Pila` puede estar llamando métodos de `ArrayList`.
- No es un caso inventado: `java.util.Stack` hereda de `Vector` y arrastra exactamente este problema desde 1996. Por eso hoy se recomienda `Deque`.

## Paso 5 — Código mejorado

Primero, la corrección de la pila. Segundo, un uso legítimo de herencia.

<!-- file: ejemplos/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Pila pila = new Pila();
        pila.apilar("plato 1");
        pila.apilar("plato 2");
        System.out.println("Tope: " + pila.desapilar());
        // pila.add(0, "colado");  // no compila: Pila no expone eso

        List<Empleado> nomina = List.of(
                new EmpleadoAsalariado("Ana", 4_000_000),
                new EmpleadoPorHoras("Luis", 25_000, 120));
        for (Empleado empleado : nomina) {
            System.out.println(empleado.resumen());
        }
    }
}

/** La pila TIENE una lista. No ES una lista. */
class Pila {

    private final List<String> elementos = new ArrayList<>();

    void apilar(String elemento) {
        elementos.add(elemento);
    }

    String desapilar() {
        if (elementos.isEmpty()) {
            throw new IllegalStateException("La pila está vacía");
        }
        return elementos.remove(elementos.size() - 1);
    }
}

abstract class Empleado {

    private final String nombre;

    protected Empleado(String nombre) {
        this.nombre = nombre;
    }

    abstract long pagoMensual();

    final String resumen() {
        return nombre + " cobra $" + pagoMensual();
    }
}

class EmpleadoAsalariado extends Empleado {

    private final long salario;

    EmpleadoAsalariado(String nombre, long salario) {
        super(nombre);
        this.salario = salario;
    }

    @Override
    long pagoMensual() {
        return salario;
    }
}

class EmpleadoPorHoras extends Empleado {

    private final long valorHora;
    private final int horas;

    EmpleadoPorHoras(String nombre, long valorHora, int horas) {
        super(nombre);
        this.valorHora = valorHora;
        this.horas = horas;
    }

    @Override
    long pagoMensual() {
        return valorHora * horas;
    }
}
```

Por qué la jerarquía de `Empleado` sí es válida:

- Pasa la prueba "es un" sin reservas.
- Cualquier código que reciba un `Empleado` funciona con cualquiera de las subclases.
- La superclase es pequeña y estable: un dato común y un contrato.
- `nombre` es `private`: las subclases no dependen de cómo se guarda.
- `resumen()` es `final`: las subclases cambian solo lo que está previsto que cambien.

La palabra `abstract` se estudia a fondo en la lección 05.

## Paso 6 — Taller práctico

Una aplicación de dibujo tiene tres clases sin relación entre sí: `Circulo` (radio), `Rectangulo` (ancho, alto) y `Triangulo` (base, altura, tres lados). Cada una tiene `area()`, `perimetro()` y un método `describir()` idéntico en las tres: imprime el nombre de la figura, su área y su perímetro con dos decimales.

1. Escribe primero las tres clases como están descritas, con la duplicación.
2. Diseña una jerarquía que elimine la duplicación. Decide qué va en la superclase y qué no.
3. Escribe un método que reciba una lista de figuras e imprima la de mayor área, sin conocer los tipos concretos.
4. Alguien propone que `Cuadrado` herede de `Rectangulo` "porque un cuadrado es un rectángulo". Sin implementarlo todavía, escribe qué podría salir mal si `Rectangulo` tuviera `setAncho` y `setAlto`. Volveremos a esto en LSP.
5. `java.util.Properties` hereda de `Hashtable<Object,Object>`. Investiga qué problema concreto causa y descríbelo en tus palabras.

Entrega: rama `taller/n1-03-herencia`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Paso 7 — Revisión técnica

- ¿Cada `extends` pasa la prueba "es un" en todos los usos, no solo en el nombre?
- ¿La superclase expone campos `protected` que podrían ser `private`?
- ¿Hay métodos heredados que una subclase no debería ofrecer?
- ¿La jerarquía tiene más de dos niveles? ¿Por qué?

## Paso 8 — Aplicación profesional

- **`@MappedSuperclass` para una `EntidadBase`** con `id`, fecha de creación y de modificación: uso razonable y extendido. Los campos comunes son estables y de infraestructura.
- **Jerarquías de excepciones:** `PedidoNoEncontradoException extends RecursoNoEncontradoException` permite que un `@ControllerAdvice` trate toda la familia de una vez.
- **`BaseService<T>` o `BaseController<T>` genéricos:** suelen empezar ahorrando código y terminar con veinte subclases atadas a decisiones que ya no aplican a la mitad. Sospecha.
- **`extends JpaRepository<...>`** es herencia de interfaces: se hereda un contrato, no una implementación. Es un caso distinto y mucho más seguro.

## Paso 9 — Evaluación senior

1. ¿Por qué la herencia rompe el encapsulamiento de la superclase? Da un ejemplo.
2. ¿Qué preguntas haces antes de aprobar un `extends` en una revisión de código?
3. Explica el problema de la clase base frágil con un caso que no sea el de esta lección.
4. ¿Por qué `final` en clases y métodos se considera una buena práctica de diseño y no una restricción molesta?
5. ¿Qué diferencia hay entre heredar de una clase y de una interfaz en términos de acoplamiento?

## Comprobación antes de avanzar

- Diferencia entre heredar para modelar y heredar para reutilizar.
- Qué ejecuta `super(...)` y en qué orden.
- Por qué la herencia es la relación más acoplada.
- Para qué sirven `protected` y `final`.

Siguiente: [04 — Polimorfismo](../04-polimorfismo/).
