# Taller 06-06 — Java moderno (solución)

`mvn test` ejecuta 6 pruebas con JDK 21; la del punto 6 ejecuta además `post21/Novedades.java` con JDK 27.0.1 (el más reciente instalado).

1. **Formas de pago** (`FormaDePago.java`): interfaz `sealed` con `Tarjeta`, `Transferencia`, `Efectivo` y `Billetera`; `comision` es un `switch` exhaustivo sin `default`, con patrones de `record` y guardas `when` (AMEX 3,5 %; tarjeta en cuotas 2,9 % + 500 por cuota; transferencia gratis en el mismo banco y 7.000 a otro; efectivo 3.500 fijo; billetera 1,5 % con mínimo 1.000).
2. **Agregar una forma de pago**: la prueba agrega `Cripto` a la jerarquía sellada sin tocar el `switch` y compila el resultado con `javax.tools`. Deja de compilar exactamente un punto, `FormaDePago.comision`, con el error *"the switch expression does not cover all possible input values"*. Con `default` ese error no aparecería y `Cripto` caería en un caso equivocado.
3. **Clase manual → `record`** (`ClienteManual` → `Cliente`): de 27 líneas a 5; `equals`, `hashCode` y `toString` equivalentes (probado). Lo que se perdió: los nombres JavaBean (`getNombre()` pasa a `nombre()`, lo que rompe a quien use reflexión por convención de getters), la posibilidad de heredar o de ser extendida, el constructor sin argumentos que piden JPA y algunos frameworks, y el control sobre qué entra en `equals` y `toString` (ahora son todos los componentes; si uno fuera un dato sensible, aparecería en los registros).
4. **Patrones**: `describirAntes` (cadena de `if/else instanceof` + conversiones, 16 líneas) frente a `describir` (un `switch` de 5 casos con patrones de `record` y guardas). La prueba comprueba que dan lo mismo para cinco figuras.
5. **Visitor clásico** (`ComisionVisitor`): interfaz `Visitante<R>` con un método por tipo, `aceptar` en cada clase. Da los mismos resultados (probado), pero exige ~40 líneas de doble despacho frente a un `switch`. Visitor conserva una ventaja: funciona con jerarquías que no se pueden sellar (de otra biblioteca o abiertas a extensión). Con `sealed` + `switch`, agregar un tipo hace fallar la compilación en cada `switch`, igual que agregar un método a `Visitante`, sin la ceremonia.
6. **Tres novedades posteriores a Java 21**, ejecutadas con JDK 27 en `post21/Novedades.java`:
   - **Variables y patrones sin nombre `_`** (JEP 456, definitiva en Java 22): `instanceof Punto(int x, _)` y `for (var _ : lista)`. Elimina variables que nadie usa.
   - **Stream gatherers** (JEP 485, Java 24): `stream.gather(Gatherers.windowSliding(2))` → `[[1, 2], [2, 3], [3, 4]]`. Operaciones intermedias propias sin colectores retorcidos.
   - **Cuerpos de constructor flexibles** (JEP 513, Java 25): validar argumentos antes de `super()`.
   
   Java 25 es la LTS siguiente a la 21; es la versión a la que apuntaría una migración hoy.
