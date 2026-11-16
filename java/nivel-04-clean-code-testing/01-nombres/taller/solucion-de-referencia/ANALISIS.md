# Taller 01 — Nombres significativos: solución

El taller pide una clase de más de 150 líneas "de un proyecto tuyo". Como no hay proyecto del alumno,
se escribió un módulo heredado verosímil y anonimizado: `legado/Main.java`, una facturación de ferretería
cuya clase `FacturaUtil` tiene 158 líneas. Ese mismo módulo lo reutilizan los talleres 03 y 05.

- `legado/Main.java`: el original. Su salida está en `legado/salida-esperada.txt`.
- `Main.java`: el mismo código con los nombres nuevos.
- Comprobación de que **no cambió el comportamiento**: ejecuta los dos y compara la salida
  byte a byte: `java Main.java | diff - legado/salida-esperada.txt`.
  Resultado: idéntica.

En este taller solo se cambiaron nombres. Las banderas, el `switch` y la duplicación siguen ahí a
propósito: se tratan en los talleres 03 y 05. Mezclar renombrado y refactor en el mismo cambio
lo haría difícil de revisar.

## 1. Diez nombres mejorables

| # | Antes | Regla que incumple | Después | Qué tuve que entender para proponerlo |
|---|---|---|---|---|
| 1 | `FacturaUtil` | Sufijo genérico (`Util`) que no dice el rol | `Facturacion` | Que la clase guarda estado (las facturas emitidas y el consecutivo): no es una utilidad estática, es el servicio de facturación |
| 2 | `proc(...)` | Abreviatura y verbo vacío | `facturar(...)` | Que el método valida, calcula **y registra** la factura (y avanza el consecutivo): es la emisión, no un "proceso" |
| 3 | `l` | Una letra, se confunde con `1` | `registrosDeFacturas` | Que cada elemento es un texto `numero;cliente;base;pais`, no una factura completa |
| 4 | `n` + comentario "contador" | El comentario compensa el nombre | `ultimoNumeroDeFactura` | Que es el consecutivo fiscal y que el número se construye como `"F-" + n` |
| 5 | `tmp` | No dice qué guarda; además es un campo, no algo temporal | `baseGravable` | Que es el subtotal menos el descuento, sobre el que se calcula el IVA y lo que se guarda en el registro |
| 6 | `s`, `c` | Una letra | `subtotal`, `unidades` | Que `c` cuenta unidades y no líneas: el descuento por volumen depende de unidades (12 bultos de cemento) |
| 7 | `d`, `i`, `e` | Una letra | `descuento`, `iva`, `costoDeEnvio` | Qué componente de la factura es cada uno, mirando el texto que se imprime |
| 8 | `t` (int) + comentario "1=normal 2=mayorista 3=empleado" | Código mágico explicado en un comentario | `tipoDeCliente` y constantes `CLIENTE_DETAL`, `CLIENTE_MAYORISTA`, `CLIENTE_EMPLEADO` | Que "normal" en el negocio se dice "cliente de detal" (ver glosario) |
| 9 | `env`, `reg` | Abreviaturas de banderas | `conEnvio`, `esRegalo` | Que `reg` no es "registro" sino "regalo": solo agrega `[regalo]` al texto. El nombre corto engañaba |
| 10 | `rep()`, `totCli()`, `pts()` | Abreviaturas | `reporteDeVentasConIvaPorPais()`, `baseGravableFacturadaA(cliente)`, `puntosDeFidelidad(...)` | Que `totCli` suma la base **sin IVA**, no el total pagado; y que el reporte suma la base más el IVA **sin el envío** |

Otros renombrados menores: `ClienteDatos` → `Cliente`, `Dir` → `Direccion`, `cp` → `codigoPostal`,
`getAnios` → `getAniosComoCliente`, `IExportador` → `Exportador` (sin prefijo de tipo), `x`/`f` → `linea`/`campos`, `q`/`v` → `cantidad`/`precioUnitario`.

## 2. Un concepto con varios nombres y un nombre con varios significados

- **Un concepto, cuatro nombres:** el cliente aparece como `nom` (en `proc`), `comprador` (en `totCli`),
  `cd` (de tipo `ClienteDatos`) y `cli` en los comentarios. Ahora es siempre `cliente` / `Cliente`.
- **Un nombre, dos significados:** `total`. En `proc` es lo que paga el cliente (base + IVA + envío). En
  `rep` es la base gravable leída del registro (sin IVA ni envío). Al separarlos (`totalAPagar` y
  `baseGravableDeLaFactura`) salió a la luz una pregunta para negocio: el "reporte con IVA" de Colombia da
  299.404, pero lo que pagaron los clientes de Colombia suma 307.404, porque el reporte no incluye el envío
  de 8.000 de la factura F-3. Puede ser correcto (el envío no es venta) o un error; el nombre viejo lo ocultaba.
  No se cambió: el taller no cambia comportamiento.

## 3. Números mágicos con nombre

Se nombraron todos: `19`/`16` → `PORCENTAJE_IVA_COLOMBIA`/`PORCENTAJE_IVA_MEXICO`, `150000` →
`COMPRA_MINIMA_PARA_ENVIO_GRATIS`, `8000` → `COSTO_DE_ENVIO`, `10000` → `PESOS_POR_PUNTO_DE_FIDELIDAD`,
`60` → `LONGITUD_MAXIMA_DEL_NOMBRE`, `10`, `5`, `20`, `3` → los porcentajes de descuento y comisión.
El caso más útil fue `150000`: el nombre deja claro que se compara con la base gravable (después del
descuento) y no con el subtotal, que es lo que uno supondría.

El `19` de `calc2` se dejó: es código muerto y se elimina en el taller 03, no se renombra.

## 4. Glosario del negocio

| Término del negocio | ¿El código lo usaba igual? | Ahora |
|---|---|---|
| Cliente | No: `nom`, `comprador`, `cd`, `ClienteDatos` | `cliente`, `Cliente` |
| Cliente de detal | No: "normal" y el número 1 | `CLIENTE_DETAL` |
| Mayorista | Solo en un comentario; el código decía `2` | `CLIENTE_MAYORISTA` |
| Factura | Sí en el texto, pero el método era `proc` | `facturar`, `numeroDeFactura` |
| Consecutivo de factura | No: `n` | `ultimoNumeroDeFactura` |
| Base gravable | No: `tmp`, y "total" en el reporte | `baseGravable` |
| IVA | Sí (`i`, "iva") | `iva` |
| Anulación | Sí (`anular`) | sin cambio |
| Comisión del vendedor | Parcial: `calcular(..., esCom, ...)` | `comisionDelVendedor` (la bandera queda para el taller 05) |
| Puntos de fidelidad | No: `pts` | `puntosDeFidelidad` |

## 5. Tres comentarios reemplazados por nombres

1. `// contador` sobre `n` → el campo se llama `ultimoNumeroDeFactura`.
2. `// t: tipo de cliente 1=normal 2=mayorista 3=empleado` → parámetro `tipoDeCliente` y constantes con nombre.
3. `// solo líneas con cantidad y precio` → variable `lineaFacturable`.

También desaparecieron `// procesa la factura`, `// lista de facturas`, `// descuento`, `// iva`, `// envio`,
`// reporte por pais...`, `// anula una factura` y `// total facturado a un cliente, sin iva`: repetían el nombre nuevo.
