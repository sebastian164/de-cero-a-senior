# Composite

Familia: estructural. Frecuencia en Java empresarial: media.

## El problema

Los datos forman un árbol: elementos simples y grupos que contienen elementos simples u otros grupos. El código que los recorre termina lleno de comprobaciones "¿es un elemento o un grupo?" y trata distinto cada caso, cuando la operación (calcular un total, pintar, validar) es la misma para ambos.

## Cómo reconocer que lo necesitas

- Jerarquías parte-todo: menús y submenús, carpetas y archivos, organigramas, categorías, productos y combos, reglas compuestas.
- Recursión con `instanceof` para distinguir hoja de contenedor.
- Quieres aplicar la misma operación a un elemento o a todo un subárbol.

## Antes

```java
long precio(Object elemento) {
    if (elemento instanceof Producto p) {
        return p.precio();
    } else if (elemento instanceof Combo c) {
        long total = 0;
        for (Object hijo : c.elementos()) total += precio(hijo);
        return total;
    }
    throw new IllegalArgumentException();
}
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Combo escritorio = new Combo("Kit escritorio", 10);
        escritorio.agregar(new Producto("Teclado", 120_000));
        escritorio.agregar(new Producto("Mouse", 60_000));

        Combo oficina = new Combo("Oficina completa", 5);
        oficina.agregar(escritorio);                       // un combo dentro de otro
        oficina.agregar(new Producto("Monitor", 800_000));

        System.out.println(oficina.describir(""));
        System.out.println("Total: $" + oficina.precio());
    }
}

/** El componente: lo que hojas y compuestos tienen en común. */
interface ItemCatalogo {
    long precio();
    String describir(String sangria);
}

/** La hoja. */
class Producto implements ItemCatalogo {

    private final String nombre;
    private final long precio;

    Producto(String nombre, long precio) {
        this.nombre = nombre;
        this.precio = precio;
    }

    @Override public long precio() { return precio; }
    @Override public String describir(String sangria) { return sangria + nombre + " $" + precio; }
}

/** El compuesto: contiene componentes y delega en ellos. */
class Combo implements ItemCatalogo {

    private final String nombre;
    private final int descuentoPorcentaje;
    private final List<ItemCatalogo> items = new ArrayList<>();

    Combo(String nombre, int descuentoPorcentaje) {
        this.nombre = nombre;
        this.descuentoPorcentaje = descuentoPorcentaje;
    }

    void agregar(ItemCatalogo item) {
        items.add(item);
    }

    @Override
    public long precio() {
        long suma = 0;
        for (ItemCatalogo item : items) {
            suma += item.precio();
        }
        return suma - suma * descuentoPorcentaje / 100;
    }

    @Override
    public String describir(String sangria) {
        StringBuilder texto = new StringBuilder(sangria + nombre + " (-" + descuentoPorcentaje + "%)");
        for (ItemCatalogo item : items) {
            texto.append("\n").append(item.describir(sangria + "  "));
        }
        return texto.toString();
    }
}
```

`agregar` está solo en `Combo`, no en la interfaz común: se prefiere seguridad (una hoja no puede recibir hijos) sobre uniformidad total.

## Alternativas

- **Una lista plana**, si no hay anidamiento real.
- **Interfaz `sealed` con `switch` exhaustivo** (Java 21): el compilador garantiza que se cubren todos los casos. Buena opción cuando hay muchas operaciones y pocos tipos de nodo.
- **Visitor**, cuando las operaciones sobre el árbol cambian más que los tipos de nodo.

## En Spring y en Java

- `CompositeHealthContributor`, `CompositePropertySource`, `DelegatingFilterProxy` y los `Composite…` de Spring agrupan varios elementos tras la interfaz de uno.
- Especificaciones de JPA combinadas con `and`/`or`; predicados compuestos (`Predicate.and`).
- Reglas de validación o de autorización que se agrupan en reglas mayores.

## Trade-offs

- A favor: el cliente trata igual a uno y a muchos; agregar tipos de nodo es fácil; la recursión queda dentro de la estructura.
- En contra: la interfaz común puede forzar operaciones que no tienen sentido en las hojas; difícil restringir qué puede contener qué; árboles profundos pueden desbordar la pila; riesgo de ciclos.

## Errores comunes

- Poner `agregar`/`quitar` en el componente y lanzar excepción en las hojas (violación de LSP).
- No protegerse contra ciclos (un combo que se contiene a sí mismo).
- Recalcular todo el árbol en cada consulta cuando podría guardarse el resultado.

## Cuándo no usarlo

Cuando la estructura no es recursiva; cuando hojas y contenedores no comparten operaciones reales.

## Taller

Modela el organigrama de una empresa: empleados y áreas que contienen empleados u otras áreas.

1. Calcula el costo mensual de nómina de cualquier nodo y el número de personas.
2. Impide que un área se agregue dentro de sí misma, directa o indirectamente.
3. Agrega la operación "listar a todos los que ganan más de X". ¿Tuviste que tocar todas las clases? Relaciónalo con Visitor.
4. Reescribe con `sealed interface` y `switch`. ¿Cuándo preferirías esta versión?

Entrega: rama `taller/n3-composite`.
