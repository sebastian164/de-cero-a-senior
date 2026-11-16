# Visitor — análisis

Expresión de prueba: `-(2 + 3) * (4 / 2) * 1 + 0` → evalúa −10, 12 nodos.

1. **Visitor clásico**: `Expr.aceptar(Visitante<R>)`, `Visitante<R>` con un `visitar` por tipo; operaciones `Evaluar`, `Imprimir` (con paréntesis) y `ContarNodos`.

2. **`sealed interface` + `switch`** (`Arbol`): records sin comportamiento y una función con `switch` exhaustivo por operación. Dan los mismos resultados (`lasMismasConSealed`).

3. **Agregar `Division`**: se comprobó compilando los experimentos de `src/test/resources/experimentos` con `javax.tools.JavaCompiler` (OpenJDK 21) dentro de las pruebas:
   - Visitor: al agregar `visitar(Division)` a la interfaz, **cada visitante** existente deja de compilar: `ContarNodos is not abstract and does not override abstract method visitar(Division) in Visitante`. Además hubo que crear `Division` con su `aceptar`.
   - `sealed`: al agregar `Div` a `permits`, **cada `switch`** sobre `Nodo` deja de compilar: `the switch expression does not cover all possible input values`.
   - Contraste: con `instanceof` suelto el código compila y falla en ejecución (`InstanceofSinDivision.java` compila sin errores).
   En ambas versiones el compilador enumera los lugares que hay que atender; en este taller fueron 3 operaciones (4 contando la conversión `desde`).

4. **Agregar "simplificar"** (`x * 1 → x`, `1 * x → x`, `x + 0 → x`, `0 + x → x`, `x / 1 → x`, `--x → x`): en Visitor, una clase nueva `Simplificar`; en `sealed`, una función nueva `simplificar`. **Ningún nodo cambió en ninguna de las dos.** La versión `switch` es más corta gracias a los patrones de registro (`d instanceof Num(long v) && v == 1`).

5. **Regla propia**:
   - Muchos tipos que cambian, pocas operaciones estables → **polimorfismo** (método en cada tipo).
   - Pocos tipos estables, muchas operaciones que crecen → **`sealed` + `switch`** en Java 21: mismo beneficio que Visitor (operación nueva = función nueva, tipo nuevo = el compilador avisa) sin `aceptar`, sin doble despacho y sin la interfaz `Visitante`.
   - **Visitor clásico** solo cuando no se puede usar `sealed` (Java < 17, jerarquías abiertas a otros módulos) o cuando el recorrido mismo es reutilizable y conviene encapsularlo (visitantes con estado, como `ContarNodos` acumulando).
