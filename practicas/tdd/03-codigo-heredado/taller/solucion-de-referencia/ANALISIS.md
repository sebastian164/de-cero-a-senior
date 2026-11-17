# Taller TDD 03: el parqueadero heredado

## Paso 1: obstáculos al intentar probar `salir` sin tocar nada

| Obstáculo | Por qué impide la prueba |
|---|---|
| `LocalDateTime.now()` en `entrar` y en `salir` | Entre las dos llamadas pasan milisegundos: siempre son 0 minutos. El domingo y la noche dependen del día y la hora en que corra la prueba |
| Estado estático (`entradas`, `tipos`, `caja`) | Las pruebas se contaminan entre sí; `caja` acumula lo de todas |
| `System.out.println` en mitad del cálculo | No molesta para el resultado, pero ensucia y no hay forma de separarlo del cálculo |
| El cálculo está dentro de `salir` | Para probar una tarifa hay que registrar una entrada y esperar de verdad |

## Paso 2: las reglas que creo que tiene, leyendo el código antes de probar nada

1. Carro: los primeros 15 minutos gratis; después, 4500 por hora o fracción.
2. Carro: tope de 30 000 a partir de las 8 horas.
3. Moto: igual, con 1500 por hora y tope de 10 000.
4. Otros vehículos: 50 por minuto.
5. Domingo: 20 % de descuento.
6. Sello: descuenta 3000, sin bajar de 0.
7. De noche (de 22:00 a 6:00) se suman 2000.

## Paso 4: la muestra de referencia

`src/test/resources/muestra.txt`, generada con `GenerarMuestra` sobre el código sin refactorizar:
tipo (`CARRO`, `MOTO` y uno cualquiera, `BICICLETA`), minutos a cada lado de cada límite
(0, 1, 14, 15, 16, 59, 60, 61, 119, 120, 121, 360, 361, 420, 421, 479, 480, 481, 540, 541, 1440),
salida en sábado, domingo y lunes, a las 5:59, 6:00, 12:00, 21:59 y 22:00, con y sin sello.

## Paso 6: comportamientos sospechosos (no se corrigen al caracterizar)

| # | Caso exacto | Qué hace | Pregunta al negocio |
|---|---|---|---|
| 1 | `CARRO;480;2026-11-16T12:00;false` | Cobra 36 000; con 481 minutos cobra 30 000 | ¿El tope de 30 000 debía aplicarse en cuanto la cuenta lo supera? Hoy quedarse 8 horas cuesta más que quedarse 9 |
| 2 | `MOTO;420;2026-11-16T12:00;false` | Cobra 10 500, más que el tope de 10 000 que se cobra con 541 minutos | La misma pregunta para motos |
| 3 | `CARRO;10;2026-11-16T22:00;false` | Cobra 2000: el recargo nocturno se suma aunque el tiempo sea gratis | ¿Quien sale de noche dentro de los 15 minutos gratis debe pagar 2000? |
| 4 | `BICICLETA;14;2026-11-16T12:00;false` | Cobra 700: cualquier tipo que no sea `CARRO` ni `MOTO` paga por minuto, sin minutos gratis ni tope. `carro` en minúsculas también | ¿Qué tipos existen? ¿Una bicicleta de un día debe pagar 72 000? |
| 5 | `CARRO;120;2026-11-15T12:00;true` | 9000 × 0,8 − 3000 = 4200: el sello se descuenta después del descuento dominical | ¿En qué orden se aplican descuento y sello? |
| 6 | Entrada el sábado a las 23:00, salida el domingo a la 1:00 | Recibe el descuento de domingo y el recargo nocturno: ambos dependen solo de la hora de salida | ¿El domingo y la noche se miden a la salida? |
| 7 | `salir` de una placa que no entró | `NullPointerException` | ¿Qué debe pasar? |
| 8 | Salida a los 15 min 59 s | 0: los segundos se truncan | ¿Correcto? |

## Paso 3: costuras

| Obstáculo | Costura | Commit |
|---|---|---|
| La hora real | Sobrecargas de `entrar` y `salir` que reciben la hora; las originales delegan con `now()` | `costura: la hora ...` |
| El cálculo mezclado con estado e impresión | Extraer `cobro(tipo, minutos, salida, sello)`, que solo devuelve el valor | `costura: el cálculo ...` |
| El estado estático | No se rompió: cada prueba de `salir` usa su propia placa y mide la caja por diferencia | — |

## Paso 7: el refactor

Cuatro commits `refactor:` en el historial original, con la muestra en verde tras cada uno:
nombres, una sola regla por horas para carro y moto, un nombre para cada ajuste y los límites con nombre.

## Paso 8: mis reglas del paso 2 frente a las que encontré

| Regla del paso 2 | Lo que hace de verdad |
|---|---|
| 2. Tope de 30 000 "a partir de las 8 horas" | **Mal**: a partir de más de 8 horas. Con 7 y 8 horas se cobra más que el tope (hallazgo 1) |
| 4. Otros vehículos, 50 por minuto | Incompleta: sin minutos gratis y sin tope; y cualquier texto que no sea exactamente `CARRO` o `MOTO` cae aquí |
| 6. El sello descuenta 3000 | Incompleta: solo si había algo que cobrar, y después del descuento dominical |
| 7. De noche se suman 2000 | Incompleta: también cuando el tiempo era gratis, y se mide solo a la salida |
| — | **Me faltaba**: los segundos se truncan; el domingo se decide por la hora de salida; una placa que no entró lanza `NullPointerException` |

Una de siete estaba mal y tres incompletas, y faltaban tres reglas.

## Paso 9: los eléctricos pagan la mitad

Decisión, a confirmar con el negocio: la mitad se aplica al valor por tiempo; el descuento dominical, el sello
y el recargo nocturno se aplican después, igual que a todos. `ElectricosTest` fija las dos cosas. Se hizo en dos ciclos:
primero la tarifa (`cobro`), después el registro a la entrada (`entrar(placa, tipo, electrico, hora)`).

## Paso 10: el hallazgo 1 era un error

En el historial original, el commit `rojo:` cambia la muestra a propósito y explica en su mensaje qué casos cambian (300 combinaciones:
carros y motos que salen con más de 6 y hasta 8 horas). El `verde:` siguiente corrige `porHoras` con `Math.min`.

## Paso 11: tres casos con nombre

`ReglasTest` sustituye a tres líneas de la muestra: la gratuidad de 15 minutos, el descuento dominical y el recargo nocturno.
La muestra queda con 1887 combinaciones.

## Criterios de aceptación

| Criterio | Evidencia |
|---|---|
| Caracterización antes de cualquier refactor | En el historial original, el commit `caracterización:` va antes del primer `refactor:` |
| Ningún commit mezcla refactor y cambio de comportamiento | Los `refactor:` dejan la muestra intacta y en verde; los cambios de comportamiento son `rojo:`/`verde:` |
| La muestra cubre los límites | Paso 4 |
| Las pruebas no dependen de la hora real | Ninguna prueba llama a `now()` ni a los métodos sin hora |
| Los comportamientos sospechosos están documentados | Paso 6 |
