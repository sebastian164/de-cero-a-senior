# Taller 02 — Métodos y clases: solución

Código: `Main.java` (se ejecuta con `java Main.java`; termina con código 1 si algo difiere).

## 1. Lo que hace `liquidar`

1. Calcula el salario de los días trabajados (`salario / 30 * dias`).
2. Calcula el valor de la hora ordinaria (`salario / 240`).
3. Decide el recargo de las horas extra según la bandera `nocturnas` (175 % o 125 %).
4. Calcula el valor de las horas extra.
5. Suma el total devengado.
6. Decide el porcentaje de descuento según el país (texto `"CO"`, `"MX"` u otro).
7. Calcula el descuento.
8. Calcula el neto.
9. Formatea el texto de salida.
10. Decide, según la bandera `incluirDetalle`, si agrega el desglose al texto.

Mezcla tres niveles de abstracción: reglas de nómina (1-8), reglas por país (6-7) y presentación (9-10).

## 2. Refactor

| Antes | Después |
|---|---|
| 6 parámetros, 2 banderas | `Liquidador(Pais)` + `liquidar(salario, Jornada)`: ningún método con más de 2 parámetros |
| `boolean nocturnas` | `enum TipoHoraExtra { DIURNA, NOCTURNA }` con su recargo |
| `String pais` + `if/else` | `enum Pais` con su porcentaje; `Pais.deCodigo` traduce el texto en el borde |
| `boolean incluirDetalle` | `Liquidacion.resumen()` y `Liquidacion.conDetalle()`: quien llama elige el método, no una bandera |
| Números mágicos 30 y 240 | `DIAS_DEL_MES`, `HORAS_DEL_MES` |

Cada método queda en un solo nivel: `liquidar` orquesta (base, extra, descuento), los privados calculan una regla cada uno, y la presentación vive en `Liquidacion`. No se fragmentó más: el cálculo de `valorHora` sigue en línea dentro de `valorHorasExtra` porque extraerlo daría un método de una línea que solo renombra una división.

## 3. Consultas y comandos

Todos los métodos de la versión nueva son **consultas**: reciben datos y devuelven un valor sin modificar estado (`liquidar`, `salarioDeLosDias`, `valorHorasExtra`, `neto`, `resumen`, `conDetalle`, `descuentoSobre`, `valorDe`, `deCodigo`). No hay comandos: el original tampoco tenía efectos, solo construía un texto. El único efecto del programa (`System.out.println`) está en `main`, el borde.

## 4. El resultado numérico no cambió

`Main` ejecuta la versión original (copiada sin cambios en la clase `Original`) y la nueva sobre 1.800 combinaciones: 6 salarios (incluidos 0, 1 y 239, que prueban la división entera), 5 valores de días, 5 de horas extra, diurnas y nocturnas, los países CO, MX y uno sin descuento, con y sin detalle. Compara los textos completos. Salida verificada:

```
Neto: 960250
Neto: 960250 (base 1000000, extra 43750, descuento 83500)
1800 casos comparados con la versión original, 0 diferencias
```

La primera línea es la misma que imprime `taller/inicio/Main.java` (`Neto: 960250`).

Un detalle que la comparación protege: el orden de las operaciones enteras. `salario / 30 * dias` no es igual a `salario * dias / 30`: con 1.300.000 y 15 días da 649.995 contra 650.000. Se conservó el orden original, aunque el segundo sea más exacto; cambiarlo sería un cambio de comportamiento y va en un commit aparte, con su propia prueba.
