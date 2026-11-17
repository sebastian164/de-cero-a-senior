# Taller n3-02 — Strategy y Factory con inyección

| # | Solución | Prueba |
|---|---|---|
| 1 | `documentos.ts`: `VALIDADORES_DE_DOCUMENTO` (token `multi`), `CedulaColombia`, `DniArgentina`, `ValidacionDeDocumentos` (contexto que no conoce países) | `taller.spec.ts` 1 |
| 2 | `pais-nuevo.ts`: `RutChile` (con dígito verificador) y su proveedor | 2: además lee `documentos.ts` y comprueba que no menciona `CL` |
| 3 | `documentos-funciones.ts`: `Record<Pais, Validar>` | 3 |
| 4 | `provideDocumentos({ paises, extra })` con `makeEnvironmentProviders` | 1, 2, 5 y la ruta |
| 5 | Un doble registrado como una estrategia más | 5 |

## 3. Comparación

| | Clases + token `multi` | `Record` de funciones |
|---|---|---|
| Líneas | ~45 | 7 |
| Agregar un país | Archivo nuevo + proveedor; el servicio no cambia; puede vivir en otra funcionalidad o en un módulo diferido | Editar el `Record` y la unión `Pais`; el compilador obliga a completar (lo comprueba el caso `n3-02-record-incompleto` de compilación negativa) |
| Dependencias | Una estrategia puede inyectar (`HttpClient` para validar contra un registro oficial) | Ninguna |
| Sustituir en pruebas | Proveedor | Hay que pasar el `Record` como parámetro |
| Saber qué países hay | En ejecución (`paises()`) | En el tipo |

Para tres validaciones puras, el `Record` gana. El token `multi` se justifica si un país necesita dependencias o si los países los aportan equipos o módulos distintos.

## Lo que se encontró al montarlo

`ValidacionDeDocumentos` es `providedIn: 'root'`. Si las estrategias se registran solo en la ruta y no se vuelve a proveer el servicio ahí, el servicio se crea en el inyector raíz y `inject(VALIDADORES_DE_DOCUMENTO)` falla. Por eso `provideDocumentos` incluye `ValidacionDeDocumentos` en la misma lista. Lo mismo pasa con el ejemplo del README (`CotizadorEnvio` + `provideEnvios()`): funciona si `provideEnvios()` se registra en la configuración de la aplicación (el README no dice dónde; en la pantalla de la solución se vuelve a proveer `CotizadorEnvio` en la ruta).
