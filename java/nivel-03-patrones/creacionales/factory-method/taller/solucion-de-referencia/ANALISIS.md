# Factory Method — análisis

1. **Contrato**: `LectorExtracto.leer(String) -> List<Movimiento>`. Implementaciones: `LectorCsv`, `LectorXml`, `LectorAnchoFijo` (fecha 8, descripción 22, signo 1, valor 10). Simulan la lectura a partir de un texto.

2. **Extensión desconocida**: la fábrica lanza `FormatoNoSoportadoException` con la lista de extensiones soportadas. Se descartó devolver `null` (el error aparece lejos, como `NullPointerException`) y un lector "vacío" (un extracto sin movimientos parecería válido y el usuario creería que no tuvo movimientos). Fallar rápido es lo correcto en un proceso de importación.

3. **Cuarto formato (JSON)**: con un `switch`, agregar JSON obligaría a modificar la fábrica. Con el registro `Map<String, Supplier<LectorExtracto>>` solo se agrega `LectorJson` y una llamada a `registrar("json", LectorJson::new)`. La clase `FabricaLectores` no cambia: lo prueba `registrarUnFormatoNoModificaLaFabrica`, que registra `ofx` desde afuera.

4. **`Movimiento.ingreso(...)` / `Movimiento.egreso(...)`** frente a `new Movimiento(fecha, desc, valor, true)`: el nombre dice la intención, el signo lo pone el método (con las fábricas no se puede crear un "egreso positivo") y cada uno valida su caso. Matiz: `Movimiento` es un `record`, y un record no puede ocultar su constructor canónico, así que `new Movimiento(fecha, desc, valor)` sigue disponible (las pruebas lo usan para comparar). Para que las fábricas fueran la única entrada habría que usar una clase `final` con constructor privado. Un `boolean` en el constructor no se lee en la llamada.

5. **En Spring** no escribiría la fábrica: cada lector sería un `@Component` que declara su extensión (`String extension()`), y un servicio recibiría `List<LectorExtracto>` inyectada y armaría el mapa en el constructor. Agregar un formato es agregar una clase anotada; el contenedor hace de registro.
