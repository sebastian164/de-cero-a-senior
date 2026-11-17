# Taller nivel-02/01: SRP. Actores y decisiones

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-02-solid/01-srp/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Componente de partida: `antes/editor-factura.tsx`, **206 líneas**, 13 estados (`useState`), cuatro efectos encadenados y un `any`. Versión separada: `despues/` (dominio 41 líneas, datos 33, exportar 8, hook 46, presentación 94).
Pruebas: `taller.test.tsx` (las reglas sin React y el mismo recorrido contra la versión de partida y la separada).

## Punto 1. Los actores del componente

Un "actor" es quien puede pedir un cambio al componente. El editor de partida respondía ante cinco:

| Actor | Qué le pertenece dentro del componente de partida | Dónde quedó |
|---|---|---|
| Área comercial | descuentos por tipo de cliente (normal, frecuente, mayorista) | `despues/dominio.ts` (`descuento`) |
| Área tributaria | IVA del 19 % sobre la base con descuento; exentos | `despues/dominio.ts` (`totales`) |
| Equipo del servicio (API) | URL, formato DTO (`cliente_nombre`, `valor_centavos`, `fecha_emision`) y traducción | `despues/datos.ts` |
| Contabilidad | formato del CSV exportado (columnas, orden, separador) | `despues/exportar.ts` |
| Diseño / producto | moneda, fechas, mensajes de validación, estados de carga y botones | `despues/editor-factura.tsx` y `use-editor-factura.ts` |

## Punto 3. Una regla probada sin importar React

`dominio.ts` y `exportar.ts` no importan React: la prueba "reglas sin importar React" cubre descuentos comerciales, IVA sobre la base con descuento (y exentos), validación de líneas, el adaptador DTO a modelo y el CSV.
Ejemplo medido: una factura de $1.200.000 de un mayorista da descuento $60.000, IVA $216.600 y total $1.356.600.

## Hallazgo del original: totales intermedios en $0

El original calcula subtotal, descuento, IVA y total en cuatro efectos encadenados. Entre un efecto y el siguiente hay renderizados con totales viejos o en $0 hasta que la cadena termina (la prueba usa `waitFor` para esperar el valor final).
En la versión separada los totales son valores derivados (`totales(factura)`) y no existe ese estado intermedio.

## Punto 4. Qué dejé junto y por qué

- **`descuento` y el IVA en el mismo archivo (`dominio.ts`).** Son dos actores (comercial y tributario), pero el IVA se calcula sobre la base *después* del descuento y las dos reglas son funciones puras de pocas líneas que se prueban juntas. Separarlas hoy
  añadiría un archivo y una dependencia entre ellos sin que nadie las cambie por razones distintas todavía. Si el área tributaria empieza a tener sus propias reglas (retenciones, otros impuestos), se parte en `descuentos.ts` e `impuestos.ts`.
- **Formato de moneda y fecha dentro de `editor-factura.tsx`.** Cambian por el mismo actor (diseño) y por la misma razón que el resto de la presentación.
- **La coordinación de la pantalla (estado de la descarga y del formulario de línea) junto a la presentación.** Es pegamento de la interfaz sin reglas de negocio.
