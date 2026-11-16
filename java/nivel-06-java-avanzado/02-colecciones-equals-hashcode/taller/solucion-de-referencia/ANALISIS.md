# Taller 06-02 — Colecciones, `equals` y `hashCode` (solución)

`mvn test` ejecuta 6 pruebas (una arranca Hibernate con H2). La medición del punto 1 tarda alrededor de un minuto porque hace de verdad las 100.000 búsquedas lineales.

## 1. 100.000 búsquedas en un millón de elementos

`MedirBusquedas`: lista y conjunto con los enteros 0..999.999; 100.000 claves aleatorias entre 0 y 1.999.999 (la mitad no existe, que es el peor caso de la lista). Se calienta el conjunto antes de medir. JDK 21.0.13, Apple Silicon:

| Ejecución | `ArrayList.contains` | `HashSet.contains` | Encontrados |
|---|---|---|---|
| `java -cp target/classes colecciones.MedirBusquedas` | 43.165 ms | 8 ms | 50.004 |
| Dentro de `mvn test` | 62.562 ms | 3 ms | 50.004 |

La lista recorre en promedio ~750.000 elementos por búsqueda (O(n)); el conjunto calcula el hash y mira una casilla (O(1)): más de 5.000 veces más rápido. La prueba exige que la lista sea al menos 100 veces más lenta y que ambas encuentren lo mismo; los tiempos absolutos cambian con la máquina y la carga.

## 2. `equals` sin `hashCode`

`ClienteSinHashCode`: dos instancias con la misma cédula son `equals`, pero `HashSet.contains` no encuentra la segunda (cada una usa el `hashCode` de `Object`, distinto) y el conjunto acepta las dos.

## 3. Clave mutable

Al agregar un elemento a la lista usada como clave, su `hashCode` cambia: `mapa.get(clave)` devuelve `null`, tampoco se encuentra con la clave original, y la entrada sigue ocupando memoria. Corrección: usar una clave inmutable (`List.copyOf`, un `record` con campos inmutables o un `String`).

## 4. Comparador compuesto

```java
Comparator.comparing(Pedido::estado)
        .thenComparing(Pedido::fecha, Comparator.reverseOrder())
        .thenComparing(Pedido::cliente);
```

Nota: `.reversed()` al final, como en el ejemplo del README, invierte **todo** el orden acumulado; para invertir solo la fecha se pasa `Comparator.reverseOrder()` a ese `thenComparing`.

## 5. `equals` y `hashCode` de `Producto` con `id` generado

Elección: `equals` compara `id` solo si no es `null`; `hashCode` devuelve una constante por clase.

Defensa, probada con Hibernate 7 en `ProductoJpaTest`:
- El objeto se agrega a un `HashSet` antes de persistir y **sigue encontrándose** después, cuando ya tiene `id`, porque su `hashCode` no cambió.
- La instancia cargada con `em.find` (otro objeto) es igual a la original.
- Dos productos nuevos sin `id` no son iguales.
- El contraejemplo (`hashCode` por `id`) pierde el objeto en el conjunto al asignarse el `id`.

Costo aceptado: todos los productos caen en la misma casilla de un `HashSet`, lo que degrada a búsqueda lineal. Es irrelevante para colecciones de entidades en memoria, que son pequeñas. Si el producto tuviera una clave de negocio inmutable (SKU), sería mejor usarla en ambos métodos.
