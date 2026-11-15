# 01 — Clases, objetos, atributos y métodos

## Paso 1 — Explicación para principiantes

Un arquitecto dibuja el plano de una casa. Con ese plano se construyen diez casas. El plano no es una casa: no se puede vivir en él. Cada casa construida es independiente: pintar una de azul no cambia las demás.

- El plano es la **clase**.
- Cada casa es un **objeto**.
- El color, el número de habitaciones y la dirección son **atributos**: lo que cada casa tiene.
- Abrir la puerta o encender la luz son **métodos**: lo que cada casa puede hacer.

## Paso 2 — Explicación técnica

| Concepto | Definición |
|---|---|
| Clase | Definición de un tipo: qué estado guarda y qué operaciones ofrece. |
| Objeto (instancia) | Un valor concreto de ese tipo, creado con `new`, con su propio estado. |
| Atributo (campo) | Variable que pertenece a cada objeto. Juntos forman su estado. |
| Método | Operación que el objeto ejecuta, normalmente leyendo o cambiando su estado. |
| Constructor | Código que deja al objeto en un estado inicial válido. |
| `this` | Referencia al objeto sobre el que se está ejecutando el método. |

Tres ideas que causan errores reales cuando no están claras:

**Una variable no contiene un objeto: contiene una referencia.** `Producto b = a;` no copia el producto; hace que `a` y `b` apunten al mismo objeto. Cambiarlo por `b` lo cambia para `a`.

**`==` compara referencias; `equals` compara lo que la clase decida.** Dos objetos con los mismos datos son `==` distintos.

**`static` pertenece a la clase, no a los objetos.** Un atributo `static` es compartido por todas las instancias: es estado global con otro nombre. Un método `static` no tiene `this`.

## Paso 3 — Código que funciona, pero está mal diseñado

<!-- file: ejemplos/antes/Main.java -->
```java
public class Main {

    static String[] nombres = new String[10];
    static long[] precios = new long[10];
    static int[] existencias = new int[10];
    static int total = 0;

    public static void main(String[] args) {
        agregar("Teclado", 120_000, 5);
        agregar("Mouse", 60_000, 0);
        vender(0, 2);
        vender(1, 1);
        imprimir();
    }

    static void agregar(String nombre, long precio, int cantidad) {
        nombres[total] = nombre;
        precios[total] = precio;
        existencias[total] = cantidad;
        total++;
    }

    static void vender(int indice, int cantidad) {
        if (existencias[indice] >= cantidad) {
            existencias[indice] -= cantidad;
        } else {
            System.out.println("Sin existencias de " + nombres[indice]);
        }
    }

    static void imprimir() {
        for (int i = 0; i < total; i++) {
            System.out.println(nombres[i] + " $" + precios[i] + " x" + existencias[i]);
        }
    }
}
```

## Paso 4 — Análisis

- **Los datos de un producto están repartidos en tres arreglos.** Nada garantiza que la posición 3 de uno corresponda a la posición 3 de los otros. Ordenar un arreglo y olvidar los demás corrompe todo en silencio.
- **El concepto "producto" no existe en el código.** Solo existe en la cabeza de quien lo escribió.
- **Todo es `static`: hay un único inventario global.** No se puede tener el inventario de dos bodegas ni probar una parte sin afectar a otra.
- **El tamaño está fijo en 10** y el código que lo usa no lo comprueba.
- **Agregar un dato nuevo** (categoría, por ejemplo) obliga a crear otro arreglo y a tocar cada función.

## Paso 5 — Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Inventario bodega = new Inventario();
        Producto teclado = new Producto("Teclado", 120_000, 5);
        bodega.agregar(teclado);
        bodega.agregar(new Producto("Mouse", 60_000, 0));

        teclado.vender(2);
        bodega.imprimir();

        // Dos variables, un solo objeto.
        Producto otraReferencia = teclado;
        otraReferencia.vender(1);
        System.out.println("Teclados restantes: " + teclado.getExistencias());

        // Mismos datos, objetos distintos.
        Producto copia = new Producto("Teclado", 120_000, 2);
        System.out.println("¿Mismo objeto? " + (copia == teclado));
    }
}

class Producto {

    private final String nombre;
    private final long precio;
    private int existencias;

    Producto(String nombre, long precio, int existencias) {
        this.nombre = nombre;
        this.precio = precio;
        this.existencias = existencias;
    }

    void vender(int cantidad) {
        if (cantidad > existencias) {
            throw new IllegalStateException("Sin existencias de " + nombre);
        }
        existencias -= cantidad;
    }

    int getExistencias() {
        return existencias;
    }

    String describir() {
        return nombre + " $" + precio + " x" + existencias;
    }
}

class Inventario {

    private final List<Producto> productos = new ArrayList<>();

    void agregar(Producto producto) {
        productos.add(producto);
    }

    void imprimir() {
        for (Producto producto : productos) {
            System.out.println(producto.describir());
        }
    }
}
```

Qué cambió:

1. **El concepto tiene nombre.** `Producto` junta los datos que van juntos. Ya no pueden desalinearse.
2. **El comportamiento vive con los datos.** `vender` es algo que un producto hace, no algo que se le hace a tres arreglos.
3. **`Inventario` es un objeto, no un estado global.** Se pueden crear dos bodegas con `new Inventario()`.
4. **El constructor obliga a dar todos los datos.** No existe un producto a medio crear.

Los ejemplos van en un solo archivo para leerlos de corrido. En un proyecto real cada clase va en su propio archivo.

## Paso 6 — Taller práctico

Una biblioteca maneja sus libros así:

<!-- file: taller/inicio/Main.java -->
```java
public class Main {

    static String[] titulos = {"Cien años de soledad", "Rayuela", "Ficciones"};
    static String[] autores = {"García Márquez", "Cortázar", "Borges"};
    static int[] disponibles = {2, 0, 1};

    public static void main(String[] args) {
        prestar(0);
        prestar(1);
        devolver(1);
        for (int i = 0; i < titulos.length; i++) {
            System.out.println(titulos[i] + " (" + autores[i] + "): " + disponibles[i]);
        }
    }

    static void prestar(int i) {
        if (disponibles[i] > 0) {
            disponibles[i]--;
        } else {
            System.out.println("No hay ejemplares de " + titulos[i]);
        }
    }

    static void devolver(int i) {
        disponibles[i]++;
    }
}
```

1. Lista los problemas de diseño y qué podría fallar por cada uno.
2. Reescríbelo con las clases que consideres necesarias. No uses `static` salvo en `main`.
3. Debe ser posible tener dos bibliotecas independientes en el mismo programa. Demuéstralo.
4. Hay un error de lógica en `devolver`. Encuéntralo y decide dónde debe vivir la regla que lo evita.
5. Explica con un ejemplo de tu código la diferencia entre referencia y objeto.

Entrega: rama `taller/n1-01-clases`, solución en `taller/solucion/` con `ANALISIS.md`.

## Paso 7 — Revisión técnica

- ¿Cada concepto del problema tiene una clase?
- ¿Queda algún estado `static`?
- ¿Se puede crear un objeto incompleto o inválido?
- ¿Las reglas están en la clase dueña de los datos?

## Paso 8 — Aplicación profesional

- **Un `@Service` de Spring es un objeto, y por defecto hay uno solo para toda la aplicación.** Todas las peticiones concurrentes usan la misma instancia. Guardar en un atributo de un servicio datos de una petición (el usuario actual, un total parcial) es un error de concurrencia clásico: equivale al `static` del ejemplo.
- **Una entidad JPA es una instancia por fila.** Dos consultas pueden devolver dos objetos distintos que representan la misma fila; por eso importa cómo se definen `equals` y `hashCode`.
- **Los errores de referencia compartida son reales:** devolver una lista interna y que otro código la modifique, o reutilizar un objeto mutable como valor por defecto.

## Paso 9 — Evaluación senior

1. ¿Por qué un `@Service` singleton con un atributo mutable es peligroso y un objeto `Producto` con atributo mutable no lo es?
2. Un método recibe una lista y le agrega elementos. ¿Quién más ve el cambio? ¿Cómo lo evitarías y qué cuesta?
3. ¿Cuándo es legítimo un método `static`? Da dos casos válidos y uno que parezca válido y no lo sea.
4. Si dos objetos `Cliente` tienen la misma cédula, ¿son el mismo cliente? ¿De qué depende la respuesta?
5. ¿Qué información pierde el código cuando un concepto del negocio no tiene clase propia?

## Comprobación antes de avanzar

- Diferencia entre clase, objeto y referencia.
- Qué hace un constructor y por qué importa que deje el objeto válido.
- Por qué `static` es estado global.
- Diferencia entre `==` y `equals`.

Siguiente: [02 — Encapsulamiento](../02-encapsulamiento/).
