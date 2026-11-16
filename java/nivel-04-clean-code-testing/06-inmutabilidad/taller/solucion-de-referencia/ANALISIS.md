# Taller 06 — Inmutabilidad: solución

Código: `Main.java` (`java Main.java`). Cada punto tiene comprobaciones que imprimen PASA/FALLA; el programa
termina con código 1 si alguna falla. Resultado de la ejecución: 34 comprobaciones, todas pasan.

## 1. `Periodo`

`record Periodo(LocalDate inicio, LocalDate fin)`. Inmutable: `record` con campos `final` y `LocalDate`, que
también es inmutable, así que no hace falta copia defensiva. El constructor compacto rechaza `null` y un fin
anterior al inicio. Los dos extremos son inclusivos: compartir un solo día es solaparse (se prueba).
`extendidoHasta` devuelve un periodo nuevo y rechaza una fecha anterior al fin, porque eso sería acortarlo.
`equals`/`hashCode` los genera el `record` sobre los dos campos.

## 2. `Email` y `Cedula`

Ambos normalizan en el constructor (minúsculas y sin espacios en el correo; sin puntos en la cédula) y
validan, así que dos valores "iguales para el negocio" son iguales para `equals`.

- Antes: `registrar(String email, String cedula)`. `registrar("1020304050", "ana@correo.example")` compila.
- Después: `registrar(Email email, Cedula cedula)`. La misma inversión no compila.

El programa no lo afirma: compila los dos casos con el compilador del JDK (`javax.tools`) y comprueba que el
primero compila y el segundo no. El error que deja de ser posible es el de **orden de parámetros del mismo
tipo**: con dos `String`, el compilador no puede distinguirlos y el fallo aparece en producción (un correo
guardado como cédula, o una validación que falla lejos del origen).

## 3. De mutable a inmutable: `PedidoMutable` → `Pedido`

Código cliente que hubo que cambiar:

| Antes | Después | Por qué |
|---|---|---|
| `pedido.getItems().add("Teclado")` | `pedido = pedido.conItem("Teclado")` | La lista ya no se entrega para modificar: `items()` devuelve una copia inmodificable (lanza `UnsupportedOperationException`) |
| `pedido.setDireccion(...)` | `pedido = pedido.conDireccion(...)` | Un cambio produce un valor nuevo; hay que usar el valor devuelto |
| Un segundo dueño (`alias`) que modifica el objeto y el primero "ve" el cambio | Quien cambia debe devolver o publicar el pedido nuevo | Ya no hay efectos a distancia: el pedido anterior no cambia (se prueba) |
| `new PedidoMutable(id)` y luego setters | `new Pedido(id, items, direccion)` | El objeto nace completo |

La copia defensiva al construir (`List.copyOf`) se prueba modificando la lista original después de crear el pedido.

## 4. Dos hilos

- **Mutable:** un hilo mueve un `RangoMutable` alternando entre (0, 10) y (100, 110), campo por campo; el otro
  lee `inicio` y `fin` y comprueba que el ancho sea 10. Los campos son `volatile` para descartar problemas
  de visibilidad: lo que se demuestra es que el par se escribe en dos pasos. En la ejecución se observaron
  cientos de miles de lecturas con ancho distinto de 10 (por ejemplo inicio 100 y fin 10).
- **Inmutable, sin `synchronized`:** el hilo publica `Rango` nuevos (un `record`) en una referencia `volatile`.
  El lector toma la referencia una vez y lee los dos campos del mismo objeto. En 5 millones de lecturas,
  mientras el otro hilo escribía, hubo 0 incoherencias. Lo que garantiza que el objeto se vea completo es que
  sus campos son `final` (semántica de inicialización segura del modelo de memoria) y que se publica por una
  referencia `volatile`.

## 5. Clave mutable en un `HashMap`

Se inserta `ClaveMutable("CC-1")`, luego se cambia su código a `"CC-2"`. Resultado comprobado: `get` con la misma
instancia devuelve `null`, `get` con una clave igual a la original también, y el mapa sigue con tamaño 1. La
entrada quedó en el cubo del hash viejo y ya no se puede encontrar ni borrar por clave. Con `Cedula`
(inmutable) como clave, una cédula escrita con puntos encuentra la entrada guardada sin ellos.
