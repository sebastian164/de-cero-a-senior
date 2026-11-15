# Visitor

Familia: comportamiento. Frecuencia en Java empresarial: baja; en Java moderno suele reemplazarse por `sealed` y `switch`.

## El problema

Tienes una jerarquía de tipos **estable** y necesitas agregarle operaciones **con frecuencia**. Poner cada operación como método en todas las clases las llena de responsabilidades ajenas (exportar, calcular impuestos, validar, pintar) y obliga a modificarlas todas por cada operación nueva.

Es el dilema inverso al del polimorfismo normal:

| | Agregar un tipo nuevo | Agregar una operación nueva |
|---|---|---|
| Métodos en las clases (polimorfismo) | Fácil: una clase | Difícil: tocar todas las clases |
| Visitor | Difícil: tocar todos los visitantes | Fácil: un visitante |

## Cómo reconocer que lo necesitas

- Los tipos casi no cambian (los nodos de un árbol sintáctico, los tipos de un documento).
- Las operaciones sobre ellos no paran de crecer y no pertenecen al concepto.
- Hay `instanceof` en cadena repartido por varias operaciones.

## Antes

```java
long impuesto(Object item) {
    if (item instanceof Libro l) return 0;
    if (item instanceof Licor l) return l.precio() * 35 / 100;
    if (item instanceof Electronico e) return e.precio() * 19 / 100;
    throw new IllegalArgumentException();   // un tipo nuevo falla aquí, en ejecución
}
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Item> carrito = List.of(new Libro(50_000), new Licor(80_000), new Electronico(200_000));

        // Versión clásica: doble despacho.
        CalculoImpuesto impuestos = new CalculoImpuesto();
        long total = 0;
        for (Item item : carrito) {
            total += item.aceptar(impuestos);
        }
        System.out.println("Impuestos (visitor): " + total);

        // Versión moderna: switch exhaustivo sobre una jerarquía sellada.
        long totalModerno = 0;
        for (Item item : carrito) {
            totalModerno += switch (item) {
                case Libro libro -> 0L;
                case Licor licor -> licor.precio() * 35 / 100;
                case Electronico electronico -> electronico.precio() * 19 / 100;
            };
        }
        System.out.println("Impuestos (switch): " + totalModerno);
    }
}

sealed interface Item permits Libro, Licor, Electronico {
    <R> R aceptar(Visitante<R> visitante);
}

record Libro(long precio) implements Item {
    @Override public <R> R aceptar(Visitante<R> visitante) { return visitante.visitar(this); }
}

record Licor(long precio) implements Item {
    @Override public <R> R aceptar(Visitante<R> visitante) { return visitante.visitar(this); }
}

record Electronico(long precio) implements Item {
    @Override public <R> R aceptar(Visitante<R> visitante) { return visitante.visitar(this); }
}

/** Una operación nueva es una implementación nueva de esta interfaz. */
interface Visitante<R> {
    R visitar(Libro libro);
    R visitar(Licor licor);
    R visitar(Electronico electronico);
}

class CalculoImpuesto implements Visitante<Long> {
    @Override public Long visitar(Libro libro) { return 0L; }
    @Override public Long visitar(Licor licor) { return licor.precio() * 35 / 100; }
    @Override public Long visitar(Electronico electronico) { return electronico.precio() * 19 / 100; }
}
```

**Doble despacho:** `item.aceptar(v)` elige el método según el tipo real del ítem, y dentro `v.visitar(this)` elige la sobrecarga según el tipo, ya conocido, de `this`. Así se evita el `instanceof`.

En ambas versiones, si se agrega un cuarto tipo de ítem el código **deja de compilar** hasta que se atiende. Esa es la ventaja sobre el `instanceof` suelto.

## Alternativas

- **`sealed` + `switch` con *pattern matching*** (Java 21): mismo beneficio, sin `aceptar` ni interfaz de visitante. Es la opción recomendada hoy.
- **Métodos polimórficos en las clases**, si las operaciones son pocas y pertenecen al concepto.
- **Un `Map<Class<?>, Function>`**, flexible pero sin verificación del compilador.

## En Spring y en Java

- `FileVisitor` con `Files.walkFileTree`.
- `ElementVisitor` y `TypeVisitor` en el procesamiento de anotaciones.
- `BeanDefinitionVisitor` en Spring; los visitantes de árboles de expresiones de SpEL y de los analizadores sintácticos (ANTLR, JavaParser).
- Recorridos de árboles de criterios de consulta.

## Trade-offs

- A favor: operaciones nuevas sin tocar los tipos; la lógica de una operación está junta; el compilador exige cubrir todos los tipos.
- En contra: agregar un tipo obliga a tocar todos los visitantes; el doble despacho es difícil de leer; los visitantes pueden necesitar acceso a datos internos de los elementos.

## Errores comunes

- Usarlo sobre jerarquías que cambian a menudo.
- Visitantes con estado acumulado que se reutilizan sin reiniciar.
- Implementarlo a la manera clásica en un proyecto con Java 21, donde `switch` lo resuelve.

## Cuándo no usarlo

Cuando los tipos cambian más que las operaciones; cuando hay una o dos operaciones; cuando el lenguaje ofrece *pattern matching* exhaustivo.

## Taller

Modela expresiones aritméticas: `Numero`, `Suma`, `Multiplicacion` y `Negacion`, formando un árbol.

1. Con Visitor clásico, implementa tres operaciones: evaluar, imprimir con paréntesis y contar nodos.
2. Reescríbelo con `sealed interface` y `switch`.
3. Agrega el tipo `Division`. Anota qué dejó de compilar en cada versión.
4. Agrega la operación "simplificar" (`x * 1` → `x`, `x + 0` → `x`). ¿Qué tocaste en cada versión?
5. Con esos dos experimentos, formula tu propia regla para elegir entre polimorfismo, Visitor y `switch`.

Entrega: rama `taller/n3-visitor`.
