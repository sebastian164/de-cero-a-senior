# Builder — análisis

## 1. Versión telescópica
`SolicitudCreditoTelescopica` tiene cuatro constructores encadenados. La llamada de ejemplo es
`new SolicitudCreditoTelescopica("Ana", 80_000_000L, 72, new BigDecimal("2.1"), "Luis", "Seguro Vida", null)`:
no se sabe qué es cada `String` ni qué significa el `null`, y para dar solo la cuenta hay que pasar todo lo anterior.

## 2. Builder a mano
`SolicitudCredito` es inmutable (todos los campos `final`, sin setters; lo comprueba `esInmutable`) y solo se construye con `SolicitudCredito.builder(cliente, monto)...build()`.

## 3. Dónde se validan las reglas cruzadas
En `build()`. Si "monto > 50 M exige codeudor" se validara en `codeudor(...)` o en el constructor del builder, el resultado dependería del orden de las llamadas: `builder("Ana", 80M)` fallaría antes de que el cliente pudiera llamar a `.codeudor(...)`. En `build()` todos los datos ya están; la prueba `elOrdenDeLasLlamadasNoImporta` lo demuestra. Las validaciones de un solo campo podrían ir en el método del builder (falla antes, más cerca del error), pero se dejaron juntas para tener un único lugar.

## 4. Obligatorios en `builder(...)` o como métodos
- **En `builder(cliente, monto)`** (elegido): el compilador impide olvidarlos; no hace falta validar "falta el cliente" en ejecución. En contra: si los obligatorios fueran muchos y del mismo tipo, `builder(...)` vuelve a ser un constructor ilegible.
- **Como métodos (`.cliente(..).monto(..)`)**: lectura uniforme y con nombre. En contra: olvidarlos solo se detecta en `build()`, en ejecución.

## 5. Con Lombok `@Builder`
`@Builder` en la clase genera un `build()` que llama al constructor con todos los campos **sin validación**: `SolicitudCredito.builder().monto(-5).plazoMeses(200).build()` produce un objeto inválido, y los valores por defecto (`plazo = 12`) se pierden salvo que se marque `@Builder.Default`. Se recupera poniendo `@Builder` sobre un **constructor** que valida, o validando en el constructor canónico de un `record`.

Se comprobó con Lombok real en un proyecto de pruebas (prueba `LombokBuilderTest`): el `@Builder` sobre la clase acepta monto negativo y plazo 0 (sin `@Builder.Default` el plazo queda en 0, no en 12), y el `@Builder` sobre un constructor que valida lanza la excepción.
