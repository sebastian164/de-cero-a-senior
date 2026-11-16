# Adapter — análisis

1. **Adaptadores**: `AdaptadorClimaTexto` (parsea `"TUNJA|53.6F|RAIN:40"`, F→C con `(F-32)*5/9`, `RAIN:40` → 0.40) y `AdaptadorClimaMapa` (`temp_k - 273.15`, `pop/100`). Ambos devuelven el mismo `Pronostico(12.0, 0.40)` para Tunja (prueba parametrizada).

2. **Ciudad inexistente**: el contrato lanza `CiudadNoEncontradaException`, no comprobada y del dominio. El adaptador de texto la lanza al recibir `ERR|404`; el de mapa traduce la `CityNotFoundException` comprobada del SDK. Ninguna excepción ni tipo del proveedor cruza el adaptador. Se descartó devolver `null` o un `Pronostico` por defecto (una temperatura falsa es peor que un error).

3. **Recomendador**: depende solo de `ServicioClima`; la misma clase se prueba con ambos adaptadores (`elRecomendadorFuncionaConAmbosSinCambiar`).

4. **Información perdida**: la precisión (se redondea a una décima), la estación meteorológica (`station`) del proveedor de mapa, el nombre de ciudad normalizado que devuelve el de texto, y el código de error concreto (404). Es aceptable porque el negocio solo decide sombrilla; si mañana se necesita la estación, se agrega al `Pronostico` y se adapta, sin tocar al proveedor.

5. **Reintentos**: **no** van en el adaptador. Son un comportamiento transversal y combinable, así que se implementaron como decorador `ConReintentos` sobre el puerto: sirven para cualquier proveedor, y el adaptador sigue con una sola responsabilidad (traducir). El adaptador sí colabora traduciendo el fallo transitorio del SDK a `ClimaNoDisponibleException`, que es lo único que se reintenta; `CiudadNoEncontrada` no se reintenta (la prueba verifica 1 llamada).
