# 02 — Colecciones, `equals` y `hashCode`

## Idea

Una biblioteca puede guardar los libros en una pila, en estantes numerados o con un fichero por autor. Lo que tardas en encontrar un libro depende de esa decisión, no de cuántos libros haya. Elegir la colección correcta es elegir cómo se va a buscar.

## Lo esencial

### Elegir la estructura

| Necesito | Colección | Buscar | Insertar |
|---|---|---|---|
| Orden de inserción, acceso por índice | `ArrayList` | O(n) | O(1) al final |
| Sin duplicados, búsqueda rápida | `HashSet` | O(1) | O(1) |
| Sin duplicados, ordenado | `TreeSet` | O(log n) | O(log n) |
| Clave → valor | `HashMap` | O(1) | O(1) |
| Clave → valor, orden de inserción | `LinkedHashMap` | O(1) | O(1) |
| Clave → valor, ordenado por clave | `TreeMap` | O(log n) | O(log n) |
| Cola o pila | `ArrayDeque` | — | O(1) en extremos |
| Mapa concurrente | `ConcurrentHashMap` | O(1) | O(1) |

`LinkedList` casi nunca es la respuesta: `ArrayDeque` o `ArrayList` la superan en la práctica. `Vector`, `Stack` y `Hashtable` son heredadas.

### El contrato de `equals` y `hashCode`

1. Si `a.equals(b)`, entonces `a.hashCode() == b.hashCode()`. Siempre.
2. Lo contrario no se exige: dos objetos distintos pueden compartir `hashCode`.
3. `equals` debe ser reflexivo, simétrico, transitivo y consistente.
4. Se sobrescriben los dos o ninguno.

Un `HashMap` usa `hashCode` para encontrar la casilla y `equals` para encontrar el elemento dentro de ella. Si el contrato se rompe, el objeto se "pierde" dentro del mapa.

### Orden

- `Comparable<T>`: el orden natural, definido en la propia clase.
- `Comparator<T>`: un orden externo, componible: `Comparator.comparing(Pedido::fecha).thenComparing(Pedido::total).reversed()`.

## Ejemplo

```java
record Cedula(String numero) { }      // record genera equals y hashCode por valor

Set<Cedula> vistos = new HashSet<>();
vistos.add(new Cedula("123"));
vistos.contains(new Cedula("123"));   // true

class ClienteMal {                     // sin equals/hashCode: compara por referencia
    String cedula;
}
Set<ClienteMal> clientes = new HashSet<>();
// dos objetos con la misma cédula se guardan como distintos

// El error de la clave mutable
List<String> clave = new ArrayList<>(List.of("a"));
Map<List<String>, String> mapa = new HashMap<>();
mapa.put(clave, "valor");
clave.add("b");                        // cambió el hashCode
mapa.get(clave);                       // null: el valor quedó inalcanzable
```

## Errores comunes

- Sobrescribir `equals` sin `hashCode`.
- Usar objetos mutables como clave de mapa o elemento de conjunto.
- `equals` con `getClass()` o `instanceof` mal elegido en jerarquías.
- Buscar con `list.contains` dentro de un bucle: O(n²). Usa un `Set`.
- Modificar una colección mientras se recorre.
- Usar `HashMap` desde varios hilos.

## En Spring y JPA

`equals` y `hashCode` de entidades JPA son un tema clásico: no deben basarse en todos los campos ni en un `id` generado que es `null` antes de persistir, y `@Data` de Lombok genera una versión problemática (incluye colecciones perezosas). Opciones: una clave de negocio inmutable, o `id` con `hashCode` constante por clase.

## Taller

1. Mide el tiempo de 100.000 búsquedas en un `ArrayList` y en un `HashSet` de un millón de elementos.
2. Escribe una clase con `equals` y sin `hashCode` y demuestra el fallo en un `HashSet`.
3. Demuestra el error de la clave mutable y corrígelo.
4. Ordena una lista de pedidos por estado, luego por fecha descendente y luego por cliente, con `Comparator` compuesto.
5. Implementa `equals` y `hashCode` para una entidad `Producto` con `id` generado, y defiende tu elección.

Rama `taller/n6-02-colecciones`.

## Preguntas senior

1. ¿Cómo funciona un `HashMap` por dentro? ¿Qué pasa con las colisiones?
2. ¿Qué ocurre si dos objetos iguales tienen distinto `hashCode`?
3. ¿Cómo implementas `equals` y `hashCode` en una entidad JPA y por qué?
4. `ArrayList` frente a `LinkedList`: ¿cuándo gana cada una en la práctica?
5. `ConcurrentHashMap` frente a `Collections.synchronizedMap`.
