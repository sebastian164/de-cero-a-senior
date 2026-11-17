# Taller TypeScript 01: lo que dijo el compilador

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/typescript/01-typescript-esencial/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Versión: TypeScript 5.9.3, `strict: true`. La salida íntegra de `tsc -p <caso>` queda en `mediciones/errores-del-compilador/*.txt`
y `scripts/fallos-de-compilacion.sh` la regenera, exige código de salida distinto de 0 y comprueba que cada código de error de este documento sigue apareciendo.
Los comportamientos en ejecución están en `taller.test.ts`, bloque "lo que el JavaScript original hacía en ejecución".

## Punto 2. Agregar un medio de pago y anotar qué deja de compilar

Caso `fallo-medio-nuevo/`: se agrega `{ tipo: 'cripto'; red: 'bitcoin' | 'ethereum' }` a la unión y NO se atiende en el `switch`.

| Qué deja de compilar | Error |
|---|---|
| La rama `default` de `comision`: `const nunca: never = medio` | `TS2322: Type '{ readonly tipo: "cripto"; … }' is not assignable to type 'never'` |

Es un solo error, y apunta exactamente al lugar que falta. Sin la comprobación exhaustiva `never`, `comision` devolvería `undefined` para `cripto` y nadie se enteraría.

## Punto 5. JavaScript de 50 líneas a TypeScript estricto: errores reales

Dos pasadas sobre `js-original/inventario.js` (el original, sin tocar la lógica):

**A. El propio JavaScript con `checkJs` + `strict`** (`tsc -p js-original`, 11 errores):

| Código | Dónde | Qué descubrió |
|---|---|---|
| TS7006 (×5) | `buscar`, `precioConIva`, `descontar` (2), `etiqueta` | parámetros `sku` y `cantidad` sin tipo: eran `any` implícitos |
| TS18048 (×3) | `precioConIva`, `etiqueta` (2) | `buscar` puede devolver `undefined` y se leía `.precio` y `.nombre` sin comprobar |
| TS2551 (×1) | `valorInventario` | `p.existencia` no existe (es `existencias`) |
| TS7053 (×2) | `resumenPorCategoria` | el objeto `{}` se indexaba con un texto cualquiera |

**B. La conversión directa, solo con tipos agregados** (`fallo-conversion-directa/`, 9 errores):

| Código | Dónde | Qué descubrió |
|---|---|---|
| TS18048 (×3) | `precioConIva`, `etiqueta` | lo mismo que arriba, ya con el tipo de retorno `Producto \| undefined` |
| TS2551 (×1) | `valorInventario` | el campo mal escrito |
| TS2540 (×2) | `descontar`, `agotados` | `existencias` es de solo lectura: `descontar` mutaba el inventario, y `filter((p) => p.existencias = 0)` **asignaba** en vez de comparar |
| TS7053 (×2) | `resumenPorCategoria` | `const resumen = {}` indexado por la categoría |
| TS2554 (×1) | `descontar2 = descontar('CAF-01')` | falta el argumento `cantidad` |

Tres defectos NO los ve el JavaScript con `checkJs`: la asignación en `agotados` (en JS `existencias` es mutable), el argumento faltante de `descontar('CAF-01')`
(en archivos JavaScript todos los parámetros son opcionales) y el tipo de `categoria` (`string` frente a la unión). Solo aparecen al añadir los tipos.

### Qué hacía cada defecto en ejecución (observado ejecutando el original)

| Defecto | Resultado real |
|---|---|
| `precioConIva('NO-EXISTE')` | `TypeError: Cannot read properties of undefined (reading 'precio')` |
| `valorInventario()` | `NaN`, sin error |
| `agotados()` | devuelve `[]` y deja las existencias de TODOS los productos en 0: `resumenPorCategoria()` pasa a `{ bebidas: 0, panaderia: 0 }` |
| `descontar('CAF-01')` al cargar el módulo | se ejecuta con `cantidad` indefinida y devuelve `true` |

### Resultado: `inventario.ts` sin `any`

Cada error tiene su corrección en `inventario.ts`: `Categoria` como unión, `existencias` de solo lectura, `buscar` devuelve `Producto | undefined` y `precioConIva` devuelve `null`
si no existe, `valorInventario` usa el campo correcto, `agotados` compara con `===`, `descontar` devuelve un resultado nuevo sin mutar el inventario y `resumenPorCategoria` es un `Record<Categoria, number>`.
Las pruebas "corrige los errores que el JavaScript escondía" y "descontar no modifica el inventario original" lo comprueban.
