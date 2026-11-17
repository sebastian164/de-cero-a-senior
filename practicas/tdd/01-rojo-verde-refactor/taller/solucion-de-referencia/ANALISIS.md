# Taller TDD 01: calculadora de cadenas

## Lista de pruebas, escrita antes de empezar

De más simple a más compleja. Las marcadas con (+) se añadieron por el camino.

1. `""` da 0
2. `"7"` da 7
3. `"1,2"` da 3
4. `"1,2,3,4,5"` da 15
5. `"1\n2,3"` da 6
6. `"//;\n1;2"` da 3
7. `"1,-2"` lanza un error que dice `-2`
8. (+) `"-1,2,-3"` lanza un error que dice `-1, -3`. Apareció al ver que el verde del 7 solo nombraba el primer negativo
9. `"2,1001"` da 2
10. (+) `"2,1000"` da 1002: el límite exacto. Sin ella, cambiar `<=` por `<` pasaría inadvertido
11. `"//[***]\n1***2***3"` da 6
12. `"//[*][%]\n1*2%3"` da 6

## Decisiones que el enunciado no tomaba

| Pregunta | Decisión | Prueba que la fija |
|---|---|---|
| Con un separador declarado, ¿siguen valiendo la coma y el salto de línea? | Sí: el declarado se suma a los de siempre | Ninguna todavía: está en la lista de pendientes |
| ¿Qué dice el error de negativos? | `negativos no permitidos: -1, -3`, en el orden en que aparecen | `elErrorListaTodosLosNegativos` |
| ¿1000 cuenta? | Sí. "Mayores que 1000" excluye solo a partir de 1001 | `milTodaviaCuenta` |

Pendientes que no pidió ningún requisito y quedaron fuera: `"1,"` (separador al final), texto que no es número, desbordamiento de `int`.

## El historial

Esta carpeta guarda solo el estado final. El historial de la solución original tuvo 11 commits `rojo:`, 11 `verde:` y 5 `refactor:`, más dos de documentación.
Cada `rojo:` compilaba y dejaba exactamente una prueba fallando, cada `verde:` las dejaba todas pasando sin tocar las pruebas,
y ningún `refactor:` se hizo con una prueba en rojo. Ese es el ritmo que tu propio historial debería mostrar.

### Paso 11: ¿pocos refactor?

5 refactor para 11 verdes. Revisando el código final no queda duplicación evidente; la que hubo
(dos caminos casi iguales para leer separadores con y sin corchetes) la quitó el último refactor.

### Paso 12: el refactor del que estoy más orgulloso

"Un solo camino para leer los separadores declarados". Tras el verde de los varios separadores había dos ramas,
un `Matcher` en bucle y una lista intermedia. Lo que permitió simplificarlo sin miedo fueron las doce pruebas en verde:
cubrían los tres formatos de cabecera (`//;`, `//[***]` y `//[*][%]`), así que bastaba con ejecutarlas tras cada cambio.

### La regla de los cinco minutos

No hubo que deshacer ningún paso: el más largo fue el de varios separadores.

### Paso 13: repetir la kata otro día

No se puede hacer en una sola sesión; queda para ti: repite la kata otro día y compara.
