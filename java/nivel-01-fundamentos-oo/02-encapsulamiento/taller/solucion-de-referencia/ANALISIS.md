# Taller — Encapsulamiento: el carrito (solución de referencia)

Ejecución: `javac -d out *.java && java -cp out Main && java -cp out Pruebas` (14 pruebas, 0 fallos).
`diagnostico/DemoViolaciones.java` se compila con `../inicio/Main.java`: `javac -d out2 diagnostico/DemoViolaciones.java ../inicio/Main.java && java -cp out2 DemoViolaciones`. Los archivos de `no-compila/` deben fallar al compilarse contra la solución: `javac -d out3 Carrito.java Linea.java no-compila/SetTotal.java` (y así con cada uno).

## 1. Una línea que viola cada regla en la versión original

| Regla | Línea | Resultado real |
|---|---|---|
| Cantidades positivas | `c.getLineas().add(new Linea("Pan", -3, 4_000));` o `linea.cantidad = 1_000;` | Se acepta. |
| Total = suma de las líneas | `c.setTotal(1);` | El total queda en 1. |
| Pagado no admite cambios | `c.setPagado(true); c.getLineas().add(new Linea("Queso", 1, 22_000));` | La línea se agrega. |
| No se paga un carrito vacío | `new Carrito().setPagado(true);` | Queda pagado. |

Salida de la demostración: `líneas=2 total=1 pagado=true vacío pagado=true`.

## 2. Refactorización

| Línea original | Ahora |
|---|---|
| `c.setTotal(1)` | No compila: no existe `setTotal` (`no-compila/SetTotal.java`). |
| `c.getLineas().add(...)` | No compila: no existe `getLineas`; `lineas()` devuelve una vista inmodificable que lanza `UnsupportedOperationException` (`no-compila/AgregarPorLista.java`). |
| `linea.cantidad = -3` | No compila: los campos son privados y finales (`no-compila/CampoCantidad.java`). |
| `new Linea("Pan", -3, ...)` / `agregar("Pan", -3, ...)` | Compila y falla: `IllegalArgumentException: La cantidad debe ser positiva: -3`. |
| `c.setPagado(true)` | No compila (`no-compila/SetPagado.java`). Se paga con `pagar()`, que rechaza el carrito vacío con `IllegalStateException`. |
| Agregar o quitar después de pagar | `IllegalStateException: El carrito ya está pagado y no admite cambios`. |

## 3. `total`: atributo o cálculo

Cálculo. Como atributo habría que mantenerlo sincronizado en cada `agregar` y `quitar`, y cualquier camino nuevo que olvide actualizarlo rompe la regla "el total es la suma". Calculado, la regla se cumple por construcción. El costo es recorrer las líneas en cada consulta, despreciable para un carrito. Si el total tuviera que congelarse (por ejemplo, precios que cambian después del pago), se guardaría una instantánea al pagar, no un campo editable.

## 4. Mismo producto dos veces

Una línea, con la cantidad sumada. Es lo que espera quien compra (el carrito muestra "Café x3", no dos filas de café) y simplifica quitar un producto: se quita una línea, no varias. Si el mismo producto llega con otro precio se rechaza, porque fusionar ocultaría una diferencia de precio. Otra decisión defendible: dos líneas cuando el precio difiere (por ejemplo, una promoción); se descartó porque hoy no hay reglas de promociones.
