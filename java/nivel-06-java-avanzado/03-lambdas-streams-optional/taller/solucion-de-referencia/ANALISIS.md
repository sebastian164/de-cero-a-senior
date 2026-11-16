# Taller 06-03 — Lambdas, streams y `Optional` (solución)

`Consultas.java` con 7 pruebas sobre un listado fijo (`mvn test`).

1. **Total por categoría ordenado**: `groupingBy` + `summingLong`, y un segundo stream que ordena por valor descendente y recoge en `LinkedHashMap` para conservar el orden.
2. **Cliente con más transacciones del último mes**: filtro `[hoy − 1 mes, hoy]`, `groupingBy` + `counting`, `max`. Decisión explícita para empates: gana el primero en orden alfabético (la prueba cubre el empate, el desempate y la lista vacía).
3. **Promedio por tipo**: `groupingBy` + `averagingLong`.
4. **Partición por umbral**: `partitioningBy(t -> t.valor() > umbral, counting())`.
5. **Con bucles** (`totalPorCategoriaConBucles`): 15 líneas con `merge`, una lista intermedia y un `sort` frente a 6 del stream. El stream dice qué se calcula; los bucles obligan a leer cómo. La prueba comprueba que dan el mismo mapa en el mismo orden. Aquí gana el stream; el bucle ganaría si hubiera que salir temprano o acumular varios resultados a la vez.
6. **`parallelStream` incorrecto**: `IntStream.range(0, n).parallel().forEach(i -> acumulado[0] += 1)` pierde incrementos porque `+=` no es atómico. La prueba lo ejecuta con 5.000.000 de elementos hasta 20 veces y exige observar al menos un resultado distinto de `n` (en la práctica falla en el primer intento); la versión sin estado compartido (`mapToLong(...).sum()`) siempre da `n`.
7. **`Optional` en lugar de nulls anidados**: `paisConNulls` (cuatro `if` anidados) frente a `paisConOptional` (`ofNullable` + cuatro `map` + `orElse`). La prueba verifica que ambas dan lo mismo en cuatro casos, incluido el cliente `null`.
