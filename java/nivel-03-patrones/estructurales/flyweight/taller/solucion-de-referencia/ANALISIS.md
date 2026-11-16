# Flyweight — análisis

Medición real (OpenJDK 21.0.13, macOS arm64, heap por defecto, `java -cp target/classes taller.flyweight.Main`), 200.000 caracteres, memoria retenida = `totalMemory - freeMemory` después de 5 × `System.gc()` con el documento vivo menos la misma medida antes:

| Versión | Bytes retenidos | Bytes por carácter |
|---|---|---|
| Sin compartir (`CaracterPesado`: letra, `String` fuente propio, tamaño, `ColorRgb` propio, fila, columna) | 18.464.328 | 92,3 |
| Flyweight (`Caracter`: letra, fila, columna + referencia a `Formato` compartido) | 7.335.968 | 36,7 |

Ahorro ≈ 60 %. La cifra exacta varía entre ejecuciones (el GC es una sugerencia); la prueba `flyweightUsaMuchaMenosMemoria` exige que la versión ligera use menos del 60 % de la pesada. Las cuentas cuadran con el tamaño de los objetos en una JVM de 64 bits con *compressed oops*: `CaracterPesado` 40 B + `String` 24 B + `ColorRgb` 24 B + 4 B de la referencia en el arreglo ≈ 92 B; `Caracter` 32 B + 4 B ≈ 36 B. Solo se crean **2** `Formato` (texto y título).

1. Sin compartir: ver tabla.
2. Con Flyweight: el formato (fuente, tamaño, color) es estado intrínseco y se pide a `FabricaFormatos`, que devuelve la instancia existente (`ConcurrentHashMap.computeIfAbsent`, seguro entre hilos). La posición y la letra son extrínsecas.
3. **Cambiar el color de un formato compartido** cambiaría el color de todos los caracteres que lo usan. Se impide haciendo `Formato` un `record` (campos `final`, sin setters). Para "cambiar el color" de un carácter, `conColor(...)` devuelve un formato nuevo que se pide a la fábrica (`elFormatoCompartidoEsInmutable`).
4. **`Integer` 127 frente a 128**: `Integer a = 127` compila a `Integer.valueOf(127)`, que devuelve una instancia de la caché de −128 a 127; `a == b` compara referencias y da `true` porque es el mismo objeto. Con 128, `valueOf` crea objetos nuevos y `==` da `false` aunque `equals` dé `true` (salida real: `127 == 127: true | 128 == 128: false | 128 equals 128: true`). Error real: `EnterosEnCache.mismoCliente(Integer, Integer)` compara ids con `==`; las pruebas con ids pequeños pasan y en producción, con el cliente 5000, dice que no es el mismo cliente. Corrección: `equals` o `Objects.equals`, o usar `int`.
