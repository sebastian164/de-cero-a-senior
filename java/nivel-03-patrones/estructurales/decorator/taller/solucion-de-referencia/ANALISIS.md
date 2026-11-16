# Decorator — análisis

1. **Decoradores**: `ConCache`, `ConCifrado` (César, desplazamiento configurable) y `ConRegistroTiempos` (recibe una `Bitacora` para poder probarlo). Todos implementan `FuenteDatos` y envuelven otra `FuenteDatos`.

2. **Orden entre caché y cifrado** (probado en `ordenA_...` y `ordenB_...`):
   - **A: `ConCache(ConCifrado(memoria))`**: la caché guarda el **texto plano** (`"secreto"`). Ahorra descifrar en cada lectura, pero deja el secreto en claro en memoria, que es justamente lo que el cifrado quería evitar.
   - **B: `ConCifrado(ConCache(memoria))`**: la caché guarda el **texto cifrado** (`"vhfuhwr"`). Cada lectura descifra (barato), pero nada en claro queda guardado.

   Ambas devuelven el valor correcto. La correcta es **B**: si el requisito es "los valores se guardan cifrados", la caché también es un almacenamiento y debe estar del lado cifrado. A solo es aceptable si el cifrado protege el disco y no la memoria del proceso, y descifrar es caro.

3. **Escritura con caché**: `ConCache.escribir` escribe en el envuelto e **invalida** la entrada. No la actualiza con el valor recibido, porque el envuelto puede transformarlo (en el orden B, el valor que llega a la caché ya está cifrado; en otros casos podría normalizarse). Sin invalidar, la siguiente lectura devolvería el valor viejo (`laCacheEvitaLecturasYSeInvalidaAlEscribirYEliminar`).

4. **Cuarto método (`eliminar`)**: hubo que tocar **5 tipos**: la interfaz y las 4 implementaciones (memoria y los 3 decoradores), aunque a `ConCifrado` no le interesa y solo reenvía. Las interfaces que se decoran deben ser pequeñas; con muchas operaciones, el costo de cada decorador crece linealmente. (Mitigación: una clase base `FuenteDatosDelegada` que reenvía todo, a costa de herencia.)

5. **Registro frente a un aspecto de Spring**: el decorador se arma a mano, es explícito y visible en el código que ensambla, funciona sin contenedor, solo aplica a `FuenteDatos` y hay que repetir el patrón para cada método. Un `@Around` de Spring AOP se declara una vez con un *pointcut* y se aplica a muchos beans y métodos, pero solo funciona sobre beans (no con `new`), solo intercepta llamadas que pasan por el proxy (no la autoinvocación ni métodos `private`/`final`) y el comportamiento no se ve leyendo la clase. Esas limitaciones del proxy se verificaron con `@Transactional` (mismo mecanismo de proxy que un aspecto `@Around`) en un proyecto Spring de pruebas (`ProxyAutoinvocacionTest`); además se ejecutó un aspecto `@Around` propio que mide tiempos (`AspectoAroundTest`, con `aspectjweaver`): mide la llamada externa a un bean y no se aplica a un objeto creado con `new`, a la autoinvocación ni a un método `final`.
