# Taller 06-01 — Genéricos (solución)

`mvn test` ejecuta 7 pruebas. Los puntos 4 y 5 se demuestran **compilando código en memoria** con `javax.tools.JavaCompiler` y comprobando el mensaje de error real de `javac` 21.

1. **`Resultado<T>`** (`Resultado.java`): interfaz `sealed` con `Exito<T>` y `Fallo<T>`; `map(Function<? super T, ? extends U>)` y `orElse`. El `switch` es exhaustivo sin `default`.
2. **`Cache<K, V>`** (`Cache.java`): `LinkedHashMap` en orden de inserción con `removeEldestEntry`. Leer no cambia la antigüedad (si se quisiera LRU, sería `accessOrder = true`).
3. **`agregarTodos(Collection<? super T> destino, Iterable<? extends T> origen)`**:
   - `? extends T` en el origen: el método solo **lee** de él (productor). Acepta `List<Integer>` cuando `T = Number`.
   - `? super T` en el destino: el método solo **escribe** en él (consumidor). Acepta `List<Object>` cuando `T = Integer`.
4. **Invarianza**: `List<Number> numeros = enteros;` no compila — *"incompatible types: java.util.List<java.lang.Integer> cannot be converted to java.util.List<java.lang.Number>"*. Si compilara, `numeros.add(3.14)` metería un `Double` en una lista de enteros y el error saldría lejos, al leer un `Integer`. Con arreglos sí compila, y la prueba muestra el `ArrayStoreException` en ejecución: es el error que la invarianza evita.
5. **`new T[10]`** no compila — *"generic array creation"*. Por el borrado, en ejecución `T` es `Object` y la JVM no sabría qué tipo de arreglo crear ni podría verificar sus escrituras. Alternativas implementadas: `Array.newInstance(Class<T>, n)` (token de tipo), un generador `IntFunction<T[]>` (`String[]::new`, como `Collection.toArray`) y usar `List<T>`.
