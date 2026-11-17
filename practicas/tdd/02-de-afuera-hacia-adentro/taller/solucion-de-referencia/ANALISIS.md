# Taller TDD 02: devolver una bicicleta, de afuera hacia adentro

## Las reglas, con los números que el enunciado no daba

| Regla | Decisión |
|---|---|
| Media hora gratis | Hasta 30:00 exactos no se cobra. 30:01 ya inicia un bloque |
| Bloques de 15 minutos iniciados | 1000 por bloque. Se cuentan segundos: un segundo de más inicia un bloque |
| Tope diario | 12 000 por cada día de 24 horas iniciado. 25 horas pagan como máximo 24 000 |
| Abonados | Su tiempo gratis es de 60 minutos en lugar de 30 |
| Otra estación | Recargo de 2000, que no entra en el tope |
| Cobro de cero | No se registra ningún pago |
| Alquiler inexistente o cerrado | `AlquilerNoDisponible`, y no se cobra nada |

## Paso 1 y 2: los puertos que aparecieron

La primera prueba (`dadoUnAlquilerDeLasDiez...`) se escribió en el primer commit con código del historial original, antes que ninguna otra.
Para que compilara se crearon solo los tipos que nombraba:

| Puerto | Por qué apareció |
|---|---|
| `Alquileres` | La prueba necesita "un alquiler iniciado a las 10:00" que el caso de uso pueda encontrar |
| `Reloj` | "Se devuelve a las 10:50" no puede depender de la hora real |
| `Cobros` | "Se cobran dos bloques" es un efecto hacia fuera que hay que poder observar |
| `Bicicletas` | "La bicicleta queda disponible" es otro efecto hacia fuera |

## Paso 4: la lógica del precio, en funciones puras

`Tarifa.precio` y `Tarifa.recargo` no conocen puertos. `TarifaTest` las prueba sin dobles, en los límites:
30:00, 30:01, 45:00, 45:01, 60:00 y 60:01 para el abonado, 10 horas y 25 horas para el tope.

## Paso 5: la prueba atada a la implementación

`DevolverBicicletaAtadaTest` comprobaba el orden de las llamadas: buscar, hora, cobrar, guardar, liberar.
El refactor "leer la hora antes de buscar el alquiler" (dos lecturas sin efecto externo: se invierte su orden) no cambia lo que se logra, y fue la única prueba que se rompió:
en el historial original, el commit `refactor!:` rompía exactamente esa prueba y se borraba en el commit siguiente.

## Paso 6: espía hecho a mano frente a Mockito

| | `CobrosEspia` | `mock(Cobros.class)` |
|---|---|---|
| Código de apoyo | Una clase de 15 líneas | Ninguno |
| Lo que comprueba la prueba | Una lista de cobros: `assertEquals(List.of(new Cobro("ana", 4000)), ...)` | Una llamada: `verify(cobros).registrar("ana", 4000)` |
| Cuánto sabe del interior | Solo el resultado | El nombre del método y sus argumentos, y con `verifyNoMoreInteractions` que no hubo otra |

El de Mockito es más corto, pero invita a verificar llamadas. Aquí es legítimo porque el cobro **es** el resultado.

## Paso 7: tres pruebas para confiar en un repositorio real contra una base de datos

1. Guardar un alquiler y buscarlo por su identificador devuelve los mismos datos, fechas incluidas, sin perder precisión ni zona horaria.
2. Guardar el mismo alquiler cerrado sobre el abierto lo actualiza, no crea un segundo registro, y buscarlo lo devuelve cerrado.
3. Buscar un identificador que no existe devuelve vacío, no una excepción del controlador de la base.

## Paso 8: la proporción

8 pruebas usan dobles (las del caso de uso, con dobles hechos a mano o con Mockito) y 11 no usan ninguno (las de `TarifaTest`).
Las que no usan dobles son las que tienen los límites y las cuentas: la lógica quedó en funciones puras.
Las que usan dobles comprueban la orquestación, que es poca.

## Criterios de aceptación

| Criterio | Evidencia |
|---|---|
| La primera prueba es del caso de uso | En el historial original, el primer commit con código: `rojo: el caso de uso completo, en el lenguaje del negocio` |
| La lógica del precio está en funciones puras, probadas sin dobles | `Tarifa` y `TarifaTest` |
| Ninguna prueba depende de la hora real | Ninguna prueba usa `now()` ni `RelojDelSistema`; se puede comprobar con `grep -rnE 'now\(|RelojDelSistema' src/test`, que no debe encontrar nada |
| Un refactor interno no rompe ninguna prueba salvo la del paso 5 | El commit `refactor!:` y los dos `refactor:` del historial original |
