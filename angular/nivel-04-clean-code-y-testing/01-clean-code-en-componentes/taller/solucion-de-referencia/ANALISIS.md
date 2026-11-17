# Taller n4-01 — Clean code en componentes

Punto de partida: el componente "Antes" del README (`ResumenComponent` con `d: any`, `l`, `e`, `f`, un getter `t`) más las reglas de la tabla (cálculo en plantilla, tres banderas, condición sin nombre). La solución es `limpio.ts`.

| # | Qué se hizo | Dónde se comprueba |
|---|---|---|
| 1 | Cero cálculos en la plantilla: `{{ totalConIva() }}`, `{{ cantidadActiva() }}`, `@if (puedeEditar())`. El cálculo vive en funciones puras (`totalConIva`, `itemsActivos`, `puedeEditarPedido`). | `limpio.spec.ts` (reglas puras y componente) |
| 2 | `l`, `e` y `d` se sustituyen por `Carga<Pedido>` y un `@switch` sobre `carga().estado`. | spec: cargando → listo y cargando → error |
| 3 | Estado mínimo: solo `carga` (más las entradas). Eran derivados: `cantidad`, `total`, `hayItems` (y `t` del getter). | `computed` en `PedidoLimpioComponent` |
| 4 | Renombres por intención (diez): `d`→`pedido`/`datos`, `l`/`e`→`estado: 'cargando'/'error'`, `u`→`usuario`, `onClick2`→`editarPedido`, `filtrarItem`→`itemsActivos`, `sumar`→`totalConIva`, `cantidad`→`cantidadActiva`, `hayItems`→`hayItemsActivos`, la condición larga→`puedeEditar`/`puedeEditarPedido`, `f`→`filtro`. | revisión + spec |
| 5 | `strict` y `strictTemplates` activados en `tsconfig.json` de esta aplicación (todo el código del curso compila con ellos). Al activarlos no apareció ningún error; los tipos `any` del "antes" desaparecen con `Item`, `Pedido`, `Usuario`. | `ngc -p tsconfig.app.json` y `ng build` |

Además: miembros de la plantilla `protected`, entradas `readonly` (`input`/`output`), la API pública del componente es solo `pedidoId`, `usuario` y `edicionSolicitada`.
