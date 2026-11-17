# Taller TDD 04: la calidad real de las pruebas

Sobre la kata de la sesión 01 (`kata/`) y el caso de uso de la sesión 02 (`bicis/`).
Herramientas: JaCoCo 0.8.14 (cobertura), PIT 1.30.2 con `pitest-junit5-plugin` 1.2.3 (mutación), jqwik 1.10.2 (propiedades),
JUnit 6.1.3 y Mockito 5.23.0, con JDK 21.

`medir.sh` regenera todos los números de este documento y deja los informes HTML en `informes/<proyecto>-<juego>/`;
`resumen.esperado` trae el resumen que debe imprimir. Las cifras de `*-antes` salen de las soluciones de referencia de las sesiones 01 y 02 (`../../../01-rojo-verde-refactor/taller/solucion-de-referencia` y `../../../02-de-afuera-hacia-adentro/taller/solucion-de-referencia`).

| Juego | Líneas | Ramas | Mutantes | Muertos | Sobreviven |
|---|---|---|---|---|---|
| `kata-antes`: la kata de la sesión 01, con sus 12 pruebas | 23/23 (100 %) | 12/12 | 17 | 16 | 1 |
| `kata-experimento`: una prueba sin comprobaciones (paso 5) | 25/25 (100 %) | 12/12 | 17 | 10 | 7 |
| `kata-despues`: la kata de esta sesión, con todas sus pruebas | 25/25 (100 %) | 12/12 | 17 | 17 | 0 |
| `bicis-antes`: el caso de uso de la sesión 02 | 38/42 (90 %) | 12/12 | 33 | 29 | 3 (y 1 sin cobertura) |
| `bicis-despues`: con las pruebas nuevas | 42/42 (100 %) | 12/12 | 33 | 33 | 0 |

La kata de esta sesión tiene dos líneas más que la de la sesión 01: las de la corrección del paso 6.

## Pasos 1 y 2: la medida inicial

La kata ya tenía 100 % de líneas y de ramas. Aun así, un mutante sobrevivió. El caso de uso tenía 90 % de líneas
(las cuatro que faltan son `RelojDelSistema`, que ninguna prueba usaba) y tres mutantes vivos.

## Paso 3: cada superviviente, el error real que representa y la prueba que lo mata

| Mutante | Error real que representa | Prueba que lo mata |
|---|---|---|
| Kata, línea 50: `numero < 0` pasa a `numero <= 0` | El 0 se rechazaría como negativo: `"0,1"` lanzaría un error | `MutantesTest.elCeroNoEsNegativo` |
| Tarifa, `diasIniciados`: `+ dia - 1` pasa a `+ dia + 1` | 24 horas exactas contarían como dos días y el tope sería el doble | `MutantesTest.veinticuatroHorasExactasSonUnSoloDiaDeTope` |
| Caso de uso: se quita la llamada a `alquileres.guardar(...)` | El alquiler quedaría abierto: se podría devolver y cobrar otra vez | `MutantesTest.despuesDeDevolverElAlquilerQuedaCerrado` |
| Caso de uso: `return valor` pasa a `return 0` | El caso de uso devolvería 0 aunque cobrara. Nadie miraba lo que devuelve | `MutantesTest.elCasoDeUsoDevuelveLoQueCobro` |
| `RelojDelSistema.ahora` (sin cobertura) | Podría devolver `null` | `RelojDelSistemaTest`, con un `Clock` fijo |

**¿Alguno que no se pueda matar?** Ninguno de los que generó PIT, ni con los mutadores por defecto ni con `STRONGER`
(23 mutantes sobre la kata, todos muertos). `demostraciones.sh` escribe a mano uno que sí es equivalente:
quitar el `Math.max(1, …)` de `Tarifa.diasIniciados`. Solo cambia algo cuando la duración es cero o negativa,
y ahí los bloques ya valen cero, así que el precio no cambia. Las 24 pruebas del caso de uso siguen pasando.
La única forma de distinguirlo sería una duración de menos de −24 horas, que el caso de uso no produce
salvo que el reloj retroceda. Matarlo exigiría una prueba de un caso imposible: se deja vivo y se documenta.

## Paso 4: la cobertura después

En la kata no cambió: 100 % antes y después. Pasó de 1 mutante vivo a 0 sin que la cobertura se moviera:
la cobertura no veía el hueco. En el caso de uso subió de 90 % a 100 %, pero solo por `RelojDelSistemaTest`;
los otros tres mutantes muertos estaban en líneas que ya contaban como cubiertas.

## Paso 5: el experimento contrario

`SinComprobacionesTest` llama a `sumar` con cinco entradas que recorren todas las líneas y ramas, y no comprueba nada.
Resultado: **100 % de líneas y de ramas, y 7 de 17 mutantes vivos.** Los 10 que mueren lo hacen porque el código mutado
lanza una excepción, no porque la prueba compruebe algo. Los siete supervivientes, entre ellos devolver siempre 0,
no filtrar los mayores que 1000 y no rechazar los negativos, son errores graves que una suite así dejaría pasar.
Los informes están en `informes/kata-experimento/`.

## Paso 6: propiedades

`PropiedadesTest`, con jqwik: el orden no cambia la suma, la suma es la de los que no pasan de 1000, un número
mayor que 1000 no cambia la suma, un separador declarado suma lo mismo que la coma, y lo mismo con dos separadores.

**Encontraron un error real que las doce pruebas no veían.** Con dos separadores donde uno empieza como el otro,
la kata falla. El caso reducido fue `numeros [0, 0]`, separadores `";"` y `";*"`: `sumar("//[;][;*]\n0;*0")`
lanzaba `NumberFormatException: For input string: "*0"`, porque la expresión regular probaba `;` antes que `;*`.
Se fijó con dos pruebas normales en `RegresionTest`, se vieron fallar y se corrigió ordenando los separadores
de más largo a más corto. Por eso el `src/main` de esta sesión difiere del de la sesión 01.

## Paso 7: olores en las pruebas de las sesiones 01 a 03

| Olor | Dónde | Corregido |
|---|---|---|
| Varias razones para fallar | La primera prueba de `DevolverBicicletaTest` comprobaba el cobro y la bicicleta | Sí: son dos pruebas en `bicis/` |
| Copia la fórmula del código | `DevolverBicicletaTest`: `2 * 1000 + 2000` | Sí: `4000` |
| Copia la fórmula del código | `TarifaTest.elRecargoNoCuentaParaElTope`: `12_000 + 2000` | Sí: `14_000` |
| Copia la fórmula del código | Sesión 03, `ReglasTest`: `9000 * 0.8` | No: se deja como ejemplo, es la cuarta |
| Depende de otras pruebas | Sesión 03, `SalirTest`: la caja es estática y se mide por diferencia | No: arreglarlo exige quitar el estado estático del parqueadero |

## Paso 8: corregir un error con TDD

`demostraciones.sh` introduce el error `numero < 0` → `numero <= 0` en una copia. Las 12 pruebas de la kata siguen
pasando, porque ninguna tiene un cero. La prueba que lo reproduce, `elCeroNoEsNegativo`, falla con el error y pasa
sin él. El orden importa: si se arregla primero, nunca se ve que la prueba detecta el error, y una prueba que no se
vio fallar puede estar pasando por la razón equivocada.

## Paso 9: dónde no usaría TDD

`RelojDelSistema`: es una línea que delega en `LocalDateTime.now(clock)`. No hay lógica que diseñar.
Basta una prueba con un `Clock` fijo después de escribirlo. Lo mismo para un adaptador de base de datos:
ahí la confianza viene de una prueba de integración contra la base real, como las tres del paso 7 de la sesión 02.

## Paso 10: con un asistente de IA

No se hizo en esta solución: la escribió un asistente de IA, así que no hay forma honesta de comparar "con pruebas"
y "sin pruebas" como pide el paso. Queda para ti: hazlo con tu propio asistente y mide con PIT.

## Paso 11: la política de pruebas

Está en [POLITICA.md](POLITICA.md).
