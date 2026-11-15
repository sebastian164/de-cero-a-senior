# Flyweight

Familia: estructural. Frecuencia en Java empresarial: baja como patrón explícito; es una técnica de optimización de memoria.

## El problema

El programa crea una cantidad enorme de objetos que repiten los mismos datos. Un millón de registros que guardan cada uno su propia copia del texto `"Colombia"`, de la misma tarifa o del mismo estilo consumen memoria sin aportar información.

## La idea

Separar el estado en dos:

| Estado | Qué es | Dónde vive |
|---|---|---|
| **Intrínseco** | Lo que se repite y no depende del contexto | En un objeto compartido e inmutable |
| **Extrínseco** | Lo propio de cada uso | Fuera, lo aporta quien usa el objeto |

## Cómo reconocer que lo necesitas

- Un análisis de memoria (no una sospecha) muestra millones de objetos casi iguales.
- Gran parte de cada objeto es idéntica entre instancias.
- Esos datos repetidos pueden ser inmutables.

## Antes

```java
// Cada punto del mapa crea su propio objeto con los mismos datos del tipo
new Marcador(4.60, -74.08, "Hospital", "cruz-roja.png", "#FF0000", "Atención 24 h");
new Marcador(5.53, -73.36, "Hospital", "cruz-roja.png", "#FF0000", "Atención 24 h");
// ... 500.000 veces
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        CatalogoTipos catalogo = new CatalogoTipos();
        List<Marcador> marcadores = new ArrayList<>();

        for (int i = 0; i < 100_000; i++) {
            String tipo = i % 2 == 0 ? "Hospital" : "Colegio";
            marcadores.add(new Marcador(4.0 + i * 0.00001, -74.0, catalogo.obtener(tipo)));
        }

        System.out.println("Marcadores: " + marcadores.size());
        System.out.println("Objetos TipoMarcador creados: " + catalogo.creados());
        System.out.println(marcadores.get(0).describir());
    }
}

/** Estado intrínseco: compartido e inmutable. */
record TipoMarcador(String nombre, String icono, String color) { }

/** La fábrica garantiza que cada tipo se crea una sola vez. */
class CatalogoTipos {

    private final Map<String, TipoMarcador> tipos = new HashMap<>();

    TipoMarcador obtener(String nombre) {
        return tipos.computeIfAbsent(nombre,
                n -> new TipoMarcador(n, n.toLowerCase() + ".png", n.equals("Hospital") ? "#FF0000" : "#0000FF"));
    }

    int creados() {
        return tipos.size();
    }
}

/** Estado extrínseco (la posición) más una referencia al compartido. */
class Marcador {

    private final double latitud;
    private final double longitud;
    private final TipoMarcador tipo;

    Marcador(double latitud, double longitud, TipoMarcador tipo) {
        this.latitud = latitud;
        this.longitud = longitud;
        this.tipo = tipo;
    }

    String describir() {
        return tipo.nombre() + " en (" + latitud + ", " + longitud + ") icono " + tipo.icono();
    }
}
```

Cien mil marcadores, dos objetos de tipo.

## Alternativas

- **No hacer nada**: es la opción correcta hasta que una medición diga lo contrario.
- **Normalizar los datos**: guardar un identificador y consultar el catálogo; es el mismo principio que una clave foránea.
- **Un `enum`**, si el conjunto de valores compartidos es fijo.
- **Cachés con política de expulsión** (Caffeine), si el conjunto compartido puede crecer sin límite.

## En Spring y en Java

- El *pool* de cadenas de Java y `String.intern()`.
- `Integer.valueOf(127)` devuelve una instancia en caché para valores de −128 a 127; por eso comparar `Integer` con `==` "funciona" con números pequeños y falla con grandes.
- `Boolean.valueOf`, `enum`, `List.of()` vacía.
- Los beans singleton sin estado son objetos compartidos por todos los hilos por la misma razón.

## Trade-offs

- A favor: reduce mucho la memoria cuando la repetición es real; menos presión sobre el recolector de basura.
- En contra: más complejidad; el estado compartido **debe** ser inmutable; la fábrica necesita protección frente a concurrencia; un catálogo sin límite es una fuga de memoria.

## Errores comunes

- Aplicarlo sin medir.
- Compartir objetos mutables: un cambio afecta a todos los que lo usan.
- Comparar con `==` objetos que "a veces" están en caché.
- Catálogos que crecen sin límite.

## Cuándo no usarlo

Cuando no hay un problema de memoria demostrado; cuando los objetos son pocos; cuando casi todo el estado es propio de cada instancia.

## Taller

Un editor de texto representa cada carácter como un objeto con: letra, fuente, tamaño, color y posición.

1. Crea un documento de 200.000 caracteres sin compartir nada y mide la memoria usada (`Runtime.totalMemory() - freeMemory()` antes y después, tras sugerir GC).
2. Aplica Flyweight separando el formato del carácter. Mide de nuevo.
3. ¿Qué pasa si alguien cambia el color de un formato compartido? Impídelo.
4. Explica con código por qué `Integer a = 127, b = 127; a == b` es verdadero y con 128 es falso, y qué error real puede causar.

Entrega: rama `taller/n3-flyweight`.
