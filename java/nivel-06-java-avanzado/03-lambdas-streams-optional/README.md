# 03 — Lambdas, streams y `Optional`

## Idea

Hay dos formas de pedir un café. Imperativa: "camina a la máquina, pon el filtro, agrega 18 gramos…". Declarativa: "un espresso doble". La segunda dice **qué** quieres y deja el **cómo** a quien sabe. Los streams permiten procesar datos diciendo qué se quiere obtener.

## Lo esencial

### Lambdas e interfaces funcionales

Una lambda es una función sin nombre que implementa una interfaz de un solo método abstracto.

| Interfaz | Forma | Uso |
|---|---|---|
| `Function<T, R>` | `T -> R` | Transformar |
| `Predicate<T>` | `T -> boolean` | Filtrar |
| `Consumer<T>` | `T -> void` | Efecto |
| `Supplier<T>` | `() -> T` | Proveer, evaluación diferida |
| `BiFunction<T, U, R>` | `(T, U) -> R` | Combinar |

Referencias a métodos: `Pedido::total`, `String::isBlank`, `Pedido::new`. Una lambda solo puede capturar variables efectivamente finales.

### Streams

Una tubería: **origen** → operaciones **intermedias** (perezosas) → operación **terminal** (dispara la ejecución).

- Intermedias: `filter`, `map`, `flatMap`, `distinct`, `sorted`, `limit`, `peek`.
- Terminales: `toList`, `collect`, `reduce`, `count`, `findFirst`, `anyMatch`, `forEach`.
- Un stream se consume una sola vez.
- Las operaciones deben ser sin efectos secundarios y sin estado compartido.

### `Optional`

Representa un valor que puede no estar. Sirve como **tipo de retorno**; no como atributo ni como parámetro.

- Bien: `orElse`, `orElseGet`, `orElseThrow`, `map`, `flatMap`, `ifPresent`.
- Mal: `optional.get()` sin comprobar; `if (optional.isPresent()) optional.get()` (es un `null` con más pasos).

## Ejemplo

```java
record Pedido(String cliente, String ciudad, long total, boolean pagado) { }

// Total facturado por ciudad, solo pedidos pagados
Map<String, Long> totalPorCiudad = pedidos.stream()
        .filter(Pedido::pagado)
        .collect(Collectors.groupingBy(Pedido::ciudad, Collectors.summingLong(Pedido::total)));

// Los tres clientes con el pedido más alto
List<String> mejores = pedidos.stream()
        .sorted(Comparator.comparingLong(Pedido::total).reversed())
        .map(Pedido::cliente)
        .distinct()
        .limit(3)
        .toList();

// Optional encadenado en lugar de comprobaciones de null anidadas
String ciudad = repositorio.buscar(id)
        .map(Pedido::ciudad)
        .filter(c -> !c.isBlank())
        .orElse("Sin ciudad");
```

## Errores comunes

- Efectos secundarios dentro de `map` o `filter` (modificar una lista externa).
- Tuberías de quince pasos con lambdas de varias líneas: extrae métodos con nombre.
- `parallelStream()` "para ir más rápido" sin medir: usa un pool compartido, y con operaciones de E/S o listas pequeñas suele ir peor.
- `orElse(calculoCostoso())`: se evalúa siempre. Usa `orElseGet`.
- `Optional` como campo de una entidad o como parámetro.
- Usar streams donde un `for` es más claro.

## En Spring

Los repositorios devuelven `Optional<T>`. Métodos de repositorio que devuelven `Stream<T>` requieren transacción abierta y cierre (`try-with-resources`). Las APIs funcionales de `RestClient`, `WebClient` y la configuración de seguridad usan lambdas extensamente.

## Taller

Dado un listado de transacciones (cliente, fecha, tipo, valor, categoría):

1. Total por categoría, ordenado de mayor a menor.
2. El cliente con más transacciones del último mes.
3. Promedio de valor por tipo.
4. Particionar en mayores y menores a un umbral y contar cada grupo.
5. Reescribe una de las consultas con bucles y compara legibilidad.
6. Escribe un ejemplo donde `parallelStream` dé un resultado incorrecto por estado compartido.
7. Reescribe un método con tres comprobaciones de `null` anidadas usando `Optional`.

Rama `taller/n6-03-streams`.

## Preguntas senior

1. ¿Qué significa que un stream sea perezoso y qué ventaja tiene?
2. ¿Cuándo usarías `parallelStream` y qué riesgos tiene en una aplicación web?
3. Diferencia entre `map` y `flatMap`, en streams y en `Optional`.
4. ¿Por qué `Optional` no debe usarse como campo o parámetro?
5. ¿Cuándo un bucle `for` es mejor que un stream?
