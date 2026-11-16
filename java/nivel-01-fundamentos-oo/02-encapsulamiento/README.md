# 02 — Encapsulamiento

## Paso 1 — Explicación para principiantes

Un cajero automático tiene dinero adentro. Tú no abres la caja y sacas billetes: presionas botones y el cajero decide si te entrega el dinero, cuánto y bajo qué condiciones. Las reglas (tu saldo, el tope diario) las aplica el cajero, siempre, a todo el mundo.

Encapsular es eso: el objeto guarda sus datos bajo llave y ofrece botones. Quien lo usa no puede saltarse las reglas porque no tiene acceso a la caja.

## Paso 2 — Explicación técnica

Encapsulamiento es ocultar el estado interno de un objeto y permitir modificarlo únicamente a través de operaciones que preservan sus **invariantes** (las condiciones que siempre deben ser ciertas).

### Modificadores de acceso en Java

| Modificador | Quién puede acceder |
|---|---|
| `private` | Solo la propia clase. |
| (sin modificador) | Las clases del mismo paquete. |
| `protected` | El mismo paquete y las subclases. |
| `public` | Cualquiera. |

Regla práctica: empieza por el más restrictivo y abre solo cuando haya un motivo.

### Lo que el encapsulamiento no es

**No es poner `private` y generar getters y setters.** Un campo privado con `setSaldo(long)` público está tan expuesto como un campo público. El modificador cambió; el diseño, no.

**No se rompe solo con setters.** También se rompe al devolver una referencia a un objeto mutable interno. Si `getItems()` devuelve la lista real, cualquiera puede hacer `getItems().clear()`.

### Ideas relacionadas

- **Tell, Don't Ask:** en lugar de pedirle datos a un objeto para decidir por él, pídele que haga la operación.
- **Copia defensiva:** devolver una copia o una vista de solo lectura de las colecciones internas.
- **Ley de Deméter:** un método debería hablar con sus colaboradores directos, no con los colaboradores de sus colaboradores.

## Paso 3 — Código que funciona, pero está mal diseñado

<!-- file: ejemplos/antes/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Curso curso = new Curso();
        curso.setNombre("Arquitectura de software");
        curso.setCupoMaximo(2);

        // La regla del cupo la aplica quien usa el curso... cuando se acuerda.
        if (curso.getEstudiantes().size() < curso.getCupoMaximo()) {
            curso.getEstudiantes().add("Ana");
        }
        curso.getEstudiantes().add("Luis");
        curso.getEstudiantes().add("Marta");
        curso.getEstudiantes().add("Marta");
        curso.setCupoMaximo(-5);

        System.out.println(curso.getNombre() + ": " + curso.getEstudiantes()
                + " cupo " + curso.getCupoMaximo());
    }
}

class Curso {

    private String nombre;
    private int cupoMaximo;
    private List<String> estudiantes = new ArrayList<>();

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public int getCupoMaximo() { return cupoMaximo; }
    public void setCupoMaximo(int cupoMaximo) { this.cupoMaximo = cupoMaximo; }

    public List<String> getEstudiantes() { return estudiantes; }
    public void setEstudiantes(List<String> estudiantes) { this.estudiantes = estudiantes; }
}
```

## Paso 4 — Análisis

Todos los campos son `private` y aun así:

- El curso terminó con cuatro inscritos en un cupo de dos, un estudiante duplicado y un cupo negativo.
- La regla del cupo depende de que **cada** lugar que inscribe estudiantes la recuerde. El primero la aplicó; los otros tres no.
- `getEstudiantes()` entrega la lista real: el estado interno salió de la clase.
- El curso puede existir sin nombre: nada obliga a llamar a `setNombre`.

La pregunta para detectar el problema: **¿puede alguien desde afuera dejar este objeto en un estado que el negocio considera imposible?** Si la respuesta es sí, no está encapsulado.

## Paso 5 — Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Curso curso = new Curso("Arquitectura de software", 2);
        curso.inscribir("Ana");
        curso.inscribir("Luis");

        intentar(() -> curso.inscribir("Marta"));
        intentar(() -> curso.estudiantes().add("Colado"));

        System.out.println(curso.nombre() + ": " + curso.estudiantes()
                + " cupos libres " + curso.cuposLibres());
    }

    static void intentar(Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            System.out.println("Rechazado: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " - " + e.getMessage()));
        }
    }
}

class Curso {

    private final String nombre;
    private final int cupoMaximo;
    private final List<String> estudiantes = new ArrayList<>();

    Curso(String nombre, int cupoMaximo) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El curso necesita nombre");
        }
        if (cupoMaximo <= 0) {
            throw new IllegalArgumentException("El cupo debe ser mayor que cero");
        }
        this.nombre = nombre;
        this.cupoMaximo = cupoMaximo;
    }

    void inscribir(String estudiante) {
        if (estudiantes.contains(estudiante)) {
            throw new IllegalStateException(estudiante + " ya está inscrito");
        }
        if (cuposLibres() == 0) {
            throw new IllegalStateException("El curso está lleno");
        }
        estudiantes.add(estudiante);
    }

    int cuposLibres() {
        return cupoMaximo - estudiantes.size();
    }

    String nombre() {
        return nombre;
    }

    List<String> estudiantes() {
        return Collections.unmodifiableList(estudiantes);
    }
}
```

Paso a paso:

1. **Constructor con validación.** No existe un curso sin nombre ni con cupo inválido.
2. **Se eliminan los setters.** Nombre y cupo son `final`: no cambian después de crear el curso. Si el negocio necesitara ampliar el cupo, se agregaría `ampliarCupo(int)` con sus reglas, no un setter genérico.
3. **`inscribir` reemplaza a `getEstudiantes().add`.** La regla vive en un único lugar.
4. **`estudiantes()` devuelve una vista de solo lectura.** Leer está permitido; modificar, no.
5. **`cuposLibres()` responde la pregunta que antes obligaba a pedir dos datos.**

## Paso 6 — Taller práctico

<!-- file: taller/inicio/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Carrito carrito = new Carrito();
        carrito.getLineas().add(new Linea("Café", 2, 18_000));
        carrito.getLineas().add(new Linea("Pan", -3, 4_000));
        carrito.setTotal(1);
        carrito.setPagado(true);
        carrito.getLineas().add(new Linea("Queso", 1, 22_000));
        System.out.println("Total: " + carrito.getTotal() + " pagado: " + carrito.isPagado());
    }
}

class Linea {
    public String producto;
    public int cantidad;
    public long precioUnitario;

    Linea(String producto, int cantidad, long precioUnitario) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }
}

class Carrito {
    private List<Linea> lineas = new ArrayList<>();
    private long total;
    private boolean pagado;

    public List<Linea> getLineas() { return lineas; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public boolean isPagado() { return pagado; }
    public void setPagado(boolean pagado) { this.pagado = pagado; }
}
```

Reglas del negocio: las cantidades son positivas; el total siempre es la suma de las líneas; un carrito pagado no admite cambios; no se puede pagar un carrito vacío.

1. Escribe una línea de código que viole cada regla en la versión actual.
2. Refactoriza para que ninguna de esas líneas compile o, si compila, falle con un error claro.
3. Decide qué hacer con `total`: ¿atributo o cálculo? Justifica.
4. Si se agrega dos veces el mismo producto, ¿una línea o dos? Decide, impleméntalo y justifícalo.

Entrega: rama `taller/n1-02-encapsulamiento`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Paso 7 — Revisión técnica

- ¿Queda algún setter? ¿Qué regla lo justifica?
- ¿Alguna colección interna sale sin protección?
- ¿El constructor rechaza estados inválidos?
- ¿Hay datos derivados guardados que puedan desincronizarse?

## Paso 8 — Aplicación profesional

- **`@Data` de Lombok en entidades** genera un setter público por campo. En entidades con reglas, prefiere `@Getter` y métodos con nombre de negocio (`aprobar()`, `cancelar()`).
- **JPA necesita un constructor sin argumentos**, pero puede ser `protected`: el framework lo usa y tu código no.
- **Colecciones en entidades:** `getItems()` que devuelve la lista gestionada por Hibernate permite modificar la relación desde cualquier servicio. Expón `agregarItem` y una vista de solo lectura.
- **DTOs y `record`:** son datos sin reglas que cruzan una frontera. Que sean transparentes es correcto; encapsular no aplica a todo.

## Paso 9 — Evaluación senior

1. ¿En qué casos un setter es correcto? Da un criterio, no una lista.
2. `Collections.unmodifiableList` devuelve una vista. Si los elementos son mutables, ¿el encapsulamiento está completo? ¿Qué harías?
3. Un colega dice que quitar setters complica el mapeo desde un formulario. ¿Cómo separas el objeto que recibe el formulario del objeto que protege reglas?
4. ¿Qué relación hay entre encapsulamiento y facilidad para probar una clase?
5. ¿Por qué la validación en el constructor es más fuerte que un método `esValido()`?

## Comprobación antes de avanzar

- Qué es una invariante y dónde debe protegerse.
- Por qué getters y setters no equivalen a encapsular.
- Qué es una copia defensiva y cuándo se necesita.
- Qué significa Tell, Don't Ask.

Siguiente: [03 — Herencia](../03-herencia/).
