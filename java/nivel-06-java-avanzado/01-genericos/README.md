# 01 — Genéricos

## Idea

Una caja rotulada "solo manzanas" evita que alguien meta naranjas y te ahorra revisar qué sacas. Los genéricos son ese rótulo: le dicen al compilador qué tipo contiene una estructura, para que los errores aparezcan al compilar y no al ejecutar.

## Lo esencial

- **Clases y métodos genéricos:** `class Caja<T>`, `<T> T primero(List<T> lista)`.
- **Tipos acotados:** `<T extends Comparable<T>>` exige una capacidad.
- **Invarianza:** `List<Integer>` **no** es un `List<Number>`, aunque `Integer` sea un `Number`. Si lo fuera, se podría insertar un `Double` en una lista de enteros.
- **Comodines:**

| Sintaxis | Significa | Puedes |
|---|---|---|
| `List<? extends Number>` | Lista de algún subtipo de `Number` | Leer `Number`; no agregar |
| `List<? super Integer>` | Lista de algún supertipo de `Integer` | Agregar `Integer`; leer solo `Object` |
| `List<?>` | Lista de tipo desconocido | Leer `Object` |

- **PECS:** *Producer Extends, Consumer Super*. Si el parámetro te **da** elementos, `extends`; si los **recibe**, `super`.
- **Borrado de tipos (*type erasure*):** los genéricos existen solo al compilar. En ejecución `List<String>` y `List<Integer>` son `List`. Por eso no se puede hacer `new T()`, `T.class`, `instanceof List<String>` ni sobrecargar por parámetro genérico.

## Ejemplo

```java
// Productor: solo se lee de 'origen'  -> extends
// Consumidor: solo se escribe en 'destino' -> super
static <T> void copiar(List<? extends T> origen, List<? super T> destino) {
    for (T elemento : origen) {
        destino.add(elemento);
    }
}

List<Integer> enteros = List.of(1, 2, 3);
List<Number> numeros = new ArrayList<>();
copiar(enteros, numeros);          // funciona gracias a los comodines

// Tipo acotado: exige que los elementos sepan compararse
static <T extends Comparable<? super T>> T maximo(Collection<? extends T> elementos) {
    return elementos.stream().max(Comparator.naturalOrder()).orElseThrow();
}
```

## Errores comunes

- Usar tipos crudos (`List` sin parámetro): se pierde toda la verificación.
- Suprimir avisos `unchecked` sin entender por qué aparecen.
- Devolver comodines en tipos de retorno: obliga al cliente a lidiar con ellos.
- Creer que los genéricos existen en ejecución y hacer reflexión que depende de ellos.
- Jerarquías genéricas de cuatro parámetros que nadie puede leer.

## En Spring

`JpaRepository<Pedido, Long>`, `ResponseEntity<T>`, `Converter<S, T>`, `ApplicationListener<E>`. Como el tipo se borra, para deserializar una lista con Jackson o `RestClient` se usa un portador de tipo: `new ParameterizedTypeReference<List<Pedido>>() {}`.

## Taller

1. Implementa `Resultado<T>` con dos casos: éxito con valor y fallo con mensaje. Agrega `map` y `orElse`.
2. Implementa `Cache<K, V>` con capacidad máxima y expulsión del más antiguo.
3. Escribe `agregarTodos(Collection<? super T> destino, Iterable<? extends T> origen)` y explica cada comodín.
4. Demuestra con código por qué `List<Integer>` no puede asignarse a `List<Number>` y qué error evitaría si se pudiera.
5. Explica por qué no compila `new T[10]` y muestra dos alternativas.

Rama `taller/n6-01-genericos`.

## Preguntas senior

1. ¿Qué es el borrado de tipos y qué limitaciones impone?
2. Explica PECS con un ejemplo que no sea `copiar`.
3. ¿Por qué los arreglos son covariantes y las listas genéricas no? ¿Qué error permite la covarianza de arreglos?
4. ¿Cuándo un método genérico es mejor que usar `Object`?
5. ¿Cómo resuelve Jackson la deserialización de `List<Pedido>` si el tipo se borra?
